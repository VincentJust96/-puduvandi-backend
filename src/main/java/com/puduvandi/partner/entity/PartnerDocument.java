package com.puduvandi.partner.entity;

import com.puduvandi.common.entity.BaseEntity;
import com.puduvandi.common.enums.DocumentStatus;
import com.puduvandi.common.enums.DocumentType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "partner_documents")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PartnerDocument extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partner_id", nullable = false)
    private PartnerProfile partner;

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
