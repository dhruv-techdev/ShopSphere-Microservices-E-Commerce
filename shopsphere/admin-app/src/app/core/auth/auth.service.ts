import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import {
  Observable,
  catchError,
  finalize,
  map,
  of,
  shareReplay,
  switchMap,
  tap,
  throwError,
} from 'rxjs';

import { API_BASE_URL } from '../config/api.config';
import { skipAuth } from './auth-context';
import {
  AuthResponse,
  AuthSession,
  AuthUser,
  InvalidAuthResponseError,
  NotAdminError,
  SessionExpiredError,
  UserRole,
} from './auth.models';
import { SessionStore } from './session-store';

/** Treat the access token as expired this long before it really is (latency + clock skew). */
export const ACCESS_TOKEN_SKEW_MS = 30_000;

/** Only this role may use the admin app. */
export const REQUIRED_ROLE: UserRole = 'ADMIN';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly store = inject(SessionStore);
  private readonly authUrl = `${inject(API_BASE_URL)}/auth`;

  private readonly _session = signal<AuthSession | null>(this.store.load());
  /** Single-flight guard: refresh tokens rotate, so concurrent refreshes would trip reuse detection. */
  private refreshInFlight$: Observable<AuthSession> | null = null;

  readonly session = this._session.asReadonly();

  readonly currentUser = computed<AuthUser | null>(() => {
    const s = this._session();
    return s
      ? { userId: s.userId, email: s.email, firstName: s.firstName, lastName: s.lastName, role: s.role }
      : null;
  });

  readonly displayName = computed(() => {
    const user = this.currentUser();
    if (!user) {
      return '';
    }
    const fullName = [user.firstName, user.lastName].filter(Boolean).join(' ');
    return fullName || user.email;
  });

  /** POST /api/v1/auth/login. Fails with NotAdminError for non-ADMIN accounts. */
  login(email: string, password: string): Observable<AuthSession> {
    return this.http
      .post<AuthResponse>(`${this.authUrl}/login`, { email: email.trim(), password }, { context: skipAuth() })
      .pipe(
        map((res) => toSession(res, Date.now())),
        switchMap((session) => {
          if (session.role !== REQUIRED_ROLE) {
            // Don't leave a live refresh token behind for an account that can't use this app.
            return this.revoke(session.refreshToken).pipe(
              switchMap(() => throwError(() => new NotAdminError())),
            );
          }
          this.setSession(session);
          return of(session);
        }),
      );
  }

  /**
   * POST /api/v1/auth/refresh. Concurrent callers share one request.
   * If the server rejects the refresh token, the local session is cleared.
   * Network or 5xx failures keep the session so a transient blip doesn't log the admin out.
   */
  refresh(): Observable<AuthSession> {
    if (this.refreshInFlight$) {
      return this.refreshInFlight$;
    }
    const refreshToken = this._session()?.refreshToken;
    if (!refreshToken || !this.canRefresh()) {
      return throwError(() => new SessionExpiredError());
    }

    this.refreshInFlight$ = this.http
      .post<AuthResponse>(`${this.authUrl}/refresh`, { refreshToken }, { context: skipAuth() })
      .pipe(
        map((res) => toSession(res, Date.now())),
        tap((session) => {
          if (session.role !== REQUIRED_ROLE) {
            throw new NotAdminError();
          }
          this.setSession(session);
        }),
        catchError((err: unknown) => {
          if (isRejection(err)) {
            this.clearSession();
          }
          return throwError(() => err);
        }),
        finalize(() => {
          this.refreshInFlight$ = null;
        }),
        shareReplay({ bufferSize: 1, refCount: false }),
      );

    return this.refreshInFlight$;
  }

  /** Clears the local session, revokes the refresh token (best effort) and goes to /login. */
  logout(options: { redirect?: boolean } = {}): void {
    const refreshToken = this._session()?.refreshToken ?? null;
    this.clearSession();
    this.revoke(refreshToken).subscribe();
    if (options.redirect ?? true) {
      void this.router.navigate(['/login']);
    }
  }

  /** Used when the server has definitively rejected the session. */
  expireSession(): void {
    const returnUrl = this.router.url;
    this.clearSession();
    const queryParams: Record<string, string> = { reason: 'expired' };
    if (returnUrl && returnUrl !== '/' && !returnUrl.startsWith('/login')) {
      queryParams['returnUrl'] = returnUrl;
    }
    void this.router.navigate(['/login'], { queryParams });
  }

  accessToken(): string | null {
    return this._session()?.accessToken ?? null;
  }

  isAccessTokenFresh(skewMs: number = ACCESS_TOKEN_SKEW_MS): boolean {
    const s = this._session();
    return !!s && Date.now() < s.accessTokenExpiresAt - skewMs;
  }

  canRefresh(): boolean {
    const s = this._session();
    return !!s?.refreshToken && (s.refreshTokenExpiresAt === null || Date.now() < s.refreshTokenExpiresAt);
  }

  /** True while the access token is valid, or while it can still be refreshed. */
  isAuthenticated(): boolean {
    return this.isAccessTokenFresh(0) || this.canRefresh();
  }

  hasRole(role: UserRole): boolean {
    return this._session()?.role === role;
  }

  private setSession(session: AuthSession): void {
    this._session.set(session);
    this.store.save(session);
  }

  private clearSession(): void {
    this._session.set(null);
    this.store.clear();
  }

  /** POST /api/v1/auth/logout (public endpoint). Never fails. */
  private revoke(refreshToken: string | null): Observable<void> {
    if (!refreshToken) {
      return of(undefined);
    }
    return this.http
      .post<void>(`${this.authUrl}/logout`, { refreshToken }, { context: skipAuth() })
      .pipe(
        map(() => undefined),
        catchError(() => of(undefined)),
      );
  }
}

function toSession(res: AuthResponse | null, now: number): AuthSession {
  if (!res?.token || !res.email || !res.role) {
    throw new InvalidAuthResponseError();
  }
  return {
    userId: res.userId ?? null,
    email: res.email,
    firstName: res.firstName ?? null,
    lastName: res.lastName ?? null,
    role: res.role,
    accessToken: res.token,
    accessTokenExpiresAt: now + (res.expiresIn ?? 900) * 1000,
    refreshToken: res.refreshToken ?? null,
    refreshTokenExpiresAt:
      res.refreshToken && res.refreshExpiresIn != null ? now + res.refreshExpiresIn * 1000 : null,
  };
}

/** The server said "no" (vs. couldn't be reached). */
function isRejection(err: unknown): boolean {
  if (err instanceof HttpErrorResponse) {
    return err.status === 400 || err.status === 401 || err.status === 403;
  }
  return err instanceof NotAdminError || err instanceof InvalidAuthResponseError;
}
