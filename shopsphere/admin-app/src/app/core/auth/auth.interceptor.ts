import { HttpErrorResponse, HttpEvent, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { Observable, catchError, switchMap, throwError } from 'rxjs';

import { API_BASE_URL } from '../config/api.config';
import { SKIP_AUTH } from './auth-context';
import { AuthService } from './auth.service';

/**
 * Attaches `Authorization: Bearer <access token>` to same-origin API calls.
 *
 *  - Access token about to expire  -> refresh first, then send.
 *  - 401 response                   -> refresh once and retry once.
 *  - Another request already rotated the token -> just retry with the new one.
 *  - Refresh rejected by the server -> session cleared, redirect to /login?reason=expired.
 *
 * Tokens are never attached to absolute or third-party URLs.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const apiBaseUrl = inject(API_BASE_URL);

  if (req.context.get(SKIP_AUTH) || !isApiRequest(req.url, apiBaseUrl)) {
    return next(req);
  }

  let sentToken: string | null = null;
  let refreshed = false;

  const forward = (token: string | null): Observable<HttpEvent<unknown>> => {
    sentToken = token;
    return next(token ? withBearer(req, token) : req);
  };

  const refreshThenForward = (): Observable<HttpEvent<unknown>> => {
    refreshed = true;
    return auth.refresh().pipe(
      catchError((err: unknown) => {
        // refresh() drops the session only when the server rejected the refresh token.
        if (!auth.isAuthenticated()) {
          auth.expireSession();
        }
        return throwError(() => err);
      }),
      switchMap((session) => forward(session.accessToken)),
    );
  };

  const first$ =
    auth.isAccessTokenFresh() || !auth.canRefresh() ? forward(auth.accessToken()) : refreshThenForward();

  return first$.pipe(
    catchError((err: unknown) => {
      if (!(err instanceof HttpErrorResponse) || err.status !== 401 || sentToken === null || refreshed) {
        return throwError(() => err);
      }

      const current = auth.accessToken();
      if (current && current !== sentToken && auth.isAccessTokenFresh()) {
        return forward(current);
      }
      if (auth.canRefresh()) {
        return refreshThenForward();
      }

      auth.expireSession();
      return throwError(() => err);
    }),
  );
};

function isApiRequest(url: string, apiBaseUrl: string): boolean {
  return url === apiBaseUrl || url.startsWith(`${apiBaseUrl}/`) || url.startsWith(`${apiBaseUrl}?`);
}

function withBearer(req: HttpRequest<unknown>, token: string): HttpRequest<unknown> {
  return req.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
}
