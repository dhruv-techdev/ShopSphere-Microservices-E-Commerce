import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { InventoryApiService } from './inventory-api.service';

describe('InventoryApiService', () => {
  let api: InventoryApiService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(InventoryApiService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('reads the low-stock list', () => {
    api.lowStock().subscribe();
    expect(httpMock.expectOne('/api/v1/inventory/low-stock').request.method).toBe('GET');
  });

  it('restocks with an INCREMENT update', () => {
    api.restock(5, 40).subscribe();
    const req = httpMock.expectOne('/api/v1/inventory/5');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ operation: 'INCREMENT', quantity: 40 });
  });
});
