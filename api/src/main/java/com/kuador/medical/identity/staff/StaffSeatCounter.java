package com.kuador.medical.identity.staff;

import com.kuador.medical.identity.AccountKind;
import com.kuador.medical.identity.ClinicAccountStatus;
import com.kuador.medical.identity.StaffSeats;
import com.kuador.medical.identity.account.ClinicAccountRepository;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Staff accounts of the current clinic that take a seat of the plan: invited, pending verification or active. */
@Component
class StaffSeatCounter implements StaffSeats {

    private static final List<ClinicAccountStatus> SEATED =
            List.of(ClinicAccountStatus.INVITED, ClinicAccountStatus.PENDING_EMAIL_VERIFICATION, ClinicAccountStatus.ACTIVE);

    private final ClinicAccountRepository accounts;

    StaffSeatCounter(ClinicAccountRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    @Transactional(readOnly = true)
    public long seatedStaff() {
        return accounts.countByKindAndStatus(AccountKind.STAFF, SEATED);
    }
}
