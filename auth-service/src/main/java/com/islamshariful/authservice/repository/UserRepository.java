package com.islamshariful.authservice.repository;

import com.islamshariful.authservice.domain.RoleName;
import com.islamshariful.authservice.domain.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Every method here is implicitly scoped to the current tenant.
 *
 * <p>{@code findByEmail} looks unsafe for a multi-tenant system and is not: {@link User} carries
 * {@code @TenantId}, so Hibernate issues {@code ... WHERE email = ? AND tenant_id = ?}. The same holds for
 * {@code findById} and {@code findAll} — an id belonging to another tenant simply does not resolve. That is
 * the payoff of enforcing tenancy in the mapping instead of in every query.
 */
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Page<User> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * Counts active holders of a role within the current tenant -- the join to {@code roles} is unfiltered
     * (the catalogue is global) while the {@code User} side is tenant-filtered, which is exactly right.
     * Used to stop a tenant from removing its last administrator and locking itself out.
     */
    @Query("select count(u) from User u join u.roles r where r.name = :role and u.status = com.islamshariful.authservice.domain.UserStatus.ACTIVE")
    long countActiveWithRole(@Param("role") RoleName role);
}
