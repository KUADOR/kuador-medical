package com.kuador.medical.platform.settings;

import com.kuador.medical.audit.AuditAction;
import com.kuador.medical.audit.AuditRecorder;
import com.kuador.medical.identity.AuthenticatedAccount;
import com.kuador.medical.shared.web.ApiException;
import com.kuador.medical.shared.web.ErrorCode;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Platform settings (plan D10), stored one row per key in {@code platform_setting}. Other parts
 * of the platform read them with {@link #current()}; only the SUPER_ADMIN changes them, audited.
 * New values apply to periods computed from then on; dates already set are not recomputed.
 */
@Service
public class PlatformSettingsService {

    static final String TRIAL_DAYS = "TRIAL_DAYS";
    static final String GRACE_DAYS = "GRACE_DAYS";
    static final String SUSPENSION_DAYS_BEFORE_CANCEL = "SUSPENSION_DAYS_BEFORE_CANCEL";
    static final String RETENTION_DAYS = "RETENTION_DAYS";
    static final String OWNER_INVITATION_TTL_HOURS = "OWNER_INVITATION_TTL_HOURS";
    static final String PRICE_CHANGE_NOTICE_DAYS = "PRICE_CHANGE_NOTICE_DAYS";

    private final JdbcTemplate jdbc;
    private final AuditRecorder audit;
    private final Clock clock = Clock.systemUTC();

    PlatformSettingsService(JdbcTemplate jdbc, AuditRecorder audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    /** The settings in force. */
    @Transactional(readOnly = true)
    public PlatformSettings current() {
        return read("select key, value, version from platform_setting");
    }

    /** Replaces every setting if {@code settings.version()} is the current one, else {@code VERSION_CONFLICT}. */
    @Transactional
    public PlatformSettings update(PlatformSettings settings, AuthenticatedAccount actor) {
        // The row lock serializes concurrent updates, so the version check below cannot race.
        PlatformSettings stored = read("select key, value, version from platform_setting for update");
        if (stored.version().longValue() != settings.version().longValue()) {
            throw ApiException.conflict(ErrorCode.VERSION_CONFLICT, "The settings changed since they were read.");
        }
        long next = stored.version() + 1;
        Timestamp now = Timestamp.from(clock.instant());
        Map<String, Integer> values = Map.of(
                TRIAL_DAYS, settings.trialDays(),
                GRACE_DAYS, settings.graceDays(),
                SUSPENSION_DAYS_BEFORE_CANCEL, settings.suspensionDaysBeforeCancel(),
                RETENTION_DAYS, settings.retentionDays(),
                OWNER_INVITATION_TTL_HOURS, settings.ownerInvitationTtlHours(),
                PRICE_CHANGE_NOTICE_DAYS, settings.priceChangeNoticeDays());
        values.forEach((key, value) -> jdbc.update(
                "update platform_setting set value = ?, version = ?, updated_by = ?, updated_at = ? where key = ?",
                value, next, actor.accountId(), now, key));
        audit.record(AuditAction.PLATFORM_SETTINGS_UPDATED, null, actor.accountId(), null);
        return current();
    }

    private PlatformSettings read(String sql) {
        Map<String, Integer> values = new HashMap<>();
        long[] version = {0};
        jdbc.query(sql, rs -> {
            values.put(rs.getString("key"), rs.getInt("value"));
            version[0] = Math.max(version[0], rs.getLong("version"));
        });
        return new PlatformSettings(
                required(values, TRIAL_DAYS),
                required(values, GRACE_DAYS),
                required(values, SUSPENSION_DAYS_BEFORE_CANCEL),
                required(values, RETENTION_DAYS),
                required(values, OWNER_INVITATION_TTL_HOURS),
                required(values, PRICE_CHANGE_NOTICE_DAYS),
                version[0]);
    }

    private static Integer required(Map<String, Integer> values, String key) {
        Integer value = values.get(key);
        if (value == null) {
            throw new IllegalStateException("Missing platform setting " + key);
        }
        return value;
    }
}
