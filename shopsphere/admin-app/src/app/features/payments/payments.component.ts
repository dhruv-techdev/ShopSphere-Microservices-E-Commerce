import { DatePipe, DecimalPipe, PercentPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { MatTabsModule } from '@angular/material/tabs';
import { RouterLink } from '@angular/router';
import { BehaviorSubject, catchError, debounceTime, filter, forkJoin, map, of, switchMap, tap } from 'rxjs';

import { apiErrorMessage } from '../../core/api/api-error';
import { PageResult, emptyPage } from '../../core/api/page';
import { toPositiveInt } from '../orders/order-query';
import {
  PAYMENT_STATUSES,
  Payment,
  PaymentListQuery,
  PaymentStatus,
  PaymentSummary,
  RECONCILIATION_LABELS,
  ReconciliationItem,
  ReconciliationKind,
} from './payment.models';
import { PaymentApiService } from './payment-api.service';
import { PERIOD_LABELS, Period, mergeReconciliation, periodStart } from './reconciliation';

export const OVERDUE_AFTER_MINUTES = 30;

const STATUS_CHIPS: Record<PaymentStatus, string> = {
  PENDING: 'chip chip--neutral',
  SUCCESSFUL: 'chip chip--ok',
  FAILED: 'chip chip--warn',
  REFUNDED: 'chip chip--muted',
};

@Component({
  selector: 'app-payments',
  standalone: true,
  imports: [
    DatePipe,
    DecimalPipe,
    PercentPipe,
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatButtonToggleModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatPaginatorModule,
    MatProgressBarModule,
    MatSelectModule,
    MatTableModule,
    MatTabsModule,
  ],
  templateUrl: './payments.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PaymentsComponent {
  private readonly api = inject(PaymentApiService);
  private readonly fb = inject(FormBuilder);

  protected readonly periods = Object.keys(PERIOD_LABELS) as Period[];
  protected readonly periodLabels = PERIOD_LABELS;
  protected readonly statuses = PAYMENT_STATUSES;
  protected readonly labels = RECONCILIATION_LABELS;
  protected readonly overdueMinutes = OVERDUE_AFTER_MINUTES;

  readonly issueColumns = ['kind', 'orderId', 'userId', 'amount', 'references', 'since'];
  readonly paymentColumns = ['createdAt', 'paymentReference', 'orderId', 'userId', 'amount', 'status', 'message'];

  /* ---------- summary ---------- */
  private readonly period$ = new BehaviorSubject<Period>('7d');
  readonly period = signal<Period>('7d');
  readonly summary = signal<PaymentSummary | null>(null);
  readonly summaryError = signal<string | null>(null);

  readonly captured = computed(() => this.total('SUCCESSFUL'));
  readonly failed = computed(() => this.total('FAILED'));
  readonly pending = computed(() => this.total('PENDING'));

  /* ---------- reconciliation ---------- */
  private readonly issuesReload$ = new BehaviorSubject<void>(undefined);
  readonly issues = signal<ReconciliationItem[]>([]);
  readonly issuesLoading = signal(false);
  readonly issuesError = signal<string | null>(null);

  /* ---------- payment list ---------- */
  private readonly query$ = new BehaviorSubject<PaymentListQuery>({ status: null, orderId: null, page: 0, size: 20 });
  readonly payments = signal<PageResult<Payment>>(emptyPage(20));
  readonly paymentsLoading = signal(false);
  readonly paymentsError = signal<string | null>(null);

  readonly filters = this.fb.group({
    status: this.fb.control<PaymentStatus | null>(null),
    orderId: this.fb.control<number | null>(null, [Validators.min(1)]),
  });

  constructor() {
    this.period$
      .pipe(
        tap(() => this.summaryError.set(null)),
        switchMap((period) =>
          this.api.summary(periodStart(period)).pipe(
            catchError((err: unknown) => {
              this.summaryError.set(apiErrorMessage(err, 'Could not load the payment summary.'));
              return of(null);
            }),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((summary) => this.summary.set(summary));

    this.issuesReload$
      .pipe(
        tap(() => {
          this.issuesLoading.set(true);
          this.issuesError.set(null);
        }),
        switchMap(() => {
          const failures: string[] = [];
          return forkJoin({
            issues: this.api.orderIssues(OVERDUE_AFTER_MINUTES).pipe(
              catchError(() => {
                failures.push('order-service');
                return of([]);
              }),
            ),
            duplicates: this.api.duplicates().pipe(
              catchError(() => {
                failures.push('payment-service');
                return of([]);
              }),
            ),
          }).pipe(map((result) => ({ ...result, failures })));
        }),
        takeUntilDestroyed(),
      )
      .subscribe(({ issues, duplicates, failures }) => {
        this.issuesLoading.set(false);
        this.issues.set(mergeReconciliation(issues, duplicates));
        if (failures.length > 0) {
          this.issuesError.set(`Some checks could not run (${failures.join(', ')} unavailable). The list may be incomplete.`);
        }
      });

    this.query$
      .pipe(
        tap(() => {
          this.paymentsLoading.set(true);
          this.paymentsError.set(null);
        }),
        switchMap((query) =>
          this.api.list(query).pipe(
            catchError((err: unknown) => {
              this.paymentsError.set(apiErrorMessage(err, 'Could not load payments.'));
              return of(null);
            }),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((page) => {
        this.paymentsLoading.set(false);
        if (page) {
          this.payments.set(page);
        }
      });

    this.filters.valueChanges
      .pipe(
        debounceTime(300),
        filter(() => this.filters.valid),
        takeUntilDestroyed(),
      )
      .subscribe(() => {
        const v = this.filters.getRawValue();
        this.query$.next({ ...this.query$.value, status: v.status, orderId: toPositiveInt(v.orderId), page: 0 });
      });
  }

  setPeriod(period: Period): void {
    this.period.set(period);
    this.period$.next(period);
  }

  refresh(): void {
    this.period$.next(this.period());
    this.issuesReload$.next();
    this.query$.next({ ...this.query$.value });
  }

  onPage(event: PageEvent): void {
    this.query$.next({ ...this.query$.value, page: event.pageIndex, size: event.pageSize });
  }

  issueLabel(kind: ReconciliationKind): { title: string; action: string } {
    return this.labels[kind];
  }

  statusChip(status: PaymentStatus): string {
    return STATUS_CHIPS[status] ?? 'chip';
  }

  private total(status: PaymentStatus): { count: number; amount: number } {
    const row = this.summary()?.byStatus.find((s) => s.status === status);
    return { count: row?.count ?? 0, amount: row?.amount ?? 0 };
  }
}
