// Test-only helpers of the design system specs: render a host template and settle it.
import { Type } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

export async function render<T>(host: Type<T>): Promise<ComponentFixture<T>> {
  TestBed.configureTestingModule({
    imports: [host],
    providers: [provideRouter([])],
  });
  const fixture = TestBed.createComponent(host);
  document.body.appendChild(fixture.nativeElement);
  await settle(fixture);
  return fixture;
}

export async function settle(fixture: ComponentFixture<unknown>): Promise<void> {
  await new Promise((resolve) => setTimeout(resolve));
  await fixture.whenStable();
  fixture.detectChanges();
}

/** Root element of a fixture. */
export function el(fixture: ComponentFixture<unknown>): HTMLElement {
  return fixture.nativeElement as HTMLElement;
}

/**
 * jsdom has no <dialog> modal API: a minimal stand-in that toggles `open` and fires `close`,
 * enough to test the component's wiring (real browsers are covered by the visual tests).
 */
export function polyfillDialog(): void {
  const proto = HTMLDialogElement.prototype as HTMLDialogElement & { __kdPolyfill?: boolean };
  if (typeof proto.showModal === 'function' && !proto.__kdPolyfill) {
    return;
  }
  proto.__kdPolyfill = true;
  proto.showModal = function (this: HTMLDialogElement) {
    this.setAttribute('open', '');
  };
  proto.close = function (this: HTMLDialogElement) {
    if (this.hasAttribute('open')) {
      this.removeAttribute('open');
      this.dispatchEvent(new Event('close'));
    }
  };
}
