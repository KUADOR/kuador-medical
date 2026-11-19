package com.kuador.medical.identity.role;

import com.kuador.medical.identity.AuthenticatedAccount;
import com.kuador.medical.identity.CurrentUser;
import com.kuador.medical.shared.security.RequiresPermission;
import com.kuador.medical.shared.web.ApiException;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** {@code /staff-users/{id}/role-assignments}: branch role assignments of a staff user (ADR-013). */
@RestController
class RoleAssignmentController {

    private final AssignmentService assignments;
    private final CurrentUser currentUser;

    RoleAssignmentController(AssignmentService assignments, CurrentUser currentUser) {
        this.assignments = assignments;
        this.currentUser = currentUser;
    }

    @RequiresPermission("identity.assignments.manage")
    @PutMapping("/staff-users/{id}/role-assignments")
    List<RoleAssignmentResponse> replace(@PathVariable UUID id, @Valid @RequestBody RoleAssignmentsRequest request) {
        AuthenticatedAccount caller = currentUser.current().orElseThrow(ApiException::unauthenticated);
        return assignments.replace(id, request, caller).stream().map(RoleAssignmentResponse::of).toList();
    }
}
