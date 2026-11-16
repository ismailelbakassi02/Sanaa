package com.sanaa.auth.bootstrap;

import java.util.List;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sanaa.auth.crosstenant.MembershipViewRepository;
import com.sanaa.auth.domain.Membership;
import com.sanaa.auth.domain.MembershipRole;
import com.sanaa.auth.domain.PlatformRole;
import com.sanaa.auth.domain.Profile;
import com.sanaa.auth.domain.Tenant;
import com.sanaa.auth.domain.TenantStatus;
import com.sanaa.auth.domain.TenantType;
import com.sanaa.auth.domain.User;
import com.sanaa.auth.outbox.OutboxWriter;
import com.sanaa.auth.repository.MembershipRepository;
import com.sanaa.auth.repository.ProfileRepository;
import com.sanaa.auth.repository.RefreshTokenRepository;
import com.sanaa.auth.repository.TenantRepository;
import com.sanaa.auth.repository.UserRepository;
import com.sanaa.auth.service.EmailNormalizer;

/**
 * Idempotent demo data.
 *
 * <p>Passwords are never stored in the repository: every created account uses DEMO_PASSWORD. The
 * writer methods that touch memberships must run inside {@code TenantContext.callAs(tenantId)}; the
 * caller (the demo runner) is responsible for that ordering, exactly like publisher creation.
 */
@Service
public class DemoSeedService {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final MembershipRepository membershipRepository;
    private final MembershipViewRepository membershipViewRepository;
    private final ProfileRepository profileRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final OutboxWriter outboxWriter;

    public DemoSeedService(TenantRepository tenantRepository, UserRepository userRepository,
                           MembershipRepository membershipRepository,
                           MembershipViewRepository membershipViewRepository,
                           ProfileRepository profileRepository,
                           RefreshTokenRepository refreshTokenRepository,
                           PasswordEncoder passwordEncoder,
                           OutboxWriter outboxWriter) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.membershipRepository = membershipRepository;
        this.membershipViewRepository = membershipViewRepository;
        this.profileRepository = profileRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.outboxWriter = outboxWriter;
    }

    /**
     * Creates the tenant and, in the same transaction, the {@code PUBLISHER_UPSERTED} event that
     * course-service turns into a publisher snapshot.
     *
     * <p>WHY only on creation: a rerun finds the tenant and must not republish it. The outbox row is
     * written before the method returns, so the tenant and its event commit together or not at all.
     */
    @Transactional
    public Tenant ensureTenant(String slug, String name, TenantType type, String color,
                               String headline, String description, String contactEmail) {
        Optional<Tenant> existing = tenantRepository.findBySlug(slug);
        if (existing.isEmpty()) {
            Tenant created = tenantRepository.saveAndFlush(new Tenant(
                    java.util.UUID.randomUUID().toString(), slug, name, type, TenantStatus.ACTIVE,
                    color, contactEmail));
            outboxWriter.publisherUpserted(created);
            return created;
        }

        Tenant tenant = existing.get();
        tenant.updateProfile(name, headline, description, null, null, null, null, color,
                contactEmail);
        return tenantRepository.saveAndFlush(tenant);
    }

    /**
     * Creates the publisher accounts and, for each membership it creates, the matching event in the
     * same transaction: {@code INSTRUCTOR_UPDATED} for an instructor, nothing for a tenant admin.
     *
     * <p>WHY {@code tenantId} is a parameter: Hibernate needs it to write the membership, and the
     * caller is the one that set {@code TenantContext}. The event is emitted only when the
     * membership is created, so a rerun does not republish the instructor.
     */
    @Transactional
    public void ensurePublisherAccounts(String tenantId, List<SeedAccount> accounts,
                                        String rawPassword) {
        for (SeedAccount account : accounts) {
            String email = EmailNormalizer.normalize(account.email());
            User user = userRepository.findByEmail(email).orElseGet(() -> userRepository.saveAndFlush(
                    new User(email, passwordEncoder.encode(rawPassword), account.fullName(),
                            PlatformRole.USER)));

            // One publisher per user: only add the membership when the account has none at all.
            if (membershipViewRepository.findByUserId(user.getId()).isEmpty()) {
                membershipRepository.saveAndFlush(
                        new Membership(user.getId(), account.role()));
                if (account.role() == MembershipRole.INSTRUCTOR) {
                    outboxWriter.instructorUpdated(user.getId(), tenantId, user.getFullName());
                }
            }

            Profile profile = profileRepository.findByUserId(user.getId())
                    .orElseGet(() -> new Profile(user.getId()));
            profile.update(account.headline(), account.bio(), null, account.instagramUrl(),
                    account.linkedinUrl(), null);
            profileRepository.saveAndFlush(profile);
        }
    }

    @Transactional
    public void ensureLearners(List<SeedLearner> learners, String rawPassword) {
        for (SeedLearner learner : learners) {
            String email = EmailNormalizer.normalize(learner.email());
            User user = userRepository.findByEmail(email).orElseGet(() -> userRepository.saveAndFlush(
                    new User(email, passwordEncoder.encode(rawPassword), learner.fullName(),
                            PlatformRole.USER)));

            Profile profile = profileRepository.findByUserId(user.getId())
                    .orElseGet(() -> new Profile(user.getId()));
            profile.update(learner.headline(), learner.bio(), null, null, null, null);
            profileRepository.saveAndFlush(profile);
        }
    }

    /**
     * Removes accounts that only existed in the v1 demo. Memberships are tenant-scoped, so this
     * method is called under the tenant context of the publisher being cleaned up.
     */
    @Transactional
    public void removeAccounts(List<String> emails) {
        for (String rawEmail : emails) {
            userRepository.findByEmail(EmailNormalizer.normalize(rawEmail)).ifPresent(user -> {
                membershipRepository.deleteByUserId(user.getId());
                profileRepository.deleteByUserId(user.getId());
                refreshTokenRepository.deleteByUserId(user.getId());
                userRepository.delete(user);
            });
        }
    }

    public record SeedAccount(String email, String fullName,
                              com.sanaa.auth.domain.MembershipRole role, String headline,
                              String bio, String instagramUrl, String linkedinUrl) {
    }

    public record SeedLearner(String email, String fullName, String headline, String bio) {
    }
}
