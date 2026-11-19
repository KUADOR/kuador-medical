import { Injectable, signal } from '@angular/core';

/** One-shot confirmation message carried across a navigation (e.g. "Rol creado"). */
@Injectable({ providedIn: 'root' })
export class NoticeService {
  private readonly pending = signal<string | null>(null);

  show(message: string): void {
    this.pending.set(message);
  }

  /** Returns the pending message once and forgets it. */
  take(): string | null {
    const message = this.pending();
    this.pending.set(null);
    return message;
  }
}
