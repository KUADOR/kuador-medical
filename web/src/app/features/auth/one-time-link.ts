import { Location } from '@angular/common';
import { inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { fragmentToken } from '../../core/session/login-navigation';

/**
 * Takes the one-time token of an email link (`#t=<token>`, ADR-014) and removes the fragment
 * from the address bar and the history entry, so the token is not left behind in the browser.
 * Must be called in an injection context (a page constructor or field initializer).
 */
export function takeLinkToken(path: string): string | null {
  const token = fragmentToken(inject(ActivatedRoute).snapshot.fragment);
  if (token) {
    inject(Location).replaceState(path);
  }
  return token;
}
