package com.kuador.medical.platform.offboarding;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * The file of an export, in {@code tenant_export.content}. Native SQL, always filtered by the
 * clinic explicitly (ADR-007; covered by the isolation tests of {@code TenantExportIT}), so the
 * entity never loads the file. Only {@link #expire} runs across clinics: it drops files, it reads
 * none.
 */
@Component
class ExportFiles {

    private final JdbcTemplate jdbc;

    ExportFiles(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Stores the file and marks the export READY until {@code expiresAt}. */
    void store(UUID tenantId, UUID exportId, byte[] content, String contentType, Instant now, Instant expiresAt) {
        jdbc.update("""
                update tenant_export
                set status = 'READY', content = ?, content_type = ?, size_bytes = ?, completed_at = ?, expires_at = ?
                where id = ? and tenant_id = ? and status = 'PENDING'""",
                content, contentType, (long) content.length, Timestamp.from(now), Timestamp.from(expiresAt),
                exportId, tenantId);
    }

    void fail(UUID tenantId, UUID exportId, Instant now) {
        jdbc.update("""
                update tenant_export set status = 'FAILED', completed_at = ?
                where id = ? and tenant_id = ? and status = 'PENDING'""",
                Timestamp.from(now), exportId, tenantId);
    }

    /** The file of a READY export of the clinic that has not expired at {@code now}. */
    Optional<byte[]> read(UUID tenantId, UUID exportId, Instant now) {
        List<byte[]> found = jdbc.query("""
                select content from tenant_export
                where id = ? and tenant_id = ? and status = 'READY' and expires_at > ? and content is not null""",
                (rs, row) -> rs.getBytes(1), exportId, tenantId, Timestamp.from(now));
        return found.stream().findFirst();
    }

    /** Drops the files of every export expired at {@code now}; returns how many. */
    int expire(Instant now) {
        return jdbc.update("""
                update tenant_export set status = 'EXPIRED', content = null
                where status = 'READY' and expires_at <= ?""",
                Timestamp.from(now));
    }

    /** Deletes every export of the clinic, with its file (purge, ADR-018). */
    int deleteAll(UUID tenantId) {
        return jdbc.update("delete from tenant_export where tenant_id = ?", tenantId);
    }
}
