import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { PageResult, SpringPage, toPageResult } from '../../core/api/page';
import { API_BASE_URL } from '../../core/config/api.config';
import { DuplicateCharge, OrderPaymentIssue, Payment, PaymentListQuery, PaymentSummary } from './payment.models';

/** payment-service + order-service admin endpoints used by the reconciliation view (ADMIN only). */
@Injectable({ providedIn: 'root' })
export class PaymentApiService {
  private readonly http = inject(HttpClient);
  private readonly apiBase = inject(API_BASE_URL);
  private readonly paymentsUrl = `${this.apiBase}/admin/payments`;

  list(query: PaymentListQuery): Observable<PageResult<Payment>> {
    let params = new HttpParams()
      .set('page', String(query.page))
      .set('size', String(query.size))
      .set('sort', 'createdAt,desc');
    if (query.status) {
      params = params.set('status', query.status);
    }
    if (query.orderId !== null) {
      params = params.set('orderId', String(query.orderId));
    }
    return this.http.get<SpringPage<Payment>>(this.paymentsUrl, { params }).pipe(map((page) => toPageResult(page)));
  }

  /** @param from ISO instant, or null for all time. */
  summary(from: string | null): Observable<PaymentSummary> {
    const params = from ? new HttpParams().set('from', from) : new HttpParams();
    return this.http.get<PaymentSummary>(`${this.paymentsUrl}/summary`, { params });
  }

  duplicates(): Observable<DuplicateCharge[]> {
    return this.http.get<DuplicateCharge[]>(`${this.paymentsUrl}/duplicates`);
  }

  /** order-service view: orders whose status and payment disagree. */
  orderIssues(pendingOlderThanMinutes: number): Observable<OrderPaymentIssue[]> {
    const params = new HttpParams().set('pendingOlderThanMinutes', String(pendingOlderThanMinutes));
    return this.http.get<OrderPaymentIssue[]>(`${this.apiBase}/admin/orders/reconciliation`, { params });
  }
}
