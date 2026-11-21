package com.kuador.medical.platform.offboarding;

import com.kuador.medical.audit.AuditAction;
import com.kuador.medical.audit.AuditRecorder;
import com.kuador.medical.audit.AuditTarget;
import com.kuador.medical.identity.AuthenticatedAccount;
import com.kuador.medical.platform.api.AccessMode;
import com.kuador.medical.platform.api.PlanCapabilities;
import com.kuador.medical.platform.api.TenantDataExporter;
import com.kuador.medical.platform.offboarding.ExportDownloadGrants.Grant;
import com.kuador.medical.platform.offboarding.OffboardingRequests.CreateExportRequest;
import com.kuador.medical.platform.offboarding.OffboardingResponses.TenantExportResponse;
import com.kuador.medical.shared.idempotency.IdempotentCreations;
import com.kuador.medical.shared.tenancy.TenantContext;
import com.kuador.medical.shared.web.ApiException;
import com.kuador.medical.shared.web.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import tools.jackson.databind.json.JsonMapper;

/**
 * Data export of the clinic of the access token (FR-021, ADR-018): every
 * {@link TenantDataExporter} adds its section, in JSON or CSV. Allowed in every access mode but
 * {@code NONE}, so a suspended or cancelled clinic can still take its data. The request is recorded
 * and audited first; the file is then generated in its own transaction (a failure leaves the
 * export {@code FAILED}, never half written). Downloads go through a short-lived signed link and are
 * audited too. Nothing of the content reaches the logs.
 */
@Service
class TenantExportService {

    static final String REQUEST_OPERATION = "requestTenantExport";
    static final String DOWNLOAD_PATH = "/api/v1/public/tenant-exports/download";

    private static final Logger log = LoggerFactory.getLogger(TenantExportService.class);

    private final TenantExportRepository exports;
    private final ExportFiles files;
    private final List<TenantDataExporter> exporters;
    private final IdempotentCreations idempotency;
    private final PlanCapabilities capabilities;
    private final AuditRecorder audit;
    private final JsonMapper json;
    private final ExportProperties properties;
    private final ExportDownloadGrants grants;
    private final String webBaseUrl;
    private final TransactionTemplate transaction;
    private final TransactionTemplate readOnly;
    private final Clock clock = Clock.systemUTC();

    TenantExportService(
            TenantExportRepository exports,
            ExportFiles files,
            List<TenantDataExporter> exporters,
            IdempotentCreations idempotency,
            PlanCapabilities capabilities,
            AuditRecorder audit,
            JsonMapper json,
            ExportProperties properties,
            @Value("${kuador.jwt.secret:}") String jwtSecret,
            @Value("${kuador.links.web-base-url:}") String webBaseUrl,
            PlatformTransactionManager transactionManager) {
        this.exports = exports;
        this.files = files;
        this.exporters = exporters.stream().sorted(Comparator.comparing(TenantDataExporter::section)).toList();
        this.idempotency = idempotency;
        this.capabilities = capabilities;
        this.audit = audit;
        this.json = json;
        this.properties = properties;
        String secret = properties.signingKey().isBlank() ? jwtSecret : properties.signingKey();
        this.grants = new ExportDownloadGrants(secret.getBytes(StandardCharsets.UTF_8));
        this.webBaseUrl = webBaseUrl.endsWith("/") ? webBaseUrl.substring(0, webBaseUrl.length() - 1) : webBaseUrl;
        this.transaction = new TransactionTemplate(transactionManager);
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
    }

    /** {@code POST /tenant/exports}: idempotent by {@code Idempotency-Key}. */
    TenantExportResponse request(CreateExportRequest request, @Nullable String idempotencyKey, AuthenticatedAccount actor) {
        UUID tenantId = tenant();
        IdempotentCreations.Outcome outcome = Objects.requireNonNull(transaction.execute(status ->
                idempotency.create(REQUEST_OPERATION, idempotencyKey, request, () -> create(tenantId, request, actor))));
        if (outcome.created()) {
            generate(tenantId, outcome.resourceId());
        }
        return get(outcome.resourceId(), actor);
    }

