import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { PageResult, SpringPage, toPageResult } from '../../core/api/page';
import { API_BASE_URL } from '../../core/config/api.config';
import { AdminOrderSummary, OrderDetail } from './order.models';
import { OrderQuery, toOrderHttpParams } from './order-query';

/** order-service admin API via the gateway: /api/v1/admin/orders (ADMIN only). */
@Injectable({ providedIn: 'root' })
export class OrderApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${inject(API_BASE_URL)}/admin/orders`;

  list(query: OrderQuery): Observable<PageResult<AdminOrderSummary>> {
    return this.http
      .get<SpringPage<AdminOrderSummary>>(this.baseUrl, { params: toOrderHttpParams(query) })
      .pipe(map((page) => toPageResult(page)));
  }

  get(id: number): Observable<OrderDetail> {
    return this.http.get<OrderDetail>(`${this.baseUrl}/${id}`);
  }

  cancel(id: number): Observable<OrderDetail> {
    return this.http.post<OrderDetail>(`${this.baseUrl}/${id}/cancel`, {});
  }
}
