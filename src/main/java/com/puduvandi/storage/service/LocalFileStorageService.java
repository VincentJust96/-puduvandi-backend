package com.puduvandi.storage.service;

import com.puduvandi.exception.BusinessException;
import com.puduvandi.exception.ResourceNotFoundException;
import com.puduvandi.storage.entity.StoredFile;
import com.puduvandi.storage.repository.StoredFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.*;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LocalFileStorageService implements FileStorageService {

    @Value("${puduvandi.storage.upload-dir:./uploads}")
    private String uploadDir;

    private final StoredFileRepository storedFileRepository;

    @Override
    @Transactional
    public StoredFile store(MultipartFile file, Long uploadedByUserId, String category) {
        if (file.isEmpty()) {
            throw new BusinessException("Cannot store an empty file.");
        }
        return store(file::getInputStream, file.getOriginalFilename(), file.getContentType(), file.getSize(),
                uploadedByUserId, category);
    }

    @Override
    @Transactional
    public StoredFile store(byte[] content, String originalFilename, String contentType,
                            Long uploadedByUserId, String category) {
        if (content == null || content.length == 0) {
            throw new BusinessException("Cannot store an empty file.");
        }
        return store(() -> new ByteArrayInputStream(content), originalFilename, contentType, content.length,
                uploadedByUserId, category);
    }

    private interface ContentSource {
        InputStream open() throws IOException;
    }

    private StoredFile store(ContentSource source, String rawFilename, String contentType, long size,
                             Long uploadedByUserId, String category) {
        String originalFilename = StringUtils.cleanPath(rawFilename != null ? rawFilename : "file");

        // Reject path traversal attempts in the original filename
        if (originalFilename.contains("..")) {
            throw new BusinessException("Invalid filename: " + originalFilename);
        }

        String extension = getExtension(originalFilename);
        String storedFilename = UUID.randomUUID() + (extension.isBlank() ? "" : "." + extension);

        Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(uploadPath);
            Path targetPath = uploadPath.resolve(storedFilename);
            try (InputStream in = source.open()) {
                Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            throw new BusinessException("Failed to store file: " + ex.getMessage());
        }

        // Save metadata — fileUrl is set after the first save so we have the id
        StoredFile storedFile = StoredFile.builder()
                .originalFilename(originalFilename)
                .contentType(contentType != null ? contentType : "application/octet-stream")
                .storagePath(storedFilename)
                .category(category)
                .uploadedByUserId(uploadedByUserId)
                .fileSize(size)
                .build();

        StoredFile saved = storedFileRepository.save(storedFile);
        saved.setFileUrl("/api/v1/files/" + saved.getId());
        saved = storedFileRepository.save(saved);

        log.info("File stored: id={}, originalName={}, category={}, uploadedBy={}",
                saved.getId(), originalFilename, category, uploadedByUserId);
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public Resource loadAsResource(Long fileId) {
        StoredFile storedFile = storedFileRepository.findById(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("File", fileId));

        Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path filePath = uploadPath.resolve(storedFile.getStoragePath()).normalize();

        if (!filePath.startsWith(uploadPath)) {
            throw new BusinessException("Access denied: invalid file path.");
        }

        try {
            Resource resource = new UrlResource(filePath.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new ResourceNotFoundException("File", fileId);
            }
            return resource;
        } catch (MalformedURLException ex) {
            throw new BusinessException("Could not resolve file path: " + ex.getMessage());
        }
    }

    private String getExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        return (dotIndex >= 0) ? filename.substring(dotIndex + 1).toLowerCase() : "";
    }
}
