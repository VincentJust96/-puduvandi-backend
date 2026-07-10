package com.puduvandi.storage.controller;

import com.puduvandi.common.dto.ApiResponse;
import com.puduvandi.exception.ResourceNotFoundException;
import com.puduvandi.security.PuduvandiUserPrincipal;
import com.puduvandi.storage.dto.FileUploadResponse;
import com.puduvandi.storage.entity.StoredFile;
import com.puduvandi.storage.repository.StoredFileRepository;
import com.puduvandi.storage.service.FileStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
@Tag(name = "Files", description = "File upload and retrieval (bike photos, KYC documents, etc.)")
public class FileController {

    private final FileStorageService fileStorageService;
    private final StoredFileRepository storedFileRepository;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a file", description = "Returns a fileId and fileUrl to use in subsequent requests")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponse<FileUploadResponse>> uploadFile(
            @RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "GENERAL") String category,
            @AuthenticationPrincipal PuduvandiUserPrincipal principal) {

        StoredFile stored = fileStorageService.store(file, principal.getUserId(), category);
        FileUploadResponse response = new FileUploadResponse(
                stored.getId(),
                stored.getFileUrl(),
                stored.getContentType(),
                stored.getOriginalFilename(),
                stored.getFileSize(),
                stored.getCreatedAt()
        );
        return ResponseEntity.ok(ApiResponse.success("File uploaded successfully", response));
    }

    @GetMapping("/{fileId}")
    @Operation(summary = "Retrieve a file by ID")
    public ResponseEntity<Resource> getFile(@PathVariable Long fileId) {
        StoredFile storedFile = storedFileRepository.findById(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("File", fileId));

        Resource resource = fileStorageService.loadAsResource(fileId);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(storedFile.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + storedFile.getOriginalFilename() + "\"")
                .body(resource);
    }
}
