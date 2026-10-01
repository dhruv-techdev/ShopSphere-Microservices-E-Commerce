import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../../core/config/api.config';
import { StockLevel } from './inventory.models';

/** inventory-service via the gateway: /api/v1/inventory (writes are ADMIN-only). */
@Injectable({ providedIn: 'root' })
export class InventoryApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${inject(API_BASE_URL)}/inventory`;

  /** Items whose sellable quantity is at or below the service's low-stock threshold. */
  lowStock(): Observable<StockLevel[]> {
    return this.http.get<StockLevel[]>(`${this.baseUrl}/low-stock`);
  }

  restock(productId: number, quantity: number): Observable<StockLevel> {
    return this.http.put<StockLevel>(`${this.baseUrl}/${productId}`, { operation: 'INCREMENT', quantity });
  }
}
