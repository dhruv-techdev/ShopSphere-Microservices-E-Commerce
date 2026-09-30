import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { convertToParamMap } from '@angular/router';

import { PageResult } from '../../core/api/page';
import { Product, ProductRequest } from './catalog.models';
import { buildProduct } from './catalog.testing';
import { ProductApiService } from './product-api.service';
import { parseProductQuery } from './product-query';

describe('ProductApiService', () => {
  let api: ProductApiService;
  let httpMock: HttpTestingController;

  const request: ProductRequest = {
    name: 'Wireless Mouse',
    description: null,
    price: 29.99,
    categoryId: 3,
    stockQuantity: 12,
    active: true,
  };

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(ProductApiService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('searches with filters, paging and sort, and normalises the page', () => {
    let result: PageResult<Product> | undefined;
    api
      .search(parseProductQuery(convertToParamMap({ q: 'mouse', active: 'false', page: '1', size: '10', sort: 'price,desc' })))
      .subscribe((page) => (result = page));

    const req = httpMock.expectOne((r) => r.url === '/api/v1/products');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('name')).toBe('mouse');
    expect(req.request.params.get('active')).toBe('false');
    expect(req.request.params.get('page')).toBe('1');
    expect(req.request.params.get('size')).toBe('10');
    expect(req.request.params.get('sort')).toBe('price,desc');
    req.flush({ content: [buildProduct()], number: 1, size: 10, totalElements: 11, totalPages: 2 });

    expect(result?.content.length).toBe(1);
    expect(result?.page).toBe(1);
    expect(result?.totalElements).toBe(11);
  });

  it('creates, updates, fetches and deletes by id', () => {
    api.create(request).subscribe();
    const create = httpMock.expectOne('/api/v1/products');
    expect(create.request.method).toBe('POST');
    expect(create.request.body).toEqual(request);
    create.flush(buildProduct());

    api.update(5, request).subscribe();
    const update = httpMock.expectOne('/api/v1/products/5');
    expect(update.request.method).toBe('PUT');
    update.flush(buildProduct());

    api.get(5).subscribe();
    expect(httpMock.expectOne('/api/v1/products/5').request.method).toBe('GET');

    api.delete(5).subscribe();
    const del = httpMock.expectOne('/api/v1/products/5');
    expect(del.request.method).toBe('DELETE');
    del.flush(null, { status: 204, statusText: 'No Content' });
  });
});
