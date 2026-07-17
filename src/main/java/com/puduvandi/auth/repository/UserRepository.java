package com.puduvandi.auth.repository;

import com.puduvandi.auth.entity.User;
import com.puduvandi.common.enums.UserRole;
import com.puduvandi.common.enums.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByPhoneNumberAndDeletedFalse(String phoneNumber);

    Optional<User> findByPhoneNumber(String phoneNumber);

    boolean existsByPhoneNumber(String phoneNumber);

    @Query("""
        SELECT u FROM User u
        WHERE u.deleted = false
          AND (:role IS NULL OR u.role = :role)
          AND (:status IS NULL OR u.status = :status)
        """)
    Page<User> findAllForAdmin(UserRole role, UserStatus status, Pageable pageable);

    long countByDeletedFalse();
    long countByRoleAndDeletedFalse(UserRole role);
}
