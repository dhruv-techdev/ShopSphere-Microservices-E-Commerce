import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { ActivatedRoute } from '@angular/router';
import { BehaviorSubject, catchError, debounceTime, filter, of, switchMap, tap } from 'rxjs';

import { apiErrorMessage } from '../../core/api/api-error';
import { PageResult, emptyPage } from '../../core/api/page';
import { Notifier } from '../../core/ui/notifier.service';
import { toPositiveInt } from '../orders/order-query';
import {
  AdminNotification,
  DELIVERY_STATUSES,
  DELIVERY_STATUS_CHIPS,
  DeliveryStatus,
  NOTIFICATION_TYPES,
  NotificationQuery,
  NotificationSummary,
  NotificationType,
  typeLabel,
} from './notification.models';
import { NotificationApiService } from './notification-api.service';
import { NotificationDetailDialogComponent } from './notification-detail-dialog.component';

const ALL = 'ALL';

@Component({
  selector: 'app-notification-log',
  standalone: true,
  imports: [
    DatePipe,
    ReactiveFormsModule,
    MatButtonModule,
    MatChipsModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatPaginatorModule,
    MatProgressBarModule,
    MatSelectModule,
    MatTableModule,
  ],
  templateUrl: './notification-log.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NotificationLogComponent {
  private readonly api = inject(NotificationApiService);
  private readonly dialog = inject(MatDialog);
  private readonly notifier = inject(Notifier);
  private readonly fb = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly all = ALL;
  protected readonly statuses = DELIVERY_STATUSES;
  protected readonly types = NOTIFICATION_TYPES;
  protected readonly chips = DELIVERY_STATUS_CHIPS;
  protected readonly label = typeLabel;

  readonly columns = ['createdAt', 'type', 'recipient', 'subject', 'status', 'attempts'];

  private readonly query$: BehaviorSubject<NotificationQuery>;
  private readonly summaryReload$ = new BehaviorSubject<void>(undefined);

  readonly page = signal<PageResult<AdminNotification>>(emptyPage(25));
  readonly summary = signal<NotificationSummary | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  readonly filters = this.fb.group({
    status: this.fb.nonNullable.control<string>(ALL),
    type: this.fb.control<NotificationType | null>(null),
    userId: this.fb.control<number | null>(null, [Validators.min(1)]),
    orderId: this.fb.control<number | null>(null, [Validators.min(1)]),
    recipient: this.fb.nonNullable.control(''),
  });

  constructor() {
    // Deep links from the user list (?userId=) and order pages (?orderId=).
    const params = this.route.snapshot?.queryParamMap;
    const userId = toPositiveInt(params?.get('userId'));
    const orderId = toPositiveInt(params?.get('orderId'));
    this.filters.patchValue({ userId, orderId }, { emitEvent: false });
    this.query$ = new BehaviorSubject<NotificationQuery>({
      status: null,
      type: null,
      userId,
      orderId,
      recipient: null,
      page: 0,
      size: 25,
    });

    this.query$
      .pipe(
        tap(() => {
          this.loading.set(true);
          this.error.set(null);
        }),
        switchMap((query) =>
          this.api.list(query).pipe(
            catchError((err: unknown) => {
              this.error.set(apiErrorMessage(err, 'Could not load notifications.'));
              return of(null);
            }),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((page) => {
        this.loading.set(false);
        if (page) {
          this.page.set(page);
        }
      });

    this.summaryReload$
      .pipe(
        switchMap(() => this.api.summary().pipe(catchError(() => of(null)))),
        takeUntilDestroyed(),
      )
      .subscribe((summary) => this.summary.set(summary));

    this.filters.valueChanges
      .pipe(
        debounceTime(300),
        filter(() => this.filters.valid),
        takeUntilDestroyed(),
      )
      .subscribe(() => {
        const v = this.filters.getRawValue();
        this.query$.next({
          ...this.query$.value,
          status: v.status === ALL ? null : (v.status as DeliveryStatus),
          type: v.type,
          userId: toPositiveInt(v.userId),
          orderId: toPositiveInt(v.orderId),
          recipient: v.recipient.trim() || null,
          page: 0,
        });
      });
  }

  chip(status: DeliveryStatus): string {
    return this.chips[status] ?? 'chip';
  }

  count(status: DeliveryStatus): number {
    return this.summary()?.byStatus[status] ?? 0;
  }

  onPage(event: PageEvent): void {
    this.query$.next({ ...this.query$.value, page: event.pageIndex, size: event.pageSize });
  }

  reload(): void {
    this.query$.next({ ...this.query$.value });
    this.summaryReload$.next();
  }

  open(notification: AdminNotification): void {
    this.dialog
      .open<NotificationDetailDialogComponent, AdminNotification, AdminNotification>(NotificationDetailDialogComponent, {
        data: notification,
        width: '640px',
        maxWidth: '95vw',
      })
      .afterClosed()
      .pipe(
        filter((updated): updated is AdminNotification => !!updated),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((updated) => {
        this.notifier.success(`Notification #${updated.id} queued for another attempt.`);
        this.reload();
      });
  }
}
