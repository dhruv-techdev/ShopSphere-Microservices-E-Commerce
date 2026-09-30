export type UserRole = 'CUSTOMER' | 'ADMIN';

export interface LoginRequest {
  email: string;
  password: string;
}

/** Mirrors user-service AuthResponse (null fields are omitted by the API). */
export interface AuthResponse {
  userId?: number;
  email?: string;
  firstName?: string;
  lastName?: string;
  role?: UserRole;
  emailVerified?: boolean;
  token?: string;
  tokenType?: string;
  /** Access token lifetime in seconds. */
  expiresIn?: number;
  refreshToken?: string;
  /** Refresh token lifetime in seconds. */
  refreshExpiresIn?: number;
  message?: string;
}

/** Mirrors the backend ApiError body. */
export interface ApiError {
  timestamp?: string;
  status?: number;
  error?: string;
  message?: string;
  path?: string;
  fieldErrors?: unknown;
}

export interface AuthUser {
  userId: number | null;
  email: string;
  firstName: string | null;
  lastName: string | null;
  role: UserRole;
}

export interface AuthSession extends AuthUser {
  accessToken: string;
  /** Epoch millis (client clock) at which the access token expires. */
  accessTokenExpiresAt: number;
  refreshToken: string | null;
  /** Epoch millis at which the refresh token expires, or null if unknown. */
  refreshTokenExpiresAt: number | null;
}

export class NotAdminError extends Error {
  constructor() {
    super('This account does not have administrator access.');
    this.name = 'NotAdminError';
  }
}

export class InvalidAuthResponseError extends Error {
  constructor() {
    super('The server returned an unexpected sign-in response.');
    this.name = 'InvalidAuthResponseError';
  }
}

export class SessionExpiredError extends Error {
  constructor() {
    super('Your session has expired. Please sign in again.');
    this.name = 'SessionExpiredError';
  }
}
