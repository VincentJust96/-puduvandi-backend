package com.puduvandi.user.entity;

import com.puduvandi.auth.entity.User;
import com.puduvandi.common.entity.BaseEntity;
import com.puduvandi.common.enums.DocumentStatus;
import com.puduvandi.common.enums.DocumentType;
import jakarta.persistence.*;
import lombok.*;

/**
 * Stores documents uploaded by customers.
 * Primary use case: Driving License verification.
 */
@Entity
@Table(name = "user_documents")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDocument extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false)
    private DocumentType documentType;

    @Column(name = "document_url", nullable = false, length = 500)
    private String documentUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private DocumentStatus status;

    @Column(name = "remarks", length = 255)
    private String remarks;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;
}
