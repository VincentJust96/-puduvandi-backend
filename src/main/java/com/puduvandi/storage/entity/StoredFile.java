package com.puduvandi.storage.entity;

import com.puduvandi.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "stored_files")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoredFile extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    /** Relative filename on disk (UUID-based, e.g. "a1b2c3.jpg") */
    @Column(name = "storage_path", nullable = false, length = 500)
    private String storagePath;

    /** Client-facing URL, set after initial save so we have the DB id */
    @Column(name = "file_url", length = 500)
    private String fileUrl;

    /** Logical category: BIKE_IMAGE, OWNER_DOCUMENT, USER_DOCUMENT, PROFILE_IMAGE */
    @Column(name = "category", length = 50)
    private String category;

    @Column(name = "uploaded_by_user_id")
    private Long uploadedByUserId;

    @Column(name = "file_size")
    private Long fileSize;
}
