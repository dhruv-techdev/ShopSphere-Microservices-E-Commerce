import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';

import { adminGuard, authGuard, guestGuard } from './auth.guards';
import { AuthService } from './auth.service';
import { seedSession } from './auth.testing';

describe('auth guards', () => {
  const route = {} as ActivatedRouteSnapshot;
  const state = (url: string) => ({ url }) as RouterStateSnapshot;

  function setup(): void {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
  }

  function serialize(tree: UrlTree): string {
    return TestBed.inject(Router).serializeUrl(tree);
  }

  beforeEach(() => sessionStorage.clear());
  afterEach(() => sessionStorage.clear());

  describe('authGuard', () => {
    it('allows a signed-in user', () => {
      seedSession();
      setup();

      const result = TestBed.runInInjectionContext(() => authGuard(route, state('/dashboard')));

      expect(result).toBe(true);
    });

    it('allows a user whose access token expired but can still refresh', () => {
      seedSession({ accessTokenExpiresAt: Date.now() - 1 });
      setup();

      const result = TestBed.runInInjectionContext(() => authGuard(route, state('/dashboard')));

      expect(result).toBe(true);
    });

    it('redirects anonymous users to /login with a returnUrl', () => {
      setup();

      const result = TestBed.runInInjectionContext(() => authGuard(route, state('/dashboard'))) as UrlTree;

      expect(result instanceof UrlTree).toBeTrue();
      expect(serialize(result)).toMatch(/^\/login\?/);
      expect(result.queryParams['returnUrl']).toBe('/dashboard');
    });

    it('redirects when both tokens have expired', () => {
      seedSession({ accessTokenExpiresAt: Date.now() - 1, refreshTokenExpiresAt: Date.now() - 1 });
      setup();

      const result = TestBed.runInInjectionContext(() => authGuard(route, state('/dashboard')));

      expect(result instanceof UrlTree).toBeTrue();
    });
  });

  describe('adminGuard', () => {
    it('allows ADMIN', () => {
      seedSession();
      setup();

      const result = TestBed.runInInjectionContext(() => adminGuard(route, state('/dashboard')));

      expect(result).toBe(true);
    });

    it('rejects a non-admin session, clears it and revokes the refresh token', () => {
      seedSession({ role: 'CUSTOMER', refreshToken: 'c-refresh' });
      setup();

      const result = TestBed.runInInjectionContext(() => adminGuard(route, state('/dashboard'))) as UrlTree;

      expect(result instanceof UrlTree).toBeTrue();
      expect(result.queryParams['reason']).toBe('forbidden');
      expect(TestBed.inject(AuthService).session()).toBeNull();
      const httpMock = TestBed.inject(HttpTestingController);
      httpMock.expectOne('/api/v1/auth/logout').flush(null, { status: 204, statusText: 'No Content' });
      httpMock.verify();
    });

    it('sends anonymous users to /login', () => {
      setup();

      const result = TestBed.runInInjectionContext(() => adminGuard(route, state('/dashboard'))) as UrlTree;

      expect(serialize(result)).toBe('/login');
    });
  });

  describe('guestGuard', () => {
    it('lets anonymous users see the login page', () => {
      setup();

      const result = TestBed.runInInjectionContext(() => guestGuard(route, state('/login')));

      expect(result).toBe(true);
    });

    it('sends signed-in admins to the dashboard', () => {
      seedSession();
      setup();

      const result = TestBed.runInInjectionContext(() => guestGuard(route, state('/login'))) as UrlTree;

      expect(serialize(result)).toBe('/dashboard');
    });
  });
});
