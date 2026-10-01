import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed, discardPeriodicTasks, fakeAsync, flush, tick } from '@angular/core/testing';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';

import { ConfirmService } from '../../core/ui/confirm-dialog.component';
import { Notifier } from '../../core/ui/notifier.service';
import { OrderDetail, ShipRequest, Shipment } from './order.models';
import { OrderApiService } from './order-api.service';
import { ORDER_SYNC_ATTEMPTS, ORDER_SYNC_INTERVAL_MS, OrderDetailComponent } from './order-detail.component';
import { buildOrder, buildShipment } from './orders.testing';
import { ShipDialogComponent } from './ship-dialog.component';
import { ShipmentApiService } from './shipment-api.service';

describe('OrderDetailComponent', () => {
  let fixture: ComponentFixture<OrderDetailComponent>;
  let component: OrderDetailComponent;
  let orderApi: jasmine.SpyObj<OrderApiService>;
  let shipmentApi: jasmine.SpyObj<ShipmentApiService>;
  let dialog: jasmine.SpyObj<MatDialog>;
  let confirm: jasmine.SpyObj<ConfirmService>;
  let notifier: jasmine.SpyObj<Notifier>;

  function setup(
    order$: Observable<OrderDetail> = of(buildOrder()),
    shipment$: Observable<Shipment | null> = of(buildShipment()),
    id: string = '42',
  ): void {
    orderApi = jasmine.createSpyObj<OrderApiService>('OrderApiService', ['get', 'cancel']);
    orderApi.get.and.returnValue(order$);
    shipmentApi = jasmine.createSpyObj<ShipmentApiService>('ShipmentApiService', ['getByOrder', 'ship', 'deliver', 'cancel']);
    shipmentApi.getByOrder.and.returnValue(shipment$);
    dialog = jasmine.createSpyObj<MatDialog>('MatDialog', ['open']);
    confirm = jasmine.createSpyObj<ConfirmService>('ConfirmService', ['confirm']);
    notifier = jasmine.createSpyObj<Notifier>('Notifier', ['success', 'error']);

    TestBed.configureTestingModule({
      imports: [OrderDetailComponent],
      providers: [
        provideNoopAnimations(),
        provideRouter([]),
        { provide: OrderApiService, useValue: orderApi },
        { provide: ShipmentApiService, useValue: shipmentApi },
        { provide: MatDialog, useValue: dialog },
        { provide: ConfirmService, useValue: confirm },
        { provide: Notifier, useValue: notifier },
      ],
    });
    fixture = TestBed.createComponent(OrderDetailComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('id', id);
    fixture.detectChanges();
  }

  function text(testId: string): string | undefined {
    const el: HTMLElement | null = fixture.nativeElement.querySelector(`[data-testid="${testId}"]`);
    return el?.textContent?.trim();
  }

  function has(testId: string): boolean {
    return fixture.nativeElement.querySelector(`[data-testid="${testId}"]`) !== null;
  }

  function shipDialogReturns(request: ShipRequest | undefined): void {
    dialog.open.and.returnValue({ afterClosed: () => of(request) } as MatDialogRef<ShipDialogComponent, ShipRequest>);
  }

  describe('rendering', () => {
    it('shows items, totals, payment status and the pending shipment', () => {
      setup();

      expect(orderApi.get).toHaveBeenCalledOnceWith(42);
      expect(shipmentApi.getByOrder).toHaveBeenCalledOnceWith(42);
      expect(fixture.nativeElement.querySelectorAll('[data-testid="item-row"]').length).toBe(2);
      expect(text('order-total')).toBe('129.97');
      expect(text('order-status')).toBe('Paid');
      expect(text('payment-state')).toBe('Paid');
      expect(fixture.nativeElement.textContent).toContain('3f1c2a9e-5b7d-4c11-9a0e-6d2f8b7c1e44');
      expect(text('shipment-status')).toBe('Ready to ship');
      expect(has('ship')).toBeTrue();
      expect(has('deliver')).toBeFalse();
      expect(has('cancel-order')).toBeTrue();
    });

    it('shows the carrier tracking number and offers delivery once shipped', () => {
      setup(
        of(buildOrder({ status: 'SHIPPED', trackingNumber: 'SSX2609284K7QZ9M2PA', carrier: 'ShopSphere Express' })),
        of(buildShipment({ status: 'SHIPPED', trackingNumber: 'SSX2609284K7QZ9M2PA', carrier: 'ShopSphere Express' })),
      );

      expect(text('tracking-number')).toBe('SSX2609284K7QZ9M2PA');
      expect(fixture.nativeElement.textContent).toContain('ShopSphere Express');
      expect(has('deliver')).toBeTrue();
      expect(has('ship')).toBeFalse();
      expect(has('cancel-order')).toBeFalse();
    });

    it('falls back to the order copy of tracking when shipping-service is unavailable', () => {
      setup(
        of(buildOrder({ status: 'SHIPPED', trackingNumber: 'SSX26092800000000AA' })),
        throwError(() => new HttpErrorResponse({ status: 503 })),
      );

      expect(text('tracking-number')).toBe('SSX26092800000000AA');
      expect(fixture.nativeElement.textContent).toContain("shipping-service didn't respond");
      expect(has('deliver')).toBeFalse();
    });

    it('shows payment failures', () => {
      setup(
        of(
          buildOrder({
            status: 'PAYMENT_FAILED',
            paymentState: 'FAILED',
            paidAt: null,
            paymentFailedAt: '2026-09-28T14:01:00Z',
            paymentFailureReason: 'Card declined by issuer (simulated)',
          }),
        ),
        of(null),
      );

      expect(text('payment-state')).toBe('Failed');
      expect(fixture.nativeElement.textContent).toContain('Card declined by issuer (simulated)');
      expect(has('no-shipment')).toBeTrue();
      expect(has('cancel-order')).toBeFalse();
    });

    it('warns when a paid order was cancelled (manual refund)', () => {
      setup(of(buildOrder({ status: 'CANCELLED', cancellationReason: 'ADMIN_CANCELLED' })), of(null));

      expect(has('refund-needed')).toBeTrue();
    });

    it('explains a missing order', () => {
      setup(throwError(() => new HttpErrorResponse({ status: 404 })));

      expect(text('load-error')).toContain('Order #42 was not found.');
    });

    it('rejects a non-numeric id without calling the API', () => {
      setup(of(buildOrder()), of(null), 'abc');

      expect(orderApi.get).not.toHaveBeenCalled();
      expect(text('load-error')).toContain('Order not found.');
    });
  });

  describe('transitions', () => {
    it('ships through shipping-service and waits for the order to become SHIPPED', fakeAsync(() => {
      setup();
      shipDialogReturns({ carrier: 'UPS', trackingNumber: '1Z999AA10123456784' });
      shipmentApi.ship.and.returnValue(
        of(buildShipment({ status: 'SHIPPED', carrier: 'UPS', trackingNumber: '1Z999AA10123456784' })),
      );
      orderApi.get.and.returnValues(
        of(buildOrder({ status: 'PAID' })),
        of(buildOrder({ status: 'SHIPPED', trackingNumber: '1Z999AA10123456784', carrier: 'UPS' })),
      );

      component.ship();
      fixture.detectChanges();

      expect(dialog.open).toHaveBeenCalledWith(ShipDialogComponent, jasmine.objectContaining({ data: { orderId: 42 } }));
      expect(shipmentApi.ship).toHaveBeenCalledOnceWith(11, { carrier: 'UPS', trackingNumber: '1Z999AA10123456784' });
      expect(text('shipment-status')).toBe('In transit');
      expect(component.syncing()).toBeTrue();

      tick(ORDER_SYNC_INTERVAL_MS * 2);
      fixture.detectChanges();

      expect(component.order()?.status).toBe('SHIPPED');
      expect(component.syncing()).toBeFalse();
      expect(component.syncNote()).toBeNull();
      expect(text('tracking-number')).toBe('1Z999AA10123456784');
      expect(has('deliver')).toBeTrue();
      flush();
    }));

    it('does nothing when the ship dialog is cancelled', () => {
      setup();
      shipDialogReturns(undefined);

      component.ship();

      expect(shipmentApi.ship).not.toHaveBeenCalled();
    });

    it('reports a failed dispatch', () => {
      setup();
      shipDialogReturns({ carrier: null, trackingNumber: 'DUPLICATE-1' });
      shipmentApi.ship.and.returnValue(
        throwError(() => new HttpErrorResponse({ status: 409, error: { detail: 'Tracking number DUPLICATE-1 is already used by another shipment.' } })),
      );

      component.ship();

      expect(notifier.error).toHaveBeenCalledWith('Tracking number DUPLICATE-1 is already used by another shipment.');
      expect(component.busy()).toBeFalse();
    });

    it('marks delivered after confirmation, and notes when the order has not caught up', fakeAsync(() => {
      setup(
        of(buildOrder({ status: 'SHIPPED', trackingNumber: 'SSX1' })),
        of(buildShipment({ status: 'SHIPPED', trackingNumber: 'SSX1' })),
      );
      confirm.confirm.and.returnValue(of(true));
      shipmentApi.deliver.and.returnValue(of(buildShipment({ status: 'DELIVERED', trackingNumber: 'SSX1' })));

      component.deliver();
      tick(ORDER_SYNC_INTERVAL_MS * ORDER_SYNC_ATTEMPTS);
      fixture.detectChanges();

      expect(shipmentApi.deliver).toHaveBeenCalledOnceWith(11);
      expect(text('shipment-status')).toBe('Delivered');
      expect(orderApi.get).toHaveBeenCalledTimes(1 + ORDER_SYNC_ATTEMPTS);
      expect(component.syncing()).toBeFalse();
      expect(text('sync-note')).toContain('Delivered');
      discardPeriodicTasks();
      flush();
    }));

    it('cancels the order and its still-pending shipment', () => {
      setup();
      confirm.confirm.and.returnValue(of(true));
      orderApi.cancel.and.returnValue(of(buildOrder({ status: 'CANCELLED', cancellationReason: 'ADMIN_CANCELLED' })));
      shipmentApi.cancel.and.returnValue(of(buildShipment({ status: 'CANCELLED' })));

      component.cancelOrder();
      fixture.detectChanges();

      expect(confirm.confirm).toHaveBeenCalledWith(jasmine.objectContaining({ destructive: true }));
      expect(orderApi.cancel).toHaveBeenCalledOnceWith(42);
      expect(shipmentApi.cancel).toHaveBeenCalledOnceWith(11);
      expect(notifier.success).toHaveBeenCalledWith('Order #42 cancelled.');
      expect(text('order-status')).toBe('Cancelled');
      expect(has('refund-needed')).toBeTrue();
      expect(has('ship')).toBeFalse();
    });

    it('warns when the order was cancelled but the shipment could not be', () => {
      setup();
      confirm.confirm.and.returnValue(of(true));
      orderApi.cancel.and.returnValue(of(buildOrder({ status: 'CANCELLED' })));
      shipmentApi.cancel.and.returnValue(throwError(() => new HttpErrorResponse({ status: 503 })));

      component.cancelOrder();

      expect(notifier.success).toHaveBeenCalledWith(jasmine.stringContaining('could not be cancelled'));
    });

    it('surfaces a 409 when the order can no longer be cancelled', () => {
      setup();
      confirm.confirm.and.returnValue(of(true));
      orderApi.cancel.and.returnValue(
        throwError(() => new HttpErrorResponse({ status: 409, error: { message: 'Order 42 is SHIPPED and cannot be cancelled' } })),
      );

      component.cancelOrder();

      expect(notifier.error).toHaveBeenCalledWith('Order 42 is SHIPPED and cannot be cancelled');
      expect(shipmentApi.cancel).not.toHaveBeenCalled();
    });
  });
});
