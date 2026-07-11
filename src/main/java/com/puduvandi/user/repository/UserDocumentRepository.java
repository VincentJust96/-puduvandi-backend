package com.puduvandi.user.repository;

import com.puduvandi.common.enums.DocumentStatus;
import com.puduvandi.common.enums.DocumentType;
import com.puduvandi.user.entity.UserDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserDocumentRepository extends JpaRepository<UserDocument, Long> {

    List<UserDocument> findByUserIdAndDeletedFalse(Long userId);

    Optional<UserDocument> findByUserIdAndDocumentTypeAndDeletedFalse(
            Long userId, DocumentType documentType);

    boolean existsByUserIdAndDocumentTypeAndStatusAndDeletedFalse(
            Long userId, DocumentType documentType, DocumentStatus status);

    @Query("""
        SELECT d FROM UserDocument d
        WHERE d.deleted = false
          AND (:documentType IS NULL OR d.documentType = :documentType)
          AND (:status IS NULL OR d.status = :status)
        """)
    Page<UserDocument> findAllForAdmin(DocumentType documentType, DocumentStatus status, Pageable pageable);
}
