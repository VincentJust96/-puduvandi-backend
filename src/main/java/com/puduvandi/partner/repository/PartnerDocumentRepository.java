package com.puduvandi.partner.repository;

import com.puduvandi.partner.entity.PartnerDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PartnerDocumentRepository extends JpaRepository<PartnerDocument, Long> {
    List<PartnerDocument> findByPartnerIdAndDeletedFalse(Long partnerId);
}
