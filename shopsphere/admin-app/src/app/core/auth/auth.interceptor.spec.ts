import { HttpClient, HttpErrorResponse, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';
import { authResponse, seedSession } from './auth.testing';

describe('authInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController | undefined;
  let router: Router;

  const unauthorized = { status: 401, statusText: 'Unauthorized' };

  function setup(): void {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    });
    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    spyOn(router, 'navigate').and.resolveTo(true);
  }

  function mock(): HttpTestingController {
    return httpMock as HttpTestingController;
  }

  beforeEach(() => {
    httpMock = undefined;
    sessionStorage.clear();
  });

  afterEach(() => {
    httpMock?.verify();
    sessionStorage.clear();
  });

  it('attaches the bearer token to API requests', () => {
    seedSession();
    setup();

    http.get('/api/v1/admin/orders').subscribe();

    const req = mock().expectOne('/api/v1/admin/orders');
    expect(req.request.headers.get('Authorization')).toBe('Bearer access-1');
    req.flush([]);
  });

  it('never attaches the token to non-API or absolute URLs', () => {
    seedSession();
    setup();

    http.get('/assets/config.json').subscribe();
    http.get('https://example.com/api/v1/admin/orders').subscribe();

    const local = mock().expectOne('/assets/config.json');
    const external = mock().expectOne('https://example.com/api/v1/admin/orders');
    expect(local.request.headers.has('Authorization')).toBeFalse();
    expect(external.request.headers.has('Authorization')).toBeFalse();
    local.flush({});
    external.flush({});
  });

  it('sends API requests without a token when nobody is signed in', () => {
    setup();

    http.get('/api/v1/products').subscribe();

    const req = mock().expectOne('/api/v1/products');
    expect(req.request.headers.has('Authorization')).toBeFalse();
    req.flush([]);
  });

  it('refreshes proactively when the access token is about to expire', () => {
    seedSession({ accessTokenExpiresAt: Date.now() - 1_000 });
    setup();

    http.get('/api/v1/admin/orders').subscribe();

    mock().expectNone('/api/v1/admin/orders');
    mock().expectOne('/api/v1/auth/refresh').flush(authResponse({ token: 'access-2', refreshToken: 'refresh-2' }));
    const req = mock().expectOne('/api/v1/admin/orders');
    expect(req.request.headers.get('Authorization')).toBe('Bearer access-2');
    req.flush([]);
  });

  it('on 401 refreshes once and retries with the new token', () => {
    seedSession();
    setup();
    let body: unknown;

    http.get('/api/v1/admin/orders').subscribe((b) => (body = b));

    mock().expectOne('/api/v1/admin/orders').flush({ message: 'expired' }, unauthorized);
    const refresh = mock().expectOne('/api/v1/auth/refresh');
    expect(refresh.request.body).toEqual({ refreshToken: 'refresh-1' });
    expect(refresh.request.headers.has('Authorization')).toBeFalse();
    refresh.flush(authResponse({ token: 'access-2', refreshToken: 'refresh-2' }));

    const retry = mock().expectOne('/api/v1/admin/orders');
    expect(retry.request.headers.get('Authorization')).toBe('Bearer access-2');
    retry.flush({ ok: true });

    expect(body).toEqual({ ok: true });
  });

  it('uses a single refresh for concurrent 401s', () => {
    seedSession();
    setup();

    http.get('/api/v1/a').subscribe();
    http.get('/api/v1/b').subscribe();

    mock().expectOne('/api/v1/a').flush(null, unauthorized);
    mock().expectOne('/api/v1/b').flush(null, unauthorized);
    mock().expectOne('/api/v1/auth/refresh').flush(authResponse({ token: 'access-2', refreshToken: 'refresh-2' }));

    const a = mock().expectOne('/api/v1/a');
    const b = mock().expectOne('/api/v1/b');
    expect(a.request.headers.get('Authorization')).toBe('Bearer access-2');
    expect(b.request.headers.get('Authorization')).toBe('Bearer access-2');
    a.flush({});
    b.flush({});
  });

  it('does not refresh again when the retried request is also rejected', () => {
    seedSession();
    setup();
    let error: HttpErrorResponse | undefined;

    http.get('/api/v1/admin/orders').subscribe({ error: (e: HttpErrorResponse) => (error = e) });

    mock().expectOne('/api/v1/admin/orders').flush(null, unauthorized);
    mock().expectOne('/api/v1/auth/refresh').flush(authResponse({ token: 'access-2', refreshToken: 'refresh-2' }));
    mock().expectOne('/api/v1/admin/orders').flush(null, unauthorized);

    mock().expectNone('/api/v1/auth/refresh');
    expect(error?.status).toBe(401);
  });

  it('expires the session and redirects to /login when the refresh is rejected', () => {
    seedSession();
    setup();
    let error: HttpErrorResponse | undefined;

    http.get('/api/v1/admin/orders').subscribe({ error: (e: HttpErrorResponse) => (error = e) });

    mock().expectOne('/api/v1/admin/orders').flush(null, unauthorized);
    mock().expectOne('/api/v1/auth/refresh').flush({ error: 'Unauthorized' }, unauthorized);

    expect(error?.status).toBe(401);
    expect(TestBed.inject(AuthService).session()).toBeNull();
    expect(router.navigate).toHaveBeenCalledWith(
      ['/login'],
      jasmine.objectContaining({ queryParams: jasmine.objectContaining({ reason: 'expired' }) }),
    );
  });

  it('passes 403 through untouched', () => {
    seedSession();
    setup();
    let error: HttpErrorResponse | undefined;

    http.post('/api/v1/products', {}).subscribe({ error: (e: HttpErrorResponse) => (error = e) });
    mock().expectOne('/api/v1/products').flush(null, { status: 403, statusText: 'Forbidden' });

    mock().expectNone('/api/v1/auth/refresh');
    expect(error?.status).toBe(403);
    expect(TestBed.inject(AuthService).session()).not.toBeNull();
  });
});
