package com.puduvandi.user.entity;

import com.puduvandi.auth.entity.User;
import com.puduvandi.common.entity.BaseEntity;
import com.puduvandi.common.enums.DocumentStatus;
import jakarta.persistence.*;
import lombok.*;

/**
 * A user's request to change their login phone number to a new one they've
 * already OTP-verified — sits PENDING until an admin approves or rejects it,
 * at which point (if approved) it becomes the user's actual phone_number.
 */
@Entity
@Table(name = "phone_change_requests")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PhoneChangeRequest extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "old_phone_number", length = 15)
    private String oldPhoneNumber;

    @Column(name = "new_phone_number", nullable = false, length = 15)
    private String newPhoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private DocumentStatus status;

    @Column(name = "remarks", length = 255)
    private String remarks;
}
