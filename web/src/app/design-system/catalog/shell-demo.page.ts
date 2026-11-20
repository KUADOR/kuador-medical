import { ChangeDetectionStrategy, Component } from '@angular/core';
import { KdButton, KdEmptyState, KdNav, type KdNavItem, KdShell } from '../index';

/**
 * Shell demo of the catalog (development builds only, route /design-system/shell): responsive
 * navigation with synthetic items. The real app adopts the shell in the screen tasks of MEDK-33.
 */
@Component({
  selector: 'mk-shell-demo',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [KdShell, KdNav, KdButton, KdEmptyState],
  template: `
    <kd-shell brand="Clínica Ficticia" brandLink="/design-system/shell">
      <kd-nav kdShellNav label="Navegación de ejemplo" [items]="items" />
      <button kdShellAccount kdButton variant="tertiary" icon="log-out" type="button">Salir</button>
      <kd-empty-state
        heading="Agenda del día"
        description="Aquí irá el contenido de la pantalla."
        icon="calendar"
      />
    </kd-shell>
  `,
})
export class ShellDemoPage {
  protected readonly items: KdNavItem[] = [
    { label: 'Agenda', link: '/design-system/shell', icon: 'calendar' },
    { label: 'Pacientes', link: '/design-system', icon: 'users' },
    { label: 'Configuración', link: '/status', icon: 'settings' },
  ];
}
