package com.puduvandi.storage.controller;

import com.puduvandi.common.dto.ApiResponse;
import com.puduvandi.exception.BusinessException;
import com.puduvandi.exception.ForbiddenException;
import com.puduvandi.exception.ResourceNotFoundException;
import com.puduvandi.exception.UnauthorizedException;
import com.puduvandi.security.PuduvandiUserPrincipal;
import com.puduvandi.storage.dto.FileUploadResponse;
import com.puduvandi.storage.dto.InsuranceAnalyzeRequest;
import com.puduvandi.storage.dto.InsuranceDetailsResponse;
import com.puduvandi.storage.entity.StoredFile;
import com.puduvandi.storage.repository.StoredFileRepository;
import com.puduvandi.storage.service.FileStorageService;
import com.puduvandi.storage.service.InsuranceDocumentParser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
@Tag(name = "Files", description = "File upload and retrieval (bike photos, KYC documents, etc.)")
public class FileController {

    private final FileStorageService fileStorageService;
    private final StoredFileRepository storedFileRepository;
    private final InsuranceDocumentParser insuranceDocumentParser;

    /** Categories that contain sensitive KYC/licence documents — never publicly downloadable. */
    private static final Set<String> RESTRICTED_CATEGORIES = Set.of("OWNER_DOCUMENT", "USER_DOCUMENT");

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

    @PostMapping("/{fileId}/insurance-details")
    @Operation(summary = "Best-effort read of policy number + valid-till date from an uploaded insurance PDF",
            description = "Returns null fields when they can't be confidently detected — the caller must " +
                    "let the user fill or correct them manually rather than treating this as authoritative. " +
                    "If the PDF is password-protected, returns passwordRequired=true instead; re-submit with " +
                    "{\"password\": \"...\"} once the user has entered it.")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponse<InsuranceDetailsResponse>> extractInsuranceDetails(
            @PathVariable Long fileId,
            @RequestBody(required = false) InsuranceAnalyzeRequest request,
            @AuthenticationPrincipal PuduvandiUserPrincipal principal) {

        StoredFile storedFile = storedFileRepository.findById(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("File", fileId));

        boolean isUploader = principal.getUserId() != null
                && principal.getUserId().equals(storedFile.getUploadedByUserId());
        boolean isAdmin = "ADMIN".equals(principal.getRole()) || "SUPER_ADMIN".equals(principal.getRole());
        if (!isUploader && !isAdmin) {
            throw new ForbiddenException("You do not have permission to access this file.");
        }

        if (!MediaType.APPLICATION_PDF_VALUE.equalsIgnoreCase(storedFile.getContentType())) {
            throw new BusinessException("Please upload the insurance document as a PDF so its details can be read automatically.");
        }

        String password = request != null ? request.password() : null;
        InsuranceDetailsResponse details;
        try (InputStream in = fileStorageService.loadAsResource(fileId).getInputStream()) {
            details = insuranceDocumentParser.extract(in.readAllBytes(), password);
        } catch (IOException ex) {
            throw new BusinessException("Could not read the uploaded PDF.");
        }

        return ResponseEntity.ok(ApiResponse.success("Insurance details extracted", details));
    }

    @GetMapping("/{fileId}")
    @Operation(summary = "Retrieve a file by ID")
    public ResponseEntity<Resource> getFile(@PathVariable Long fileId) {
        StoredFile storedFile = storedFileRepository.findById(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("File", fileId));

        if (storedFile.getCategory() != null && RESTRICTED_CATEGORIES.contains(storedFile.getCategory())) {
            enforceDocumentAccess(storedFile);
        }

        Resource resource = fileStorageService.loadAsResource(fileId);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(storedFile.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + storedFile.getOriginalFilename() + "\"")
                .body(resource);
    }

    /**
     * KYC/licence documents (OWNER_DOCUMENT, USER_DOCUMENT) are not publicly downloadable
     * even though GET /api/v1/files/** is permitAll at the security-filter-chain level
     * (that permitAll exists so public bike/profile images load in plain &lt;img&gt; tags
     * without a bearer token). The JwtAuthenticationFilter still runs on permitAll routes
     * and populates the SecurityContext when a valid bearer token is present, so we can
     * enforce ownership/role here at the controller layer.
     */
    private void enforceDocumentAccess(StoredFile storedFile) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth != null && auth.getPrincipal() instanceof PuduvandiUserPrincipal principal)) {
            throw new UnauthorizedException("Authentication is required to access this file.");
        }

        boolean isOwnerOfFile = principal.getUserId() != null
                && principal.getUserId().equals(storedFile.getUploadedByUserId());
        boolean isAdmin = "ADMIN".equals(principal.getRole());

        if (!isOwnerOfFile && !isAdmin) {
            throw new ForbiddenException("You do not have permission to access this file.");
        }
    }
}
