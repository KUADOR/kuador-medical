import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import qrcode from 'qrcode-generator';

/** Modules of empty border around the code, as the QR specification requires. */
const QUIET_ZONE = 4;

/**
 * SVG path of the dark modules of `text` encoded as a QR code (error correction M), plus the
 * side of its square viewBox including the quiet zone. Built locally: the secret in the
 * otpauth:// URI never leaves the browser.
 */
export function qrPath(text: string): { path: string; size: number } {
  const code = qrcode(0, 'M');
  code.addData(text);
  code.make();
  const count = code.getModuleCount();
  const parts: string[] = [];
  for (let row = 0; row < count; row++) {
    for (let col = 0; col < count; col++) {
      if (code.isDark(row, col)) {
        parts.push(`M${col + QUIET_ZONE} ${row + QUIET_ZONE}h1v1h-1z`);
      }
    }
  }
  return { path: parts.join(''), size: count + QUIET_ZONE * 2 };
}

/**
 * QR code of a text (the otpauth:// URI of the TOTP enrollment). Always drawn dark on light,
 * also in the dark theme, so every authenticator app can scan it.
 */
@Component({
  selector: 'mk-qr-code',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <svg
      class="qr-code__image"
      role="img"
      [attr.aria-label]="label()"
      [attr.viewBox]="'0 0 ' + code().size + ' ' + code().size"
      shape-rendering="crispEdges"
      focusable="false"
    >
      <path class="qr-code__modules" [attr.d]="code().path" />
    </svg>
  `,
  styleUrl: './qr-code.scss',
  host: {
    class: 'qr-code',
    // Light token values in this subtree, whatever the theme of the page.
    'data-theme': 'light',
  },
})
export class QrCode {
  readonly value = input.required<string>();
  readonly label = input.required<string>();

  protected readonly code = computed(() => qrPath(this.value()));
}
