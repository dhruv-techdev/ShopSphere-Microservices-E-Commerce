import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { AuthSession, InvalidAuthResponseError, NotAdminError, SessionExpiredError } from './auth.models';
import { AuthService } from './auth.service';
import { authResponse, seedSession } from './auth.testing';
import { SESSION_STORAGE_KEY } from './session-store';

describe('AuthService', () => {
  let httpMock: HttpTestingController | undefined;
  let router: Router;

  function create(): AuthService {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    spyOn(router, 'navigate').and.resolveTo(true);
    return TestBed.inject(AuthService);
  }

  function http(): HttpTestingController {
    return httpMock as HttpTestingController;
  }

  function storedSession(): AuthSession | null {
    const raw = sessionStorage.getItem(SESSION_STORAGE_KEY);
    return raw ? (JSON.parse(raw) as AuthSession) : null;
  }

  beforeEach(() => {
    httpMock = undefined;
    sessionStorage.clear();
  });

  afterEach(() => {
    httpMock?.verify();
    sessionStorage.clear();
  });

  describe('login', () => {
    it('stores the session for an ADMIN account', () => {
      const service = create();
      let result: AuthSession | undefined;

      service.login('  admin@shopsphere.io ', 'Secret123!').subscribe((s) => (result = s));

      const req = http().expectOne('/api/v1/auth/login');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ email: 'admin@shopsphere.io', password: 'Secret123!' });
      expect(req.request.headers.has('Authorization')).toBeFalse();
      req.flush(authResponse());

      expect(result?.accessToken).toBe('access-1');
      expect(service.session()?.role).toBe('ADMIN');
      expect(service.isAuthenticated()).toBeTrue();
      expect(service.displayName()).toBe('Ada Admin');
      expect(storedSession()?.refreshToken).toBe('refresh-1');
    });

    it('rejects non-admin accounts and revokes the refresh token that was issued', () => {
      const service = create();
      let error: unknown;

      service.login('customer@shopsphere.io', 'Secret123!').subscribe({ error: (e) => (error = e) });

      http()
        .expectOne('/api/v1/auth/login')
        .flush(authResponse({ role: 'CUSTOMER', token: 'c-access', refreshToken: 'c-refresh' }));
      const logout = http().expectOne('/api/v1/auth/logout');
      expect(logout.request.body).toEqual({ refreshToken: 'c-refresh' });
      logout.flush(null, { status: 204, statusText: 'No Content' });

      expect(error).toEqual(jasmine.any(NotAdminError));
      expect(service.session()).toBeNull();
      expect(storedSession()).toBeNull();
    });

    it('propagates 401 without storing anything', () => {
      const service = create();
      let error: HttpErrorResponse | undefined;

      service.login('admin@shopsphere.io', 'wrong').subscribe({ error: (e: HttpErrorResponse) => (error = e) });
      http()
        .expectOne('/api/v1/auth/login')
        .flush({ status: 401, error: 'Unauthorized', message: 'Invalid email or password' }, { status: 401, statusText: 'Unauthorized' });

      expect(error?.status).toBe(401);
      expect(service.session()).toBeNull();
    });

    it('fails on a response without an access token', () => {
      const service = create();
      let error: unknown;

      service.login('admin@shopsphere.io', 'pw').subscribe({ error: (e) => (error = e) });
      http().expectOne('/api/v1/auth/login').flush(authResponse({ token: undefined }));

      expect(error).toEqual(jasmine.any(InvalidAuthResponseError));
      expect(service.session()).toBeNull();
    });
  });

  describe('token lifetime', () => {
    it('treats the access token as stale inside the skew window but keeps the session refreshable', () => {
      jasmine.clock().install();
      try {
        jasmine.clock().mockDate(new Date(Date.UTC(2026, 0, 1)));
        const service = create();
        service.login('admin@shopsphere.io', 'pw').subscribe();
        http().expectOne('/api/v1/auth/login').flush(authResponse({ expiresIn: 900 }));

        expect(service.isAccessTokenFresh()).toBeTrue();

        jasmine.clock().tick(900_000 - 29_000);

        expect(service.isAccessTokenFresh()).toBeFalse();
        expect(service.canRefresh()).toBeTrue();
        expect(service.isAuthenticated()).toBeTrue();
      } finally {
        jasmine.clock().uninstall();
      }
    });

    it('is not authenticated once both tokens have expired', () => {
      seedSession({ accessTokenExpiresAt: Date.now() - 1, refreshTokenExpiresAt: Date.now() - 1 });
      const service = create();

      expect(service.isAuthenticated()).toBeFalse();
    });
  });

  describe('refresh', () => {
    it('shares one request between concurrent callers and rotates the stored tokens', () => {
      seedSession();
      const service = create();
      const tokens: string[] = [];

      service.refresh().subscribe((s) => tokens.push(s.accessToken));
      service.refresh().subscribe((s) => tokens.push(s.accessToken));

      const req = http().expectOne('/api/v1/auth/refresh');
      expect(req.request.body).toEqual({ refreshToken: 'refresh-1' });
      req.flush(authResponse({ token: 'access-2', refreshToken: 'refresh-2' }));

      expect(tokens).toEqual(['access-2', 'access-2']);
      expect(service.accessToken()).toBe('access-2');
      expect(storedSession()?.refreshToken).toBe('refresh-2');
    });

    it('clears the session when the server rejects the refresh token', () => {
      seedSession();
      const service = create();
      let error: HttpErrorResponse | undefined;

      service.refresh().subscribe({ error: (e: HttpErrorResponse) => (error = e) });
      http()
        .expectOne('/api/v1/auth/refresh')
        .flush({ error: 'Unauthorized' }, { status: 401, statusText: 'Unauthorized' });

      expect(error?.status).toBe(401);
      expect(service.session()).toBeNull();
      expect(storedSession()).toBeNull();
    });

    it('keeps the session on a network failure', () => {
      seedSession();
      const service = create();

      service.refresh().subscribe({ error: () => undefined });
      http().expectOne('/api/v1/auth/refresh').error(new ProgressEvent('error'));

      expect(service.session()?.refreshToken).toBe('refresh-1');
    });

    it('fails fast without a refresh token', () => {
      seedSession({ refreshToken: null, refreshTokenExpiresAt: null });
      const service = create();
      let error: unknown;

      service.refresh().subscribe({ error: (e) => (error = e) });

      expect(error).toEqual(jasmine.any(SessionExpiredError));
      http().expectNone('/api/v1/auth/refresh');
    });
  });

  describe('session lifecycle', () => {
    it('restores a stored session on startup', () => {
      seedSession({ email: 'restored@shopsphere.io' });
      const service = create();

      expect(service.session()?.email).toBe('restored@shopsphere.io');
    });

    it('ignores corrupt stored data', () => {
      sessionStorage.setItem(SESSION_STORAGE_KEY, '{not json');
      const service = create();

      expect(service.session()).toBeNull();
    });

    it('logout clears the session, revokes the refresh token and navigates to /login', () => {
      seedSession();
      const service = create();

      service.logout();

      expect(service.session()).toBeNull();
      expect(storedSession()).toBeNull();
      const req = http().expectOne('/api/v1/auth/logout');
      expect(req.request.body).toEqual({ refreshToken: 'refresh-1' });
      req.flush(null, { status: 204, statusText: 'No Content' });
      expect(router.navigate).toHaveBeenCalledWith(['/login']);
    });
  });
});
