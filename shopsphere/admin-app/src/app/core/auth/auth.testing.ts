// Test helpers only — not referenced from the app bundle.
import { AuthResponse, AuthSession } from './auth.models';
import { SESSION_STORAGE_KEY } from './session-store';

export function buildSession(overrides: Partial<AuthSession> = {}): AuthSession {
  const now = Date.now();
  return {
    userId: 1,
    email: 'admin@shopsphere.io',
    firstName: 'Ada',
    lastName: 'Admin',
    role: 'ADMIN',
    accessToken: 'access-1',
    accessTokenExpiresAt: now + 15 * 60_000,
    refreshToken: 'refresh-1',
    refreshTokenExpiresAt: now + 14 * 24 * 3_600_000,
    ...overrides,
  };
}

/** Writes a session to sessionStorage; call BEFORE AuthService is first injected. */
export function seedSession(overrides: Partial<AuthSession> = {}): AuthSession {
  const session = buildSession(overrides);
  sessionStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(session));
  return session;
}

export function authResponse(overrides: Partial<AuthResponse> = {}): AuthResponse {
  return {
    userId: 1,
    email: 'admin@shopsphere.io',
    firstName: 'Ada',
    lastName: 'Admin',
    role: 'ADMIN',
    emailVerified: true,
    token: 'access-1',
    tokenType: 'Bearer',
    expiresIn: 900,
    refreshToken: 'refresh-1',
    refreshExpiresIn: 1_209_600,
    ...overrides,
  };
}
