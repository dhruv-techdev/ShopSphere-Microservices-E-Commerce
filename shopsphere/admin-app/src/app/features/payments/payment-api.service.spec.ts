import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { PaymentApiService } from './payment-api.service';

describe('PaymentApiService', () => {
  let api: PaymentApiService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(PaymentApiService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('lists payments with filters', () => {
    api.list({ status: 'FAILED', orderId: 42, page: 1, size: 50 }).subscribe();

    const req = httpMock.expectOne((r) => r.url === '/api/v1/admin/payments');
    expect(req.request.params.get('status')).toBe('FAILED');
    expect(req.request.params.get('orderId')).toBe('42');
    expect(req.request.params.get('page')).toBe('1');
    expect(req.request.params.get('sort')).toBe('createdAt,desc');
    req.flush({ content: [], number: 1, size: 50, totalElements: 0, totalPages: 0 });
  });

  it('passes the period start to the summary only when set', () => {
    api.summary('2026-09-24T12:00:00.000Z').subscribe();
    expect(httpMock.expectOne((r) => r.url === '/api/v1/admin/payments/summary').request.params.get('from')).toBe(
      '2026-09-24T12:00:00.000Z',
    );

    api.summary(null).subscribe();
    expect(httpMock.expectOne((r) => r.url === '/api/v1/admin/payments/summary').request.params.has('from')).toBeFalse();
  });

  it('reads duplicates from payment-service and order issues from order-service', () => {
    api.duplicates().subscribe();
    httpMock.expectOne('/api/v1/admin/payments/duplicates').flush([]);

    api.orderIssues(30).subscribe();
    const req = httpMock.expectOne((r) => r.url === '/api/v1/admin/orders/reconciliation');
    expect(req.request.params.get('pendingOlderThanMinutes')).toBe('30');
    req.flush([]);
  });
});
