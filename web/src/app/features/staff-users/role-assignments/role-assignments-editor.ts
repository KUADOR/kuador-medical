import { ChangeDetectionStrategy, Component, computed, input, model } from '@angular/core';
import type { BranchSummary, Role } from '../../../core/api/api-types';
import { AssignmentDraft, AssignmentMode, assignableRoles } from './role-assignments';

/**
 * Chooses the roles of a staff user per branch: the same role in every branch (default) or one
 * role per branch, with "no access" allowed in some of them.
 */
@Component({
  selector: 'mk-role-assignments-editor',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fieldset
      class="field"
      [attr.aria-describedby]="error() ? idPrefix() + '-error' : idPrefix() + '-hint'"
      data-testid="assignments-editor"
    >
      <legend>Roles por sucursal</legend>
      <p [id]="idPrefix() + '-hint'" class="field__hint">
        Por defecto la persona tiene el mismo rol en todas las sucursales. Puedes darle un rol
        distinto en cada una (por ejemplo, ADMIN en la matriz y Médico en otra).
      </p>

      <div class="radio-group">
        <label class="checkbox">
          <input
            type="radio"
            [name]="idPrefix() + '-mode'"
            value="ALL_BRANCHES"
            [checked]="draft().mode === 'ALL_BRANCHES'"
            [disabled]="disabled()"
            (change)="setMode('ALL_BRANCHES')"
            data-testid="mode-all"
          />
          <span>El mismo rol en todas las sucursales</span>
        </label>
        <label class="checkbox">
          <input
            type="radio"
            [name]="idPrefix() + '-mode'"
            value="PER_BRANCH"
            [checked]="draft().mode === 'PER_BRANCH'"
            [disabled]="disabled()"
            (change)="setMode('PER_BRANCH')"
            data-testid="mode-per-branch"
          />
          <span>Un rol distinto por sucursal</span>
        </label>
      </div>

      @if (draft().mode === 'ALL_BRANCHES') {
        <div class="field">
          <label [for]="idPrefix() + '-role'">Rol</label>
          <select
            [id]="idPrefix() + '-role'"
            [disabled]="disabled()"
            [attr.aria-invalid]="error() ? 'true' : null"
            (change)="setRole($event)"
            data-testid="all-branches-role"
          >
            <option value="" [selected]="!draft().roleId">Elige un rol</option>
            @for (role of options(); track role.id) {
              <option [value]="role.id" [selected]="role.id === draft().roleId">
                {{ roleLabel(role) }}
              </option>
            }
          </select>
        </div>
      } @else {
        <div class="table-wrapper">
          <table class="table">
            <caption class="visually-hidden">
              Rol en cada sucursal
            </caption>
            <thead>
              <tr>
                <th scope="col">Sucursal</th>
                <th scope="col">Rol</th>
              </tr>
            </thead>
            <tbody>
              @for (branch of branches(); track branch.id) {
                <tr>
                  <th scope="row">
                    <label [for]="idPrefix() + '-branch-' + branch.id">{{ branch.name }}</label>
                  </th>
                  <td>
                    <select
                      [id]="idPrefix() + '-branch-' + branch.id"
                      [disabled]="disabled()"
                      [attr.aria-invalid]="error() ? 'true' : null"
                      [attr.data-branch]="branch.id"
                      (change)="setBranchRole(branch.id, $event)"
                      data-testid="branch-role"
                    >
                      <option value="" [selected]="!draft().byBranch[branch.id]">Sin acceso</option>
                      @for (role of options(); track role.id) {
                        <option
                          [value]="role.id"
                          [selected]="role.id === draft().byBranch[branch.id]"
                        >
                          {{ roleLabel(role) }}
                        </option>
                      }
                    </select>
                  </td>
                </tr>
              } @empty {
                <tr>
                  <td colspan="2">No hay sucursales disponibles.</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }

      @if (error(); as message) {
        <p [id]="idPrefix() + '-error'" class="field__error">{{ message }}</p>
      }
    </fieldset>
  `,
})
export class RoleAssignmentsEditor {
  readonly draft = model.required<AssignmentDraft>();
  readonly roles = input.required<readonly Role[]>();
  readonly branches = input.required<readonly BranchSummary[]>();
  /** Only an OWNER is offered the OWNER role. */
  readonly callerIsOwner = input(false);
  readonly disabled = input(false);
  readonly error = input<string | null>(null);
  readonly idPrefix = input('assignments');

  protected readonly options = computed(() =>
    assignableRoles(this.roles(), this.callerIsOwner(), this.draft().mode),
  );

  protected roleLabel(role: Role): string {
    return role.clinical ? `${role.name} (rol clínico)` : role.name;
  }

  protected setMode(mode: AssignmentMode): void {
    const current = this.draft();
    if (current.mode === mode) {
      return;
    }
    const allowed = new Set(
      assignableRoles(this.roles(), this.callerIsOwner(), mode).map((r) => r.id),
    );
    if (mode === 'PER_BRANCH') {
      // Start from the role chosen for every branch, so switching modes keeps the choice.
      const byBranch: Record<string, string> = {};
      for (const branch of this.branches()) {
        const roleId = current.roleId || current.byBranch[branch.id] || '';
        byBranch[branch.id] = allowed.has(roleId) ? roleId : '';
      }
      this.draft.set({ mode, roleId: '', byBranch });
    } else {
      const roles = new Set(Object.values(current.byBranch).filter(Boolean));
      const roleId = roles.size === 1 ? [...roles][0] : '';
      this.draft.set({ mode, roleId: allowed.has(roleId) ? roleId : '', byBranch: {} });
    }
  }

  protected setRole(event: Event): void {
    const roleId = (event.target as HTMLSelectElement).value;
    this.draft.set({ ...this.draft(), roleId });
  }

  protected setBranchRole(branchId: string, event: Event): void {
    const roleId = (event.target as HTMLSelectElement).value;
    const current = this.draft();
    this.draft.set({ ...current, byBranch: { ...current.byBranch, [branchId]: roleId } });
  }
}
