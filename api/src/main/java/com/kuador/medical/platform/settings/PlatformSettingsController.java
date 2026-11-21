package com.kuador.medical.platform.settings;

import com.kuador.medical.identity.CurrentUser;
import com.kuador.medical.shared.security.RequiresPermission;
import com.kuador.medical.shared.web.ApiException;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** {@code /platform/settings}: lifecycle periods of the platform (plan D10), SUPER_ADMIN only. */
@RestController
@RequiresPermission("platform.settings.manage")
class PlatformSettingsController {

    private final PlatformSettingsService settings;
    private final CurrentUser currentUser;

    PlatformSettingsController(PlatformSettingsService settings, CurrentUser currentUser) {
        this.settings = settings;
        this.currentUser = currentUser;
    }

    @GetMapping("/platform/settings")
    PlatformSettings get() {
        return settings.current();
    }

    @PutMapping("/platform/settings")
    PlatformSettings update(@Valid @RequestBody PlatformSettings request) {
        return settings.update(request, currentUser.current().orElseThrow(ApiException::unauthenticated));
    }
}
