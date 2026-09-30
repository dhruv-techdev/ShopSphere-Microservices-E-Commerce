import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { Category } from './catalog.models';
import { buildCategory } from './catalog.testing';
import { CategoryApiService } from './category-api.service';

describe('CategoryApiService', () => {
  let api: CategoryApiService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(CategoryApiService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('lists categories', () => {
    let result: Category[] | undefined;
    api.list().subscribe((c) => (result = c));

    httpMock.expectOne('/api/v1/categories').flush([buildCategory()]);

    expect(result?.[0].name).toBe('Electronics');
  });

  it('creates, updates and deletes through the gateway routes', () => {
    api.create({ name: 'Toys', description: null }).subscribe();
    const create = httpMock.expectOne('/api/v1/categories');
    expect(create.request.method).toBe('POST');
    expect(create.request.body).toEqual({ name: 'Toys', description: null });
    create.flush(buildCategory({ id: 9, name: 'Toys' }));

    api.update(9, { name: 'Games', description: 'Board games' }).subscribe();
    const update = httpMock.expectOne('/api/v1/categories/9');
    expect(update.request.method).toBe('PUT');
    update.flush(buildCategory({ id: 9, name: 'Games' }));

    api.delete(9).subscribe();
    const del = httpMock.expectOne('/api/v1/categories/9');
    expect(del.request.method).toBe('DELETE');
    del.flush(null, { status: 204, statusText: 'No Content' });
  });
});
