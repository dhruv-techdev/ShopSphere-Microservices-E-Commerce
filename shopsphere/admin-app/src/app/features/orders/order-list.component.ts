import { DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatTableModule } from '@angular/material/table';
import { ActivatedRoute, Params, Router, RouterLink, convertToParamMap } from '@angular/router';
import { BehaviorSubject, catchError, combineLatest, debounceTime, filter, map, of, switchMap, tap } from 'rxjs';

import { apiErrorMessage } from '../../core/api/api-error';
import { PageResult, emptyPage } from '../../core/api/page';
import { AdminOrderSummary, ORDER_STATUSES, OrderStatus } from './order.models';
import { OrderApiService } from './order-api.service';
import {
  DEFAULT_ORDER_PAGE_SIZE,
  DEFAULT_ORDER_SORT,
  ORDER_PAGE_SIZE_OPTIONS,
  ORDER_SORT_FIELDS,
  OrderQuery,
  formatOrderSort,
  orderFilterParams,
  parseOrderQuery,
  toPositiveInt,
} from './order-query';
import {
  PAYMENT_STATE_LABELS,
  orderStatusChip,
  orderStatusLabel,
  paymentStateChip,
  paymentStateOf,
} from './order-status';

const ALL = 'ALL';

@Component({
  selector: 'app-order-list',
  standalone: true,
  imports: [
    DatePipe,
    DecimalPipe,
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatChipsModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatPaginatorModule,
    MatProgressBarModule,
    MatSortModule,
    MatTableModule,
  ],
  templateUrl: './order-list.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OrderListComponent {
  private readonly fb = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly orderApi = inject(OrderApiService);
  private readonly reload$ = new BehaviorSubject<void>(undefined);

  protected readonly all = ALL;
  protected readonly statuses = ORDER_STATUSES;
  protected readonly statusLabel = orderStatusLabel;
  protected readonly statusChip = orderStatusChip;
  protected readonly paymentChip = paymentStateChip;
  protected readonly paymentLabels = PAYMENT_STATE_LABELS;
  protected readonly paymentOf = paymentStateOf;

  readonly columns = ['id', 'userId', 'status', 'payment', 'itemCount', 'totalAmount', 'trackingNumber', 'createdAt'];
  readonly pageSizeOptions = ORDER_PAGE_SIZE_OPTIONS;

  readonly query = signal<OrderQuery>(parseOrderQuery(convertToParamMap({})));
  readonly page = signal<PageResult<AdminOrderSummary>>(emptyPage(DEFAULT_ORDER_PAGE_SIZE));
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly hasFilters = computed(() => this.query().status !== null || this.query().userId !== null);

  readonly filters = this.fb.group({
    status: this.fb.nonNullable.control<string>(ALL),
    userId: this.fb.control<number | null>(null, [Validators.min(1)]),
  });

  readonly jump = this.fb.control<number | null>(null, [Validators.min(1)]);

  constructor() {
    combineLatest([this.route.queryParamMap, this.reload$])
      .pipe(
        map(([params]) => parseOrderQuery(params)),
        tap((query) => {
          this.query.set(query);
          this.filters.setValue({ status: query.status ?? ALL, userId: query.userId }, { emitEvent: false });
          this.loading.set(true);
          this.error.set(null);
        }),
        switchMap((query) =>
          this.orderApi.list(query).pipe(
            catchError((err: unknown) => {
              this.error.set(apiErrorMessage(err, 'Could not load orders. Please try again.'));
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

    this.filters.valueChanges
      .pipe(
        debounceTime(250),
        filter(() => this.filters.valid),
        map(() => this.formFilterParams()),
        filter((params) => !sameParams(params, orderFilterParams(this.query()))),
        takeUntilDestroyed(),
      )
      .subscribe((params) => this.navigate({ ...params, page: null }));
  }

  onPage(event: PageEvent): void {
    const sizeChanged = event.pageSize !== this.query().size;
    this.navigate({
      page: sizeChanged || event.pageIndex === 0 ? null : event.pageIndex,
      size: event.pageSize === DEFAULT_ORDER_PAGE_SIZE ? null : event.pageSize,
    });
  }

  onSortChange(sort: Sort): void {
    const field = (ORDER_SORT_FIELDS as readonly string[]).includes(sort.active) ? sort.active : null;
    const value = field && sort.direction ? `${field},${sort.direction}` : null;
    this.navigate({ sort: value === formatOrderSort(DEFAULT_ORDER_SORT) ? null : value, page: null });
  }

  clearFilters(): void {
    this.filters.reset({ status: ALL, userId: null }, { emitEvent: false });
    this.navigate({ status: null, userId: null, page: null });
  }

  /** "Go to order #" box. */
  openOrder(): void {
    const id = toPositiveInt(this.jump.value);
    if (id === null) {
      this.jump.markAsTouched();
      return;
    }
    void this.router.navigate(['/orders', id]);
  }

  reload(): void {
    this.reload$.next();
  }

  private formFilterParams(): Params {
    const v = this.filters.getRawValue();
    const status = v.status && v.status !== ALL ? (v.status as OrderStatus) : null;
    return orderFilterParams({ status, userId: toPositiveInt(v.userId) });
  }

  private navigate(queryParams: Params): void {
    void this.router.navigate([], { relativeTo: this.route, queryParams, queryParamsHandling: 'merge' });
  }
}

function sameParams(a: Params, b: Params): boolean {
  return ['status', 'userId'].every((key) => String(a[key] ?? '') === String(b[key] ?? ''));
}
