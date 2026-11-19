package com.kuador.medical.identity.role;

import com.kuador.medical.identity.AuthenticatedAccount;
import com.kuador.medical.identity.CurrentUser;
import com.kuador.medical.shared.security.RequiresPermission;
import com.kuador.medical.shared.web.ApiException;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * {@code /roles}: roles of the caller's clinic. Only clinic permissions grant access, so a
 * PLATFORM token is always forbidden here (FR-014).
 */
@RestController
@RequestMapping("/roles")
class RoleController {

    private final RoleService roles;
    private final CurrentUser currentUser;

    RoleController(RoleService roles, CurrentUser currentUser) {
        this.roles = roles;
        this.currentUser = currentUser;
    }

    @RequiresPermission("roles.read")
    @GetMapping
    List<RoleResponse> list() {
        return roles.list().stream().map(RoleResponse::of).toList();
    }

    @RequiresPermission("roles.create")
    @PostMapping
    ResponseEntity<RoleResponse> create(@Valid @RequestBody CreateRoleRequest request) {
        RoleView created = roles.create(request.toCommand(), caller());
        var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(RoleResponse.of(created));
    }

    @RequiresPermission("roles.read")
    @GetMapping("/{id}")
    RoleResponse get(@PathVariable UUID id) {
        return RoleResponse.of(roles.get(id));
    }

    @RequiresPermission("roles.update")
    @PutMapping("/{id}")
    RoleResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateRoleRequest request) {
        return RoleResponse.of(roles.update(id, request.toCommand(), request.version(), caller()));
    }

    @RequiresPermission("roles.delete")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id) {
        roles.delete(id, caller());
    }

    private AuthenticatedAccount caller() {
        return currentUser.current().orElseThrow(ApiException::unauthenticated);
    }
}
