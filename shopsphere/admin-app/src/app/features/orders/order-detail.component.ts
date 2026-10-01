import { CdkCopyToClipboard } from '@angular/cdk/clipboard';
import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, Input, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { RouterLink } from '@angular/router';
import {
  BehaviorSubject,
  EMPTY,
  Observable,
  ReplaySubject,
  Subscription,
  catchError,
  combineLatest,
  concatMap,
  filter,
  finalize,
  forkJoin,
  map,
  of,
  switchMap,
  take,
  takeWhile,
  tap,
  timer,
} from 'rxjs';

import { apiErrorMessage } from '../../core/api/api-error';
import { ConfirmService } from '../../core/ui/confirm-dialog.component';
import { Notifier } from '../../core/ui/notifier.service';
import { OrderDetail, OrderStatus, ShipRequest, Shipment } from './order.models';
import { OrderApiService } from './order-api.service';
import { toPositiveInt } from './order-query';
import {
  PAYMENT_STATE_LABELS,
  SHIPMENT_STATUS_LABELS,
  canCancel,
  canDeliver,
  canShip,
  orderStatusChip,
  orderStatusLabel,
  paymentStateChip,
  paymentStateOf,
  shipmentStatusChip,
} from './order-status';
import { ShipDialogComponent, ShipDialogData } from './ship-dialog.component';
import { ShipmentApiService } from './shipment-api.service';

/** order-service applies shipment events asynchronously; poll this long for the order to catch up. */
export const ORDER_SYNC_INTERVAL_MS = 1000;
export const ORDER_SYNC_ATTEMPTS = 10;

type LoadResult =
  | { kind: 'ok'; order: OrderDetail; shipment: Shipment | null; shipmentAvailable: boolean }
  | { kind: 'error'; message: string };

