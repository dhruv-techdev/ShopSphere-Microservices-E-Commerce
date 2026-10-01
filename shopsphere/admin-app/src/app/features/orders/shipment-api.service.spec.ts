import { provideHttpClient } from '@angular/common/http';
import { HttpErrorResponse } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { Shipment } from './order.models';
import { buildShipment } from './orders.testing';
import { ShipmentApiService } from './shipment-api.service';

describe('ShipmentApiService', () => {
  let api: ShipmentApiService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(ShipmentApiService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('returns null when the order has no shipment (404)', () => {
    let result: Shipment | null | undefined;
    api.getByOrder(42).subscribe((s) => (result = s));

    httpMock
      .expectOne('/api/v1/shipments/order/42')
      .flush({ title: 'Not Found' }, { status: 404, statusText: 'Not Found' });

    expect(result).toBeNull();
  });

  it('propagates other errors', () => {
    let error: HttpErrorResponse | undefined;
    api.getByOrder(42).subscribe({ error: (e: HttpErrorResponse) => (error = e) });

    httpMock.expectOne('/api/v1/shipments/order/42').flush(null, { status: 503, statusText: 'Unavailable' });

    expect(error?.status).toBe(503);
  });

  it('posts ship / deliver / cancel transitions', () => {
    api.ship(11, { carrier: 'UPS', trackingNumber: '1Z999AA10123456784' }).subscribe();
    const ship = httpMock.expectOne('/api/v1/shipments/11/ship');
    expect(ship.request.method).toBe('POST');
    expect(ship.request.body).toEqual({ carrier: 'UPS', trackingNumber: '1Z999AA10123456784' });
    ship.flush(buildShipment({ status: 'SHIPPED' }));

    api.deliver(11).subscribe();
    httpMock.expectOne('/api/v1/shipments/11/deliver').flush(buildShipment({ status: 'DELIVERED' }));

    api.cancel(11).subscribe();
    httpMock.expectOne('/api/v1/shipments/11/cancel').flush(buildShipment({ status: 'CANCELLED' }));
  });
});
