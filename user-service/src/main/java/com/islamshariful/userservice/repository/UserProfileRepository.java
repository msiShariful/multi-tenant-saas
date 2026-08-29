package com.islamshariful.userservice.repository;

import com.islamshariful.userservice.domain.UserProfile;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Every method here is implicitly scoped to the current tenant.
 *
 * <p>{@code findById(userId)} looks unsafe for a multi-tenant system and is not: {@link UserProfile}
 * carries {@code @TenantId}, so Hibernate issues {@code ... WHERE id = ? AND tenant_id = ?}. A profile id
 * from another tenant simply does not resolve, which is why cross-tenant reads are 404 rather than 403.
 */
public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {

    Page<UserProfile> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * Directory search over the fields a member would recognise someone by.
     *
     * <p>{@code LIKE '%term%'} cannot use a B-tree index, so this degrades linearly with tenant size. Fine
     * at the scale a tenant directory reaches; a trigram index ({@code pg_trgm}) is the fix if it stops
     * being, and full-text search the one after that.
     */
    @Query("""
            select p from UserProfile p
            where lower(p.email) like lower(concat('%', :term, '%'))
               or lower(coalesce(p.displayName, '')) like lower(concat('%', :term, '%'))
            order by p.createdAt desc
            """)
    Page<UserProfile> search(@Param("term") String term, Pageable pageable);
}
