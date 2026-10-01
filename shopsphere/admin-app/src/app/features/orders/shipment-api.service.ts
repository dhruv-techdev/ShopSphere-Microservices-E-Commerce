import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, of, throwError } from 'rxjs';

import { API_BASE_URL } from '../../core/config/api.config';
import { ShipRequest, Shipment } from './order.models';

/** shipping-service via the gateway: /api/v1/shipments (transitions are ADMIN only). */
@Injectable({ providedIn: 'root' })
export class ShipmentApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${inject(API_BASE_URL)}/shipments`;

  /** The order's shipment, or null when shipping-service hasn't created one (404). */
  getByOrder(orderId: number): Observable<Shipment | null> {
    return this.http.get<Shipment>(`${this.baseUrl}/order/${orderId}`).pipe(
      catchError((err: unknown) =>
        err instanceof HttpErrorResponse && err.status === 404 ? of(null) : throwError(() => err),
      ),
    );
  }

  /** PENDING → SHIPPED; publishes shipment.dispatched, which moves the order to SHIPPED. */
  ship(shipmentId: number, request: ShipRequest): Observable<Shipment> {
    return this.http.post<Shipment>(`${this.baseUrl}/${shipmentId}/ship`, request);
  }

  /** SHIPPED → DELIVERED; publishes shipment.delivered, which moves the order to DELIVERED. */
  deliver(shipmentId: number): Observable<Shipment> {
    return this.http.post<Shipment>(`${this.baseUrl}/${shipmentId}/deliver`, {});
  }

  /** PENDING → CANCELLED (used when an admin cancels the order before dispatch). */
  cancel(shipmentId: number): Observable<Shipment> {
    return this.http.post<Shipment>(`${this.baseUrl}/${shipmentId}/cancel`, {});
  }
}
