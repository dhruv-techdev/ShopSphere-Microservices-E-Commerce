import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { UserApiService } from './user-api.service';

describe('UserApiService', () => {
  let api: UserApiService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(UserApiService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('maps the status filter onto enabled / emailVerified', () => {
    api.list({ q: 'ada', role: 'ADMIN', status: 'ACTIVE', page: 0, size: 20 }).subscribe();
    let req = httpMock.expectOne((r) => r.url === '/api/v1/admin/users');
    expect(req.request.params.get('q')).toBe('ada');
    expect(req.request.params.get('role')).toBe('ADMIN');
    expect(req.request.params.get('enabled')).toBe('true');
    expect(req.request.params.get('emailVerified')).toBe('true');
    req.flush({ content: [], number: 0, size: 20, totalElements: 0, totalPages: 0 });

    api.list({ q: null, role: null, status: 'DISABLED', page: 0, size: 20 }).subscribe();
    req = httpMock.expectOne((r) => r.url === '/api/v1/admin/users');
    expect(req.request.params.get('enabled')).toBe('false');
    expect(req.request.params.has('emailVerified')).toBeFalse();
    expect(req.request.params.has('q')).toBeFalse();
    req.flush({ content: [] });

    api.list({ q: null, role: null, status: 'UNVERIFIED', page: 0, size: 20 }).subscribe();
    req = httpMock.expectOne((r) => r.url === '/api/v1/admin/users');
    expect(req.request.params.get('emailVerified')).toBe('false');
    expect(req.request.params.has('enabled')).toBeFalse();
    req.flush({ content: [] });
  });
});
