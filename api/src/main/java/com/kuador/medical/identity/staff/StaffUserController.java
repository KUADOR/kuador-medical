package com.kuador.medical.identity.staff;

import com.kuador.medical.identity.AuthenticatedAccount;
import com.kuador.medical.identity.ClinicAccountStatus;
import com.kuador.medical.identity.CurrentUser;
import com.kuador.medical.shared.security.RequiresPermission;
import com.kuador.medical.shared.web.ApiException;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** {@code /staff-users}: staff users of the current clinic and their invitations (MEDK-16). */
@RestController
@RequestMapping("/staff-users")
class StaffUserController {

    private final StaffUserService staffUsers;
    private final CurrentUser currentUser;

    StaffUserController(StaffUserService staffUsers, CurrentUser currentUser) {
        this.staffUsers = staffUsers;
        this.currentUser = currentUser;
    }

    @RequiresPermission("identity.users.read")
    @GetMapping
    StaffUserPage list(
            @RequestParam(required = false) @Nullable String q,
            @RequestParam(required = false) @Nullable ClinicAccountStatus status,
            @RequestParam(required = false) @Nullable UUID branchId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return staffUsers.list(new StaffUserService.Filter(q, status, branchId), page, size);
    }

    @RequiresPermission("identity.users.manage")
    @PostMapping
    ResponseEntity<StaffUserResponse> create(
            @RequestHeader(name = "Idempotency-Key", required = false) @Nullable String idempotencyKey,
            @Valid @RequestBody CreateStaffUserRequest request) {
        StaffUserResponse created = staffUsers.create(request, idempotencyKey, caller());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @RequiresPermission("identity.users.read")
    @GetMapping("/{id}")
    StaffUserResponse get(@PathVariable UUID id) {
        return staffUsers.get(id);
    }

    @RequiresPermission("identity.users.manage")
    @PatchMapping("/{id}")
    StaffUserResponse update(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return staffUsers.update(id, StaffUserUpdate.parse(body), caller());
    }

    @RequiresPermission("identity.users.manage")
    @PostMapping("/{id}/invitation")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void resendInvitation(@PathVariable UUID id) {
        staffUsers.resendInvitation(id, caller());
    }

    @RequiresPermission("identity.users.manage")
    @PostMapping("/{id}/disable")
    StaffUserResponse disable(@PathVariable UUID id) {
        return staffUsers.disable(id, caller());
    }

    @RequiresPermission("identity.users.manage")
    @PostMapping("/{id}/enable")
    StaffUserResponse enable(@PathVariable UUID id) {
        return staffUsers.enable(id, caller());
    }

    private AuthenticatedAccount caller() {
        return currentUser.current().orElseThrow(ApiException::unauthenticated);
    }
}
