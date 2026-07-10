package com.puduvandi.owner.repository;

import com.puduvandi.owner.entity.OwnerDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OwnerDocumentRepository extends JpaRepository<OwnerDocument, Long> {
    List<OwnerDocument> findByOwnerIdAndDeletedFalse(Long ownerId);
}
