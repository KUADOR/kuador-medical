import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';

/**
 * Simple informative page driven by route data (title, message).
 * Used for forbidden and not found.
 */
@Component({
  selector: 'mk-message-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  template: `
    <section class="message" aria-labelledby="message-title">
      <h1 id="message-title">{{ heading() }}</h1>
      <p>{{ message() }}</p>
      <p><a routerLink="/">Ir al estado del sistema</a></p>
    </section>
  `,
  styleUrl: './message.page.scss',
})
export class MessagePage {
  /** Bound from route data through withComponentInputBinding(). */
  readonly heading = input.required<string>();
  readonly message = input.required<string>();
}
