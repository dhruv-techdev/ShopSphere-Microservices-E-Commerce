import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { NotificationApiService } from './notification-api.service';

describe('NotificationApiService', () => {
  let api: NotificationApiService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(NotificationApiService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('lists with only the filters that are set', () => {
    api.list({ status: 'FAILED', type: null, userId: 7, orderId: null, recipient: 'jane@', page: 0, size: 25 }).subscribe();

    const req = httpMock.expectOne((r) => r.url === '/api/v1/admin/notifications');
    expect(req.request.params.get('status')).toBe('FAILED');
    expect(req.request.params.get('userId')).toBe('7');
    expect(req.request.params.get('recipient')).toBe('jane@');
    expect(req.request.params.has('type')).toBeFalse();
    expect(req.request.params.has('orderId')).toBeFalse();
    req.flush({ content: [] });
  });

  it('reads the summary and retries by id', () => {
    api.summary().subscribe();
    httpMock.expectOne('/api/v1/admin/notifications/summary').flush({ total: 0, byStatus: {} });

    api.retry(15).subscribe();
    const req = httpMock.expectOne('/api/v1/admin/notifications/15/retry');
    expect(req.request.method).toBe('POST');
  });
});
