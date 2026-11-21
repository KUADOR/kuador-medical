package com.kuador.medical.platform.settings;

import com.kuador.medical.identity.OwnerInvitationPolicy;
import java.time.Duration;
import org.springframework.stereotype.Component;

/** Gives identity the validity of the OWNER invitation from the platform settings (plan D10). */
@Component
class SettingsOwnerInvitationPolicy implements OwnerInvitationPolicy {

    private final PlatformSettingsService settings;

    SettingsOwnerInvitationPolicy(PlatformSettingsService settings) {
        this.settings = settings;
    }

    @Override
    public Duration ownerInvitationTtl() {
        return Duration.ofHours(settings.current().ownerInvitationTtlHours());
    }
}
