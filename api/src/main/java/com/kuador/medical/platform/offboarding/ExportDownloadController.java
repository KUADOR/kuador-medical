package com.kuador.medical.platform.offboarding;

import com.kuador.medical.shared.security.PublicEndpoint;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /public/tenant-exports/download} (MEDK-70): the {@code downloadUrl} of a ready export.
 * No access token: the signed, short-lived grant is the authorization. An invalid, expired or
 * tampered grant, an expired export or a purged clinic respond 404. Never cached.
 */
@RestController
class ExportDownloadController {

    private final TenantExportService exports;

    ExportDownloadController(TenantExportService exports) {
        this.exports = exports;
    }

    @PublicEndpoint
    @GetMapping("/public/tenant-exports/download")
    ResponseEntity<byte[]> download(@RequestParam String grant) {
        TenantExportService.Download file = exports.download(grant);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.fileName()).build().toString())
                .cacheControl(CacheControl.noStore())
                .header("Referrer-Policy", "no-referrer")
                .body(file.content());
    }
}
