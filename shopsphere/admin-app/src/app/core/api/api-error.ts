import { HttpErrorResponse } from '@angular/common/http';
import { AbstractControl } from '@angular/forms';

import { ApiError } from '../auth/auth.models';

export function apiErrorBody(err: unknown): ApiError | null {
  if (err instanceof HttpErrorResponse && typeof err.error === 'object' && err.error !== null) {
    return err.error as ApiError;
  }
  return null;
}

/**
 * Field -> message map from a 400 response, or {} when absent. Understands both the Java
 * ApiError (`fieldErrors: {field: message}`) and .NET ValidationProblem (`errors: {field: [messages]}`).
 */
export function fieldErrorsOf(err: unknown): Record<string, string> {
  const body = apiErrorBody(err);
  const raw = body?.fieldErrors ?? body?.errors;
  if (typeof raw !== 'object' || raw === null || Array.isArray(raw)) {
    return {};
  }
  const result: Record<string, string> = {};
  for (const [field, value] of Object.entries(raw as Record<string, unknown>)) {
    const message = Array.isArray(value) ? value.find((v) => typeof v === 'string') : value;
    if (typeof message === 'string') {
      result[field] = message;
    }
  }
  return result;
}

/** A message an admin can act on. Server-side 5xx details are never shown verbatim. */
export function apiErrorMessage(err: unknown, fallback: string): string {
  if (!(err instanceof HttpErrorResponse)) {
    return fallback;
  }
  if (err.status === 0) {
    return 'Cannot reach the server. Check your connection and try again.';
  }
  if (err.status === 401) {
    return 'Your session has expired. Please sign in again.';
  }
  if (err.status === 403) {
    return 'You do not have permission to do that.';
  }
  if (err.status >= 500) {
    return fallback;
  }
  if (err.status === 400 && Object.keys(fieldErrorsOf(err)).length > 0) {
    return 'Please fix the highlighted fields.';
  }
  const body = apiErrorBody(err);
  return body?.message || body?.detail || fallback;
}

/**
 * Puts server-side validation messages on the matching form controls as a `server` error.
 * Returns the messages whose field has no control, so the caller can show them elsewhere.
 * The `server` error disappears as soon as the user edits the field (validators re-run).
 */
export function applyFieldErrors(form: AbstractControl, errors: Record<string, string>): string[] {
  const unmatched: string[] = [];
  for (const [field, message] of Object.entries(errors)) {
    const control = form.get(field);
    if (control) {
      control.setErrors({ ...(control.errors ?? {}), server: message });
      control.markAsTouched();
    } else {
      unmatched.push(message);
    }
  }
  return unmatched;
}
