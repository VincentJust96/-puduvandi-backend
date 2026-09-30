package com.puduvandi.auth.repository;

import com.puduvandi.auth.entity.User;
import com.puduvandi.common.enums.UserRole;
import com.puduvandi.common.enums.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByPhoneNumberAndDeletedFalse(String phoneNumber);

    Optional<User> findByPhoneNumber(String phoneNumber);

    boolean existsByPhoneNumber(String phoneNumber);

    boolean existsByPhoneNumberAndDeletedFalse(String phoneNumber);

    Optional<User> findByEmailIgnoreCaseAndDeletedFalse(String email);

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    @Query("""
        SELECT u FROM User u
        WHERE u.deleted = false
          AND u.role IS NOT NULL
          AND (:role IS NULL OR u.role = :role)
          AND (:status IS NULL OR u.status = :status)
        """)
    Page<User> findAllForAdmin(UserRole role, UserStatus status, Pageable pageable);

    // Users who verified OTP (or signed up via email) but never completed
    // the second signup step (POST /auth/set-role) — see AuthService.verifyOtp.
    // Kept out of findAllForAdmin so incomplete signups aren't listed/managed
    // as if they were real accounts; surfaced separately for cleanup instead.
    @Query("""
        SELECT u FROM User u
        WHERE u.deleted = false
          AND u.role IS NULL
        """)
    Page<User> findPendingSignups(Pageable pageable);

    long countByDeletedFalse();
    long countByRoleAndDeletedFalse(UserRole role);
    long countByRoleIsNullAndDeletedFalse();

    Page<User> findByRoleInAndDeletedFalseOrderByCreatedAtDesc(List<UserRole> roles, Pageable pageable);
}