    /** {@code GET /tenant/exports/{exportId}}: another clinic's export is not found. */
    TenantExportResponse get(UUID exportId, AuthenticatedAccount actor) {
        TenantExport export = Objects.requireNonNull(readOnly.execute(status -> exports.findById(exportId)))
                .orElseThrow(ApiException::notFound);
        Instant now = clock.instant();
        TenantExport.Status status = export.statusAt(now);
        String downloadUrl = null;
        if (status == TenantExport.Status.READY) {
            Instant expiry = now.plus(properties.linkTtl());
            Instant linkExpiry = expiry.isAfter(Objects.requireNonNull(export.getExpiresAt())) ? export.getExpiresAt() : expiry;
            downloadUrl = base() + DOWNLOAD_PATH + "?grant="
                    + grants.issue(new Grant(tenant(), export.getId(), actor.accountId(), linkExpiry));
        }
        return new TenantExportResponse(export.getId(), export.getFormat(), status, export.getCreatedAt(),
                status == TenantExport.Status.READY || status == TenantExport.Status.EXPIRED ? export.getExpiresAt() : null,
                downloadUrl);
    }

    /** {@code GET /public/tenant-exports/download}: the file of a valid grant, audited. */
    Download download(String grantValue) {
        Instant now = clock.instant();
        Grant grant = grants.verify(grantValue, now).orElseThrow(ApiException::notFound);
        if (capabilities.accessMode(grant.tenantId()) == AccessMode.NONE) {
            throw ApiException.notFound();
        }
        return TenantContext.callAs(grant.tenantId(), () -> Objects.requireNonNull(transaction.execute(status -> {
            TenantExport export = exports.findById(grant.exportId()).orElseThrow(ApiException::notFound);
            byte[] content = files.read(grant.tenantId(), export.getId(), now).orElseThrow(ApiException::notFound);
            audit.record(AuditAction.TENANT_EXPORT_DOWNLOADED, grant.tenantId(), grant.accountId(),
                    AuditTarget.tenantExport(export.getId()));
            String fileName = "kuador-medical-export-" + LocalDate.ofInstant(export.getCreatedAt(), ZoneOffset.UTC)
                    + "." + export.getFormat().extension();
            return new Download(content, export.getFormat().contentType(), fileName);
        })));
    }

    /** A file to send. */
    record Download(byte[] content, String contentType, String fileName) {}

    private UUID create(UUID tenantId, CreateExportRequest request, AuthenticatedAccount actor) {
        if (request.id() != null && exports.existsById(request.id())) {
            throw ApiException.conflict(ErrorCode.RESOURCE_ID_TAKEN, "An export with this id already exists.");
        }
        TenantExport export = exports.saveAndFlush(new TenantExport(
                request.id() != null ? request.id() : UUID.randomUUID(), request.format(), actor.accountId(),
                clock.instant()));
        audit.record(AuditAction.TENANT_EXPORT_REQUESTED, tenantId, actor.accountId(),
                AuditTarget.tenantExport(export.getId()));
        return export.getId();
    }

    private void generate(UUID tenantId, UUID exportId) {
        try {
            TenantExport export = Objects.requireNonNull(readOnly.execute(status -> exports.findById(exportId))).orElseThrow();
            Instant now = clock.instant();
            Map<String, List<Map<String, Object>>> sections = Objects.requireNonNull(readOnly.execute(status -> collect()));
            byte[] content = ExportFileWriter.write(export.getFormat(), tenantId, now, sections, json);
            transaction.executeWithoutResult(status -> files.store(tenantId, exportId, content,
                    export.getFormat().contentType(), now, now.plus(properties.retention())));
        } catch (RuntimeException e) {
            log.error("Export {} of tenant {} failed", exportId, tenantId, e);
            transaction.executeWithoutResult(status -> files.fail(tenantId, exportId, clock.instant()));
        }
    }

    private Map<String, List<Map<String, Object>>> collect() {
        Map<String, List<Map<String, Object>>> sections = new LinkedHashMap<>();
        for (TenantDataExporter exporter : exporters) {
            sections.put(exporter.section(), new ArrayList<>(exporter.rows()));
        }
        return sections;
    }

    private String base() {
        return webBaseUrl.isBlank() ? ServletUriComponentsBuilder.fromCurrentContextPath().toUriString() : webBaseUrl;
    }

    private static UUID tenant() {
        return TenantContext.current().orElseThrow(() -> new IllegalStateException("No clinic is bound"));
    }
}
