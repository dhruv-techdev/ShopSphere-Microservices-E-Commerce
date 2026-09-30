import { DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ActivatedRoute, Params, Router, RouterLink, convertToParamMap } from '@angular/router';
import { BehaviorSubject, catchError, combineLatest, debounceTime, filter, map, of, switchMap, tap } from 'rxjs';

import { apiErrorMessage } from '../../../core/api/api-error';
import { PageResult, emptyPage } from '../../../core/api/page';
import { ConfirmService } from '../../../core/ui/confirm-dialog.component';
import { Notifier } from '../../../core/ui/notifier.service';
import { Category, Product } from '../catalog.models';
import { CategoryApiService } from '../category-api.service';
import { ProductApiService } from '../product-api.service';
import {
  DEFAULT_PAGE_SIZE,
  DEFAULT_PRODUCT_SORT,
  PAGE_SIZE_OPTIONS,
  PRODUCT_SORT_FIELDS,
  ProductQuery,
  filterUrlParams,
  formatSort,
  hasActiveFilters,
  parseProductQuery,
  sameFilters,
} from '../product-query';

type TriState = '' | 'true' | 'false';

function priceRange(group: AbstractControl): ValidationErrors | null {
  const min = group.get('minPrice')?.value as number | null;
  const max = group.get('maxPrice')?.value as number | null;
  return min !== null && max !== null && min > max ? { priceRange: true } : null;
}

@Component({
  selector: 'app-product-list',
  standalone: true,
  imports: [
    DatePipe,
    DecimalPipe,
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatPaginatorModule,
    MatProgressBarModule,
    MatSelectModule,
    MatSortModule,
    MatTableModule,
    MatTooltipModule,
  ],
  templateUrl: './product-list.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProductListComponent {
  private readonly fb = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly productApi = inject(ProductApiService);
  private readonly categoryApi = inject(CategoryApiService);
  private readonly confirm = inject(ConfirmService);
  private readonly notifier = inject(Notifier);

  private readonly reload$ = new BehaviorSubject<void>(undefined);

  readonly columns = ['name', 'category', 'price', 'stockQuantity', 'active', 'updatedAt', 'actions'];
  readonly pageSizeOptions = PAGE_SIZE_OPTIONS;

  readonly query = signal<ProductQuery>(parseProductQuery(convertToParamMap({})));
  readonly page = signal<PageResult<Product>>(emptyPage(DEFAULT_PAGE_SIZE));
  readonly categories = signal<Category[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly hasFilters = computed(() => hasActiveFilters(this.query()));

  readonly filters = this.fb.group(
    {
      q: this.fb.nonNullable.control(''),
      categoryId: this.fb.control<number | null>(null),
      active: this.fb.nonNullable.control<TriState>(''),
      inStock: this.fb.nonNullable.control<TriState>(''),
      minPrice: this.fb.control<number | null>(null, Validators.min(0)),
      maxPrice: this.fb.control<number | null>(null, Validators.min(0)),
    },
    { validators: priceRange },
  );

  constructor() {
    this.categoryApi
      .list()
      .pipe(
        catchError(() => of([] as Category[])),
        takeUntilDestroyed(),
      )
      .subscribe((categories) => this.categories.set(categories));

    combineLatest([this.route.queryParamMap, this.reload$])
      .pipe(
        map(([params]) => parseProductQuery(params)),
        tap((query) => {
          this.query.set(query);
          this.patchFilters(query);
          this.loading.set(true);
          this.error.set(null);
        }),
        switchMap((query) =>
          this.productApi.search(query).pipe(
            catchError((err: unknown) => {
              this.error.set(apiErrorMessage(err, 'Could not load products. Please try again.'));
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
        debounceTime(300),
        filter(() => this.filters.valid),
        map(() => this.formFilterParams()),
        filter((params) => !sameFilters(params, filterUrlParams(this.query()))),
        takeUntilDestroyed(),
      )
      .subscribe((params) => this.navigate({ ...params, page: null }));
  }

  onPage(event: PageEvent): void {
    const sizeChanged = event.pageSize !== this.query().size;
    this.navigate({
      page: sizeChanged || event.pageIndex === 0 ? null : event.pageIndex,
      size: event.pageSize === DEFAULT_PAGE_SIZE ? null : event.pageSize,
    });
  }

  onSortChange(sort: Sort): void {
    const field = (PRODUCT_SORT_FIELDS as readonly string[]).includes(sort.active) ? sort.active : null;
    const direction = sort.direction || null;
    const value = field && direction ? `${field},${direction}` : null;
    this.navigate({ sort: value === formatSort(DEFAULT_PRODUCT_SORT) ? null : value, page: null });
  }

  clearFilters(): void {
    this.filters.reset({ q: '', categoryId: null, active: '', inStock: '', minPrice: null, maxPrice: null }, { emitEvent: false });
    this.navigate({ q: null, categoryId: null, active: null, inStock: null, minPrice: null, maxPrice: null, page: null });
  }

  reload(): void {
    this.reload$.next();
  }

  deleteProduct(product: Product): void {
    this.confirm
      .confirm({
        title: 'Delete product?',
        message: `"${product.name}" will be permanently removed from the catalog. To hide it from customers but keep its history, deactivate it instead.`,
        confirmText: 'Delete',
        destructive: true,
      })
      .pipe(
        filter(Boolean),
        switchMap(() => this.productApi.delete(product.id)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => {
          this.notifier.success(`Deleted "${product.name}".`);
          this.afterDelete();
        },
        error: (err: unknown) => this.notifier.error(apiErrorMessage(err, 'Could not delete the product.')),
      });
  }

  /** Step back a page when the last row of a non-first page was removed. */
  private afterDelete(): void {
    const { page, content } = this.page();
    if (content.length === 1 && page > 0) {
      this.navigate({ page: page - 1 === 0 ? null : page - 1 });
    } else {
      this.reload();
    }
  }

  private formFilterParams(): Params {
    const v = this.filters.getRawValue();
    return filterUrlParams({
      q: v.q.trim() || null,
      categoryId: v.categoryId,
      active: v.active === '' ? null : v.active === 'true',
      inStock: v.inStock === '' ? null : v.inStock === 'true',
      minPrice: v.minPrice,
      maxPrice: v.maxPrice,
    });
  }

  private patchFilters(q: ProductQuery): void {
    this.filters.setValue(
      {
        q: q.q ?? '',
        categoryId: q.categoryId,
        active: q.active === null ? '' : q.active ? 'true' : 'false',
        inStock: q.inStock === null ? '' : q.inStock ? 'true' : 'false',
        minPrice: q.minPrice,
        maxPrice: q.maxPrice,
      },
      { emitEvent: false },
    );
  }

  private navigate(queryParams: Params): void {
    void this.router.navigate([], { relativeTo: this.route, queryParams, queryParamsHandling: 'merge' });
  }
}
