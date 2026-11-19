package com.kuador.medical.audit.internal;

import com.kuador.medical.audit.AuditAction;
import com.kuador.medical.audit.AuditRecorder;
import com.kuador.medical.audit.AuditTarget;
import java.time.Clock;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class JpaAuditRecorder implements AuditRecorder {

    private final AuditEventRepository events;
    private final Clock clock = Clock.systemUTC();

    JpaAuditRecorder(AuditEventRepository events) {
        this.events = events;
    }

    @Override
    @Transactional
    public void record(AuditAction action, @Nullable UUID tenantId, @Nullable UUID actorId, @Nullable AuditTarget target) {
        events.save(new AuditEvent(
                action,
                tenantId,
                actorId,
                target != null ? target.type() : null,
                target != null ? target.id() : null,
                clock.instant()));
    }
}
