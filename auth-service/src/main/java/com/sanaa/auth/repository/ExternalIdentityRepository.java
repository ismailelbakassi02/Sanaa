package com.sanaa.auth.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sanaa.auth.domain.ExternalIdentity;
import com.sanaa.auth.domain.IdentityProvider;

/** External identities are global and are never tenant-filtered. */
public interface ExternalIdentityRepository extends JpaRepository<ExternalIdentity, Long> {

    Optional<ExternalIdentity> findByProviderAndProviderSubject(
            IdentityProvider provider, String providerSubject);

    Optional<ExternalIdentity> findByUserIdAndProvider(long userId, IdentityProvider provider);
}
