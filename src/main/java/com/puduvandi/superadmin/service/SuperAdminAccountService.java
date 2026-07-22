package com.puduvandi.superadmin.service;

import com.puduvandi.admin.dto.AdminUserResponse;
import com.puduvandi.auth.entity.User;
import com.puduvandi.auth.repository.UserRepository;
import com.puduvandi.common.enums.KycStatus;
import com.puduvandi.common.enums.UserRole;
import com.puduvandi.common.enums.UserStatus;
import com.puduvandi.exception.ConflictException;
import com.puduvandi.exception.ResourceNotFoundException;
import com.puduvandi.superadmin.dto.CreateAdminRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Admin-account lifecycle, restricted to the super admin. Role on a
 * newly-created account is always ADMIN — there is no request field for it —
 * which is what keeps "one super admin, many admins" true: nothing here can
 * ever mint or demote a SUPER_ADMIN.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SuperAdminAccountService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public Page<AdminUserResponse> listAdmins(int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return userRepository
                .findByRoleInAndDeletedFalseOrderByCreatedAtDesc(List.of(UserRole.ADMIN, UserRole.SUPER_ADMIN), pageable)
                .map(this::toResponse);
    }

    @Transactional
    public AdminUserResponse createAdmin(CreateAdminRequest request) {
        if (userRepository.existsByPhoneNumber(request.phoneNumber())) {
            throw new ConflictException("This phone number is already linked to another account.");
        }

        User admin = User.builder()
                .phoneNumber(request.phoneNumber())
                .fullName(request.fullName())
                .email(request.email())
                .role(UserRole.ADMIN)
                .status(UserStatus.ACTIVE)
                .kycStatus(KycStatus.NOT_SUBMITTED)
                .deleted(false)
                .build();

        User saved = userRepository.save(admin);
        log.warn("Super admin created ADMIN account userId={} phone={}", saved.getId(), saved.getPhoneNumber());
        return toResponse(saved);
    }

    @Transactional
    public void deleteAdmin(Long userId) {
        User user = userRepository.findById(userId)
                .filter(u -> !u.isDeleted() && u.getRole() == UserRole.ADMIN)
                .orElseThrow(() -> new ResourceNotFoundException("Admin", userId));

        // Same reasoning as AdminService.deleteUser: plain UNIQUE constraints on
        // phone_number/email would otherwise squat on the identifier forever.
        user.setDeleted(true);
        user.setPhoneNumber(null);
        user.setEmail(null);
        userRepository.save(user);
        log.warn("Super admin removed ADMIN account userId={}", userId);
    }

    private AdminUserResponse toResponse(User user) {
        return new AdminUserResponse(
                user.getId(),
                user.getPhoneNumber(),
                user.getFullName(),
                user.getEmail(),
                user.getRole(),
                user.getStatus(),
                user.getKycStatus(),
                user.getCreatedAt()
        );
    }
}
