package com.flowbinary.platformservice.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import com.flowbinary.platformservice.authz.sdk.RequiresPrivilege;
import com.flowbinary.platformservice.dto.storage.FileMetadataResponse;
import com.flowbinary.platformservice.dto.storage.FileUploadResponse;
import com.flowbinary.platformservice.model.FileType;
import com.flowbinary.platformservice.model.Tenant;
import com.flowbinary.platformservice.model.TenantFile;
import com.flowbinary.platformservice.security.TenantContextResolver;
import com.flowbinary.platformservice.service.FileStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/files")
@PreAuthorize("@authz.member(authentication)")
@RequiredArgsConstructor
@Tag(name = "File Storage", description = "Upload, download, and manage tenant files")
public class FileStorageController {

    private final FileStorageService    fileStorageService;
    private final TenantContextResolver tenantContextResolver;

    // ── Upload ────────────────────────────────────────────────────────────────

    @PostMapping(value = "/upload/{fileType}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Upload a file for the current tenant")
    FileUploadResponse upload(
            Authentication authentication,
            @PathVariable FileType fileType,
            @RequestPart("file") MultipartFile file) {

        Tenant tenant    = tenantContextResolver.resolveRequired(authentication);
        UUID   uploadedBy = extractUserId(authentication);
        TenantFile stored = fileStorageService.upload(tenant, uploadedBy, fileType, file);

        // Generate a download URL for non-public files
        String downloadUrl = stored.getPublicUrl() != null
                ? null
                : "/api/v1/files/" + stored.getId() + "/download";

        return FileUploadResponse.from(stored, downloadUrl);
    }

    // ── List ──────────────────────────────────────────────────────────────────

    @GetMapping
    @Operation(summary = "List files for the current tenant")
    Page<FileMetadataResponse> listFiles(
            Authentication authentication,
            @Parameter(description = "Filter by file type (optional)")
            @RequestParam(required = false) FileType fileType,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        Tenant tenant = tenantContextResolver.resolveRequired(authentication);
        return fileStorageService.listFiles(tenant, fileType, page, size)
                .map(FileMetadataResponse::from);
    }

    // ── Get metadata ──────────────────────────────────────────────────────────

    @GetMapping("/{id}")
    @Operation(summary = "Get metadata for a single file")
    FileMetadataResponse getFile(
            Authentication authentication,
            @PathVariable UUID id) {

        Tenant tenant = tenantContextResolver.resolveRequired(authentication);
        return FileMetadataResponse.from(fileStorageService.getMetadata(tenant, id));
    }

    // ── Download ──────────────────────────────────────────────────────────────

    @GetMapping("/{id}/download")
    @Operation(summary = "Download file bytes (streamed)")
    ResponseEntity<InputStreamResource> download(
            Authentication authentication,
            @PathVariable UUID id) {

        Tenant tenant   = tenantContextResolver.resolveRequired(authentication);
        TenantFile meta = fileStorageService.getMetadata(tenant, id);
        var obj         = fileStorageService.download(tenant, id);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment().filename(meta.getOriginalName()).build());
        if (obj.getContentType() != null) {
            headers.setContentType(MediaType.parseMediaType(obj.getContentType()));
        }
        return ResponseEntity.ok().headers(headers).body(new InputStreamResource(obj.getData()));
    }

    // ── Pre-signed URL ────────────────────────────────────────────────────────

    @GetMapping("/{id}/url")
    @Operation(summary = "Get a time-limited download URL (S3 pre-signed or in-app)")
    java.util.Map<String, String> getDownloadUrl(
            Authentication authentication,
            @PathVariable UUID id) {

        Tenant tenant = tenantContextResolver.resolveRequired(authentication);
        String url    = fileStorageService.presignedUrl(tenant, id);
        if (url == null) {
            url = "/api/v1/files/" + id + "/download";
        }
        return java.util.Map.of("url", url);
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @DeleteMapping("/{id}")
    @RequiresPrivilege("platform.document.delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Soft-delete a file (marks status=DELETED, frees quota)")
    void delete(
            Authentication authentication,
            @PathVariable UUID id) {

        Tenant tenant = tenantContextResolver.resolveRequired(authentication);
        fileStorageService.delete(tenant, id);
    }

    // ── Latest by type ────────────────────────────────────────────────────────

    @GetMapping("/latest/{fileType}")
    @Operation(summary = "Get metadata for the most recently uploaded file of a given type")
    FileMetadataResponse getLatest(
            Authentication authentication,
            @PathVariable FileType fileType) {

        Tenant tenant = tenantContextResolver.resolveRequired(authentication);
        return FileMetadataResponse.from(fileStorageService.getLatest(tenant, fileType));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static UUID extractUserId(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtToken) {
            Jwt jwt = jwtToken.getToken();
            String sub = jwt.getSubject();
            return sub != null ? UUID.fromString(sub) : null;
        }
        return null;
    }
}
