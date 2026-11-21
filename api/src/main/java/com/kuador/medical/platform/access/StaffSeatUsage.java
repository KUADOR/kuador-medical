package com.kuador.medical.platform.access;

import com.kuador.medical.identity.StaffSeats;
import com.kuador.medical.platform.api.PlanUsageProvider;
import com.kuador.medical.platform.plan.LimitCode;
import org.springframework.stereotype.Component;

/**
 * Usage of {@code MAX_USERS}: the seated staff accounts that identity reports. The platform
 * adapts it because identity cannot depend on the platform (the platform already uses identity),
 * so identity exposes {@link StaffSeats} instead of implementing the SPI itself.
 */
@Component
class StaffSeatUsage implements PlanUsageProvider {

    private final StaffSeats seats;

    StaffSeatUsage(StaffSeats seats) {
        this.seats = seats;
    }

    @Override
    public LimitCode limit() {
        return LimitCode.MAX_USERS;
    }

    @Override
    public long currentUsage() {
        return seats.seatedStaff();
    }
}
