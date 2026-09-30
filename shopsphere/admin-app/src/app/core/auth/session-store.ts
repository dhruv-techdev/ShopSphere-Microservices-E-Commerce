import { Injectable } from '@angular/core';

import { AuthSession } from './auth.models';

export const SESSION_STORAGE_KEY = 'shopsphere.admin.session';

/**
 * Persists the admin session in sessionStorage so it survives a reload but is
 * dropped when the tab closes. All access is guarded: if storage is blocked the
 * session simply lives in memory.
 */
@Injectable({ providedIn: 'root' })
export class SessionStore {
  load(): AuthSession | null {
    try {
      const raw = sessionStorage.getItem(SESSION_STORAGE_KEY);
      if (!raw) {
        return null;
      }
      const parsed: unknown = JSON.parse(raw);
      return isAuthSession(parsed) ? parsed : null;
    } catch {
      return null;
    }
  }

  save(session: AuthSession): void {
    try {
      sessionStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(session));
    } catch {
      // storage unavailable — session stays in memory only
    }
  }

  clear(): void {
    try {
      sessionStorage.removeItem(SESSION_STORAGE_KEY);
    } catch {
      // storage unavailable — nothing to clear
    }
  }
}

function isAuthSession(value: unknown): value is AuthSession {
  if (typeof value !== 'object' || value === null) {
    return false;
  }
  const v = value as Record<string, unknown>;
  return (
    typeof v['email'] === 'string' &&
    (v['role'] === 'ADMIN' || v['role'] === 'CUSTOMER') &&
    typeof v['accessToken'] === 'string' &&
    typeof v['accessTokenExpiresAt'] === 'number' &&
    (v['refreshToken'] === null || typeof v['refreshToken'] === 'string') &&
    (v['refreshTokenExpiresAt'] === null || typeof v['refreshTokenExpiresAt'] === 'number')
  );
}
