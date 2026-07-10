package com.puduvandi.user.repository;

import com.puduvandi.common.enums.DocumentType;
import com.puduvandi.user.entity.UserDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserDocumentRepository extends JpaRepository<UserDocument, Long> {

    List<UserDocument> findByUserIdAndDeletedFalse(Long userId);

    Optional<UserDocument> findByUserIdAndDocumentTypeAndDeletedFalse(
            Long userId, DocumentType documentType);
}
