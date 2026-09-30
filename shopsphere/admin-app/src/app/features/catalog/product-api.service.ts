import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { PageResult, SpringPage, toPageResult } from '../../core/api/page';
import { API_BASE_URL } from '../../core/config/api.config';
import { Product, ProductRequest } from './catalog.models';
import { ProductQuery, toProductHttpParams } from './product-query';

/** product-service via the gateway: /api/v1/products (writes are ADMIN-only). */
@Injectable({ providedIn: 'root' })
export class ProductApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${inject(API_BASE_URL)}/products`;

  search(query: ProductQuery): Observable<PageResult<Product>> {
    return this.http
      .get<SpringPage<Product>>(this.baseUrl, { params: toProductHttpParams(query) })
      .pipe(map((page) => toPageResult(page)));
  }

  get(id: number): Observable<Product> {
    return this.http.get<Product>(`${this.baseUrl}/${id}`);
  }

  create(request: ProductRequest): Observable<Product> {
    return this.http.post<Product>(this.baseUrl, request);
  }

  update(id: number, request: ProductRequest): Observable<Product> {
    return this.http.put<Product>(`${this.baseUrl}/${id}`, request);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }
}
