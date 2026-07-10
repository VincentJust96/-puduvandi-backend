package com.puduvandi.owner.entity;

import com.puduvandi.common.entity.BaseEntity;
import com.puduvandi.common.enums.DocumentStatus;
import com.puduvandi.common.enums.DocumentType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "owner_documents")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OwnerDocument extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private OwnerProfile owner;

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
