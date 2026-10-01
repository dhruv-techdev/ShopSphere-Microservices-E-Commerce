import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { PageResult, SpringPage, toPageResult } from '../../core/api/page';
import { API_BASE_URL } from '../../core/config/api.config';
import { AdminNotification, NotificationQuery, NotificationSummary } from './notification.models';

/** notification-service admin API via the gateway: /api/v1/admin/notifications (ADMIN only). */
@Injectable({ providedIn: 'root' })
export class NotificationApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${inject(API_BASE_URL)}/admin/notifications`;

  list(query: NotificationQuery): Observable<PageResult<AdminNotification>> {
    let params = new HttpParams()
      .set('page', String(query.page))
      .set('size', String(query.size))
      .set('sort', 'createdAt,desc');
    if (query.status) {
      params = params.set('status', query.status);
    }
    if (query.type) {
      params = params.set('type', query.type);
    }
    if (query.userId !== null) {
      params = params.set('userId', String(query.userId));
    }
    if (query.orderId !== null) {
      params = params.set('orderId', String(query.orderId));
    }
    if (query.recipient) {
      params = params.set('recipient', query.recipient);
    }
    return this.http
      .get<SpringPage<AdminNotification>>(this.baseUrl, { params })
      .pipe(map((page) => toPageResult(page)));
  }

  get(id: number): Observable<AdminNotification> {
    return this.http.get<AdminNotification>(`${this.baseUrl}/${id}`);
  }

  summary(): Observable<NotificationSummary> {
    return this.http.get<NotificationSummary>(`${this.baseUrl}/summary`);
  }

  /** Puts a FAILED notification back in the retry queue, due now. */
  retry(id: number): Observable<AdminNotification> {
    return this.http.post<AdminNotification>(`${this.baseUrl}/${id}/retry`, {});
  }
}
