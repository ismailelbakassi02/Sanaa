package com.sanaa.auth.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.sanaa.auth.config.ImageProperties;
import com.sanaa.auth.domain.Image;
import com.sanaa.auth.domain.Profile;
import com.sanaa.auth.domain.Tenant;
import com.sanaa.auth.error.BadRequestException;
import com.sanaa.auth.error.ForbiddenException;
import com.sanaa.auth.error.NotFoundException;
import com.sanaa.auth.error.PayloadTooLargeException;
import com.sanaa.auth.error.UnsupportedMediaTypeException;
import com.sanaa.auth.repository.ImageRepository;
import com.sanaa.auth.repository.ProfileRepository;
import com.sanaa.auth.repository.TenantRepository;
import com.sanaa.auth.web.dto.ImageUploadResponse;
import com.sanaa.common.security.JwtClaims;
import com.sanaa.common.security.Role;

/**
 * Avatar and logo upload, plus public image serving.
 *
 * <p>Security decisions, all tested:
 * <ul>
 *   <li>the type is detected from magic bytes, never from the file name or the client content
 *       type, so a text file renamed to {@code .png} is rejected;</li>
 *   <li>the stored file name is a random UUID, so the caller cannot influence the path;</li>
 *   <li>the maximum size is 1 MB and the database carries the same check;</li>
 *   <li>a logo can only be set by a publisher admin, and replacing an image deletes the old file
 *       and row.</li>
 * </ul>
 */
@Service
public class ImageService {

    public static final long MAX_BYTES = 1_048_576L;

    private static final byte[] PNG_SIGNATURE = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] JPEG_SIGNATURE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

    private final ImageRepository imageRepository;
    private final ProfileRepository profileRepository;
    private final TenantRepository tenantRepository;
    private final Path directory;

    public ImageService(ImageRepository imageRepository, ProfileRepository profileRepository,
                        TenantRepository tenantRepository, ImageProperties properties) {
        this.imageRepository = imageRepository;
        this.profileRepository = profileRepository;
        this.tenantRepository = tenantRepository;
        this.directory = properties.directory().toAbsolutePath().normalize();
    }

    @Transactional
    public ImageUploadResponse upload(String kind, MultipartFile file, Jwt jwt) {
        if (!"avatar".equals(kind) && !"logo".equals(kind)) {
            throw new BadRequestException("Image kind must be avatar or logo.");
        }
        long userId = AuthFacade.userId(jwt);
        String role = jwt.getClaimAsString(JwtClaims.ROLE);
        String tenantId = jwt.getClaimAsString(JwtClaims.TENANT_ID);
        if ("logo".equals(kind)
                && (!Role.TENANT_ADMIN.name().equals(role) || tenantId == null)) {
            throw new ForbiddenException("Only a publisher admin can change the logo.");
        }

        byte[] bytes = readBytes(file);
        String contentType = detectContentType(bytes);

        Image image = new Image(contentType, UUID.randomUUID().toString(), bytes.length);
        try {
            writeFile(image.getPath(), bytes);
            imageRepository.saveAndFlush(image);

            if ("avatar".equals(kind)) {
                Profile profile = profileRepository.findByUserId(userId)
                        .orElseGet(() -> new Profile(userId));
                String previous = profile.getAvatarImageId();
                profile.changeAvatar(image.getId());
                profileRepository.saveAndFlush(profile);
                deleteIfUnused(previous, image.getId());
            } else {
                Tenant tenant = tenantRepository.findById(tenantId)
                        .orElseThrow(() -> new NotFoundException("Publisher not found."));
                String previous = tenant.getLogoImageId();
                tenant.changeLogo(image.getId());
                tenantRepository.saveAndFlush(tenant);
                deleteIfUnused(previous, image.getId());
            }
        } catch (RuntimeException exception) {
            deleteQuietly(image.getPath());
            throw exception;
        }

        return new ImageUploadResponse(image.getId(), kind, contentType, image.getSizeBytes());
    }

    @Transactional(readOnly = true)
    public StoredImage load(String id) {
        Image image = imageRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Image not found."));
        Path file = resolve(image.getPath());
        if (!Files.isRegularFile(file)) {
            throw new NotFoundException("Image not found.");
        }
        try {
            return new StoredImage(image.getContentType(), Files.readAllBytes(file));
        } catch (IOException exception) {
            throw new NotFoundException("Image not found.");
        }
    }

    public record StoredImage(String contentType, byte[] bytes) {
    }

    private byte[] readBytes(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("An image file is required.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new PayloadTooLargeException("The image must be at most 1 MB.");
        }
        try {
            byte[] bytes = file.getBytes();
            if (bytes.length > MAX_BYTES) {
                throw new PayloadTooLargeException("The image must be at most 1 MB.");
            }
            return bytes;
        } catch (IOException exception) {
            throw new BadRequestException("The image could not be read.");
        }
    }

    private static String detectContentType(byte[] bytes) {
        if (startsWith(bytes, PNG_SIGNATURE)) {
            return "image/png";
        }
        if (startsWith(bytes, JPEG_SIGNATURE)) {
            return "image/jpeg";
        }
        throw new UnsupportedMediaTypeException("Only PNG and JPEG images are accepted.");
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int index = 0; index < prefix.length; index++) {
            if (bytes[index] != prefix[index]) {
                return false;
            }
        }
        return true;
    }

    private void writeFile(String path, byte[] bytes) {
        try {
            Files.createDirectories(directory);
            Files.write(resolve(path), bytes);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to store the image", exception);
        }
    }

    private Path resolve(String path) {
        Path resolved = directory.resolve(path).normalize();
        if (!resolved.startsWith(directory)) {
            throw new NotFoundException("Image not found.");
        }
        return resolved;
    }

    /**
     * The old row is removed only when it is no longer referenced by its owner. This is the
     * "replacing deletes old" rule; the file is removed after the database accepted the new id.
     */
    private void deleteIfUnused(String previousId, String currentId) {
        if (previousId == null || previousId.equals(currentId)) {
            return;
        }
        imageRepository.findById(previousId).ifPresent(previous -> {
            imageRepository.delete(previous);
            deleteQuietly(previous.getPath());
        });
    }

    private void deleteQuietly(String path) {
        try {
            Files.deleteIfExists(resolve(path));
        } catch (IOException ignored) {
            // A missing file is already the desired state; the row was the source of truth.
        }
    }
}
