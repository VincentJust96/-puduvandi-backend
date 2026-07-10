package com.puduvandi.storage.service;

import com.puduvandi.storage.entity.StoredFile;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/**
 * Abstraction over blob storage so callers are unaffected when swapping
 * local disk for S3 / GCS / Azure Blob in a later phase.
 */
public interface FileStorageService {

    /**
     * Persists the uploaded file and returns the saved metadata entity.
     *
     * @param file             the uploaded multipart file
     * @param uploadedByUserId the user performing the upload
     * @param category         logical category (e.g. BIKE_IMAGE, OWNER_DOCUMENT)
     */
    StoredFile store(MultipartFile file, Long uploadedByUserId, String category);

    /**
     * Loads the raw bytes of a stored file as a Spring {@link Resource}.
     */
    Resource loadAsResource(Long fileId);
}