@Component({
  selector: 'app-order-detail',
  standalone: true,
  imports: [
    CdkCopyToClipboard,
    DatePipe,
    DecimalPipe,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatIconModule,
    MatProgressBarModule,
    MatTooltipModule,
  ],
  templateUrl: './order-detail.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OrderDetailComponent {
  private readonly orderApi = inject(OrderApiService);
  private readonly shipmentApi = inject(ShipmentApiService);
  private readonly dialog = inject(MatDialog);
  private readonly confirm = inject(ConfirmService);
  private readonly notifier = inject(Notifier);
  private readonly destroyRef = inject(DestroyRef);

  private readonly orderId$ = new ReplaySubject<number | null>(1);
  private readonly reload$ = new BehaviorSubject<void>(undefined);
  private syncSubscription: Subscription | null = null;

  protected readonly statusLabel = orderStatusLabel;
  protected readonly statusChip = orderStatusChip;
  protected readonly paymentChip = paymentStateChip;
  protected readonly paymentLabels = PAYMENT_STATE_LABELS;
  protected readonly shipmentChip = shipmentStatusChip;
  protected readonly shipmentLabels = SHIPMENT_STATUS_LABELS;

  readonly order = signal<OrderDetail | null>(null);
  readonly shipment = signal<Shipment | null>(null);
  readonly shipmentAvailable = signal(true);
  readonly loading = signal(false);
  readonly loadError = signal<string | null>(null);
  readonly busy = signal(false);
  readonly syncing = signal(false);
  readonly syncNote = signal<string | null>(null);

  readonly payment = computed(() => {
    const order = this.order();
    return order ? paymentStateOf(order) : 'PENDING';
  });
  readonly refundNeeded = computed(() => this.order()?.status === 'CANCELLED' && this.payment() === 'PAID');
  readonly canShip = computed(() => canShip(this.order(), this.shipment()));
  readonly canDeliver = computed(() => canDeliver(this.order(), this.shipment()));
  readonly canCancel = computed(() => canCancel(this.order(), this.shipment()));
  /** Tracking comes from shipping-service first (it's the source), then the order's copy. */
  readonly trackingNumber = computed(() => this.shipment()?.trackingNumber ?? this.order()?.trackingNumber ?? null);
  readonly carrier = computed(() => this.shipment()?.carrier ?? this.order()?.carrier ?? null);
  readonly awaitingShipment = computed(() => this.order()?.status === 'PAID' && this.shipmentAvailable() && !this.shipment());

  /** Bound from the :id route param (withComponentInputBinding). */
  @Input()
  set id(value: string | number | undefined) {
    this.orderId$.next(toPositiveInt(value));
  }

  constructor() {
    combineLatest([this.orderId$, this.reload$])
      .pipe(
        tap(() => {
          this.loading.set(true);
          this.loadError.set(null);
        }),
        switchMap(([id]) => this.load(id)),
        takeUntilDestroyed(),
      )
      .subscribe((result) => {
        this.loading.set(false);
        if (result.kind === 'error') {
          this.loadError.set(result.message);
          return;
        }
        this.order.set(result.order);
        this.shipment.set(result.shipment);
        this.shipmentAvailable.set(result.shipmentAvailable);
      });

    this.destroyRef.onDestroy(() => this.syncSubscription?.unsubscribe());
  }

  reload(): void {
    this.syncNote.set(null);
    this.reload$.next();
  }

  /** PENDING shipment → SHIPPED (carrier + tracking), then wait for the order to follow. */
  ship(): void {
    const order = this.order();
    const shipment = this.shipment();
    if (!order || !shipment || !this.canShip() || this.busy()) {
      return;
    }
    this.dialog
      .open<ShipDialogComponent, ShipDialogData, ShipRequest>(ShipDialogComponent, {
        data: { orderId: order.id },
        width: '480px',
        autoFocus: 'first-tabbable',
      })
      .afterClosed()
      .pipe(
        filter((request): request is ShipRequest => !!request),
        switchMap((request) => this.withBusy(this.shipmentApi.ship(shipment.id, request))),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (updated) => {
          this.shipment.set(updated);
          this.notifier.success(`Order #${order.id} shipped — tracking ${updated.trackingNumber ?? 'pending'}.`);
          this.syncOrder('SHIPPED');
        },
        error: (err: unknown) => this.notifier.error(apiErrorMessage(err, 'Could not mark the order as shipped.')),
      });
  }

  /** SHIPPED shipment → DELIVERED, then wait for the order to follow. */
  deliver(): void {
    const order = this.order();
    const shipment = this.shipment();
    if (!order || !shipment || !this.canDeliver() || this.busy()) {
      return;
    }
    this.confirm
      .confirm({
        title: 'Mark as delivered?',
        message: `Confirm that order #${order.id} (tracking ${shipment.trackingNumber ?? 'n/a'}) reached the customer. This can't be undone.`,
        confirmText: 'Mark as delivered',
      })
      .pipe(
        filter(Boolean),
        switchMap(() => this.withBusy(this.shipmentApi.deliver(shipment.id))),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (updated) => {
          this.shipment.set(updated);
          this.notifier.success(`Order #${order.id} marked as delivered.`);
          this.syncOrder('DELIVERED');
        },
        error: (err: unknown) => this.notifier.error(apiErrorMessage(err, 'Could not mark the order as delivered.')),
      });
  }

  /** Cancels the order, then the still-pending shipment so it can never be dispatched. */
  cancelOrder(): void {
    const order = this.order();
    if (!order || !this.canCancel() || this.busy()) {
      return;
    }
    const paid = this.payment() === 'PAID';
    this.confirm
      .confirm({
        title: `Cancel order #${order.id}?`,
        message: paid
          ? 'The customer is notified. This order is already paid — refunds are not automated yet, so issue the refund manually.'
          : 'The customer is notified and the reserved stock is released.',
        confirmText: 'Cancel order',
        cancelText: 'Keep order',
        destructive: true,
      })
      .pipe(
        filter(Boolean),
        switchMap(() => this.withBusy(this.orderApi.cancel(order.id))),
        tap((updated) => this.order.set(updated)),
        switchMap(() => this.cancelPendingShipment()),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (shipmentCancelled) => {
          this.notifier.success(
            shipmentCancelled === false
              ? `Order #${order.id} cancelled, but its shipment could not be cancelled — cancel it before it ships.`
              : `Order #${order.id} cancelled.`,
          );
        },
        error: (err: unknown) => this.notifier.error(apiErrorMessage(err, 'Could not cancel the order.')),
      });
  }

  /** true = cancelled, false = attempt failed, null = nothing to cancel. */
  private cancelPendingShipment(): Observable<boolean | null> {
    const shipment = this.shipment();
    if (shipment?.status !== 'PENDING') {
      return of(null);
    }
    return this.shipmentApi.cancel(shipment.id).pipe(
      tap((updated) => this.shipment.set(updated)),
      map(() => true),
      catchError(() => of(false)),
    );
  }

  /**
   * shipment.dispatched / shipment.delivered reach order-service via Kafka, so the order's own
   * status lags the shipment by a moment. Poll until it matches (or give up with a note).
   */
  private syncOrder(expected: OrderStatus): void {
    const id = this.order()?.id;
    if (!id) {
      return;
    }
    this.syncSubscription?.unsubscribe();
    this.syncing.set(true);
    this.syncNote.set(null);

    let latest: OrderDetail | null = this.order();
    this.syncSubscription = timer(ORDER_SYNC_INTERVAL_MS, ORDER_SYNC_INTERVAL_MS)
      .pipe(
        take(ORDER_SYNC_ATTEMPTS),
        concatMap(() => this.orderApi.get(id).pipe(catchError(() => EMPTY))),
        tap((order) => {
          latest = order;
          this.order.set(order);
        }),
        takeWhile((order) => order.status !== expected),
        finalize(() => {
          this.syncing.set(false);
          if (latest?.status !== expected) {
            this.syncNote.set(
              `The shipment is updated, but the order hasn't switched to "${orderStatusLabel(expected)}" yet. ` +
                'Order-service applies shipment events asynchronously — refresh in a moment.',
            );
          }
        }),
      )
      .subscribe();
  }

  private withBusy<T>(source: Observable<T>): Observable<T> {
    this.busy.set(true);
    return source.pipe(finalize(() => this.busy.set(false)));
  }

  private load(id: number | null): Observable<LoadResult> {
    if (id === null) {
      return of({ kind: 'error', message: 'Order not found.' });
    }
    return forkJoin({
      order: this.orderApi.get(id),
      shipment: this.shipmentApi.getByOrder(id).pipe(
        map((shipment) => ({ shipment, available: true })),
        catchError(() => of({ shipment: null, available: false })),
      ),
    }).pipe(
      map(
        ({ order, shipment }): LoadResult => ({
          kind: 'ok',
          order,
          shipment: shipment.shipment,
          shipmentAvailable: shipment.available,
        }),
      ),
      catchError((err: unknown) =>
        of<LoadResult>({
          kind: 'error',
          message:
            err instanceof HttpErrorResponse && err.status === 404
              ? `Order #${id} was not found.`
              : apiErrorMessage(err, 'Could not load the order.'),
        }),
      ),
    );
  }
}
