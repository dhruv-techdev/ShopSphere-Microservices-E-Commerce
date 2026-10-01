import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { convertToParamMap } from '@angular/router';

import { PageResult } from '../../core/api/page';
import { AdminOrderSummary } from './order.models';
import { OrderApiService } from './order-api.service';
import { parseOrderQuery } from './order-query';
import { buildOrder, buildOrderSummary } from './orders.testing';

describe('OrderApiService', () => {
  let api: OrderApiService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(OrderApiService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('lists orders through the admin route with filters', () => {
    let result: PageResult<AdminOrderSummary> | undefined;
    api.list(parseOrderQuery(convertToParamMap({ status: 'PAID', userId: '7' }))).subscribe((p) => (result = p));

    const req = httpMock.expectOne((r) => r.url === '/api/v1/admin/orders');
    expect(req.request.params.get('status')).toBe('PAID');
    expect(req.request.params.get('userId')).toBe('7');
    req.flush({ content: [buildOrderSummary()], number: 0, size: 20, totalElements: 1, totalPages: 1 });

    expect(result?.content[0].id).toBe(42);
  });

  it('fetches and cancels a single order', () => {
    api.get(42).subscribe();
    httpMock.expectOne('/api/v1/admin/orders/42').flush(buildOrder());

    api.cancel(42).subscribe();
    const cancel = httpMock.expectOne('/api/v1/admin/orders/42/cancel');
    expect(cancel.request.method).toBe('POST');
    cancel.flush(buildOrder({ status: 'CANCELLED' }));
  });
});
