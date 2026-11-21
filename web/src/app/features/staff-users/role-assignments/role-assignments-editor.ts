import { ChangeDetectionStrategy, Component, computed, input, model } from '@angular/core';
import type { BranchSummary, Role } from '../../../core/api/api-types';
import { KdField, KdFieldGroup, KdIcon, KdInput } from '../../../design-system';
import { AssignmentDraft, AssignmentMode, assignableRoles } from './role-assignments';

/**
 * Chooses the roles of a staff user per branch: the same role in every branch (default) or one
 * role per branch, with "no access" allowed in some of them.
 */
@Component({
  selector: 'mk-role-assignments-editor',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [KdField, KdFieldGroup, KdIcon, KdInput],
  template: `
    <fieldset
      kdFieldGroup
      legend="Roles por sucursal"
      [groupId]="idPrefix()"
      hint="Por defecto la persona tiene el mismo rol en todas las sucursales. Puedes darle un rol distinto en cada una (por ejemplo, ADMIN en la matriz y Médico en otra)."
      data-testid="assignments-editor"
    >
      <div class="assignments__modes">
        <kd-field label="El mismo rol en todas las sucursales">
          <input
            kdInput
            type="radio"
            [name]="idPrefix() + '-mode'"
            value="ALL_BRANCHES"
            [checked]="draft().mode === 'ALL_BRANCHES'"
            [disabled]="disabled()"
            (change)="setMode('ALL_BRANCHES')"
            data-testid="mode-all"
          />
        </kd-field>
        <kd-field label="Un rol distinto por sucursal">
          <input
            kdInput
            type="radio"
            [name]="idPrefix() + '-mode'"
            value="PER_BRANCH"
            [checked]="draft().mode === 'PER_BRANCH'"
            [disabled]="disabled()"
            (change)="setMode('PER_BRANCH')"
            data-testid="mode-per-branch"
          />
        </kd-field>
      </div>

      @if (draft().mode === 'ALL_BRANCHES') {
        <kd-field label="Rol">
          <select
            kdInput
            [id]="idPrefix() + '-role'"
            [disabled]="disabled()"
            [attr.aria-invalid]="error() ? 'true' : null"
            [attr.aria-describedby]="error() ? idPrefix() + '-error' : null"
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
        </kd-field>
      } @else {
        <div class="assignments__branches" role="group" aria-label="Rol en cada sucursal">
          @for (branch of branches(); track branch.id) {
            <kd-field [label]="branch.name">
              <select
                kdInput
                [id]="idPrefix() + '-branch-' + branch.id"
                [disabled]="disabled()"
                [attr.aria-invalid]="error() ? 'true' : null"
                [attr.aria-describedby]="error() ? idPrefix() + '-error' : null"
                [attr.data-branch]="branch.id"
                (change)="setBranchRole(branch.id, $event)"
                data-testid="branch-role"
              >
                <option value="" [selected]="!draft().byBranch[branch.id]">Sin acceso</option>
                @for (role of options(); track role.id) {
                  <option [value]="role.id" [selected]="role.id === draft().byBranch[branch.id]">
                    {{ roleLabel(role) }}
                  </option>
                }
              </select>
            </kd-field>
          } @empty {
            <p class="assignments__empty">No hay sucursales disponibles.</p>
          }
        </div>
      }

      @if (error(); as message) {
        <p [id]="idPrefix() + '-error'" class="assignments__error">
          <kd-icon name="circle-alert" [size]="20" />
          <span>{{ message }}</span>
        </p>
      }
    </fieldset>
  `,
  styleUrl: './role-assignments-editor.scss',
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
