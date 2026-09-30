import { HttpErrorResponse } from '@angular/common/http';

import { ApiError, InvalidAuthResponseError, NotAdminError } from '../../core/auth/auth.models';

export const DEFAULT_AFTER_LOGIN = '/dashboard';

/** Only same-app absolute paths are allowed as post-login redirects. */
export function safeReturnUrl(raw: string | null | undefined): string {
  if (
    !raw ||
    !raw.startsWith('/') ||
    raw.startsWith('//') ||
    raw.startsWith('/\\') ||
    raw.startsWith('/login')
  ) {
    return DEFAULT_AFTER_LOGIN;
  }
  return raw;
}

export function loginNotice(reason: string | null): string | null {
  if (reason === 'expired') {
    return 'Your session has expired. Please sign in again.';
  }
  if (reason === 'forbidden') {
    return 'Administrator access is required for this app.';
  }
  return null;
}

/** Maps user-service /auth/login failures to something an admin can act on. */
export function loginErrorMessage(err: unknown): string {
  if (err instanceof NotAdminError || err instanceof InvalidAuthResponseError) {
    return err.message;
  }
  if (!(err instanceof HttpErrorResponse)) {
    return 'Something went wrong. Please try again.';
  }

  const body: ApiError | null =
    typeof err.error === 'object' && err.error !== null ? (err.error as ApiError) : null;

  if (err.status === 0) {
    return 'Cannot reach the server. Check your connection and try again.';
  }
  if (err.status === 401) {
    return 'Invalid email or password.';
  }
  if (err.status === 403 && body?.error === 'EMAIL_NOT_VERIFIED') {
    return 'Your email address is not verified yet. Use the link we emailed you, then sign in.';
  }
  if (err.status === 403) {
    return 'This account is not allowed to sign in.';
  }
  if (err.status === 400) {
    return body?.message || 'Please check your email and password.';
  }
  if (err.status === 429) {
    return 'Too many sign-in attempts. Please wait a moment and try again.';
  }
  if (err.status >= 500) {
    return 'The server could not sign you in right now. Please try again shortly.';
  }
  return 'Something went wrong. Please try again.';
}
