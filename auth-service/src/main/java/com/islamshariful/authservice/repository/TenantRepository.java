package com.islamshariful.authservice.repository;

import com.islamshariful.authservice.domain.Tenant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Tenants are global rows; nothing here is tenant-filtered, by design. */
public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    Optional<Tenant> findBySlug(String slug);

    boolean existsBySlug(String slug);
}
