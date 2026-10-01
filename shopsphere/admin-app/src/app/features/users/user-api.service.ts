import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { PageResult, SpringPage, toPageResult } from '../../core/api/page';
import { API_BASE_URL } from '../../core/config/api.config';
import { AdminUser, UserQuery } from './user.models';

/** user-service admin API via the gateway: /api/v1/admin/users (ADMIN only). */
@Injectable({ providedIn: 'root' })
export class UserApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${inject(API_BASE_URL)}/admin/users`;

  list(query: UserQuery): Observable<PageResult<AdminUser>> {
    let params = new HttpParams()
      .set('page', String(query.page))
      .set('size', String(query.size))
      .set('sort', 'createdAt,desc');
    if (query.q) {
      params = params.set('q', query.q);
    }
    if (query.role) {
      params = params.set('role', query.role);
    }
    if (query.status === 'ACTIVE') {
      params = params.set('enabled', 'true').set('emailVerified', 'true');
    } else if (query.status === 'DISABLED') {
      params = params.set('enabled', 'false');
    } else if (query.status === 'UNVERIFIED') {
      params = params.set('emailVerified', 'false');
    }
    return this.http.get<SpringPage<AdminUser>>(this.baseUrl, { params }).pipe(map((page) => toPageResult(page)));
  }
}
