package com.sanaa.payment.receipt;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.sanaa.payment.config.PaymentProperties;
import com.sanaa.payment.error.NotFoundException;

/**
 * Local-disk receipt storage.
 *
 * <p>Files are stored as {@code <tenantId>/<random UUID>} with no extension and never under the
 * original filename, so an uploaded name cannot influence the path and the tenant stays visible
 * to an operator browsing the directory. The verified content type lives in the database row.
 */
@Component
public class LocalReceiptStorage implements ReceiptStorage {

    private static final Logger log = LoggerFactory.getLogger(LocalReceiptStorage.class);

    private final Path root;

    public LocalReceiptStorage(PaymentProperties properties) {
        this.root = Path.of(properties.receiptsDir()).toAbsolutePath().normalize();
    }

    @Override
    public String store(String tenantId, byte[] content) {
        try {
            Path directory = root.resolve(tenantId).normalize();
            Files.createDirectories(directory);
            String name = UUID.randomUUID().toString();
            Files.write(directory.resolve(name), content,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            return tenantId + "/" + name;
        } catch (IOException exception) {
            log.error("Could not write a receipt under {}", root, exception);
            throw new IllegalStateException("The receipt could not be stored.");
        }
    }

    @Override
    public Path resolve(String relativePath) {
        Path target = root.resolve(relativePath).normalize();
        if (!target.startsWith(root)) {
            throw new NotFoundException("Receipt not found.");
        }
        return target;
    }

    @Override
    public void delete(String relativePath) {
        try {
            Files.deleteIfExists(resolve(relativePath));
        } catch (IOException exception) {
            // A leftover file is harmless and must not turn a rejected payment into a 500.
            log.warn("Could not delete receipt {} after a failed insert", relativePath, exception);
        }
    }
}
