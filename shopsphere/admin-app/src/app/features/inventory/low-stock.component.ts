import { DatePipe } from '@angular/common';
import { AfterViewInit, ChangeDetectionStrategy, Component, DestroyRef, ViewChild, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSort, MatSortModule } from '@angular/material/sort';
import { MatTableDataSource, MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';
import { BehaviorSubject, Observable, catchError, filter, forkJoin, map, of, switchMap, tap } from 'rxjs';

import { apiErrorMessage } from '../../core/api/api-error';
import { Notifier } from '../../core/ui/notifier.service';
import { ProductApiService } from '../catalog/product-api.service';
import { LowStockRow, StockLevel } from './inventory.models';
import { InventoryApiService } from './inventory-api.service';
import { RestockDialogComponent } from './restock-dialog.component';

@Component({
  selector: 'app-low-stock',
  standalone: true,
  imports: [
    DatePipe,
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressBarModule,
    MatSlideToggleModule,
    MatSortModule,
    MatTableModule,
  ],
  templateUrl: './low-stock.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LowStockComponent implements AfterViewInit {
  private readonly inventoryApi = inject(InventoryApiService);
  private readonly productApi = inject(ProductApiService);
  private readonly dialog = inject(MatDialog);
  private readonly notifier = inject(Notifier);
  private readonly destroyRef = inject(DestroyRef);
  private readonly reload$ = new BehaviorSubject<void>(undefined);

  @ViewChild(MatSort) sort?: MatSort;

  readonly columns = ['product', 'availableQuantity', 'reservedQuantity', 'sellableQuantity', 'updatedAt', 'actions'];
  readonly dataSource = new MatTableDataSource<LowStockRow>([]);

  readonly rows = signal<LowStockRow[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  readonly search = new FormControl('', { nonNullable: true });
  readonly outOfStockOnly = new FormControl(false, { nonNullable: true });

  readonly outOfStockCount = computed(() => this.rows().filter((r) => r.sellableQuantity <= 0).length);
  readonly reservedUnits = computed(() => this.rows().reduce((sum, r) => sum + r.reservedQuantity, 0));

  constructor() {
    this.dataSource.filterPredicate = (row, raw) => {
      const { term, outOnly } = JSON.parse(raw) as { term: string; outOnly: boolean };
      if (outOnly && row.sellableQuantity > 0) {
        return false;
      }
      return !term || String(row.productId) === term || (row.productName ?? '').toLowerCase().includes(term);
    };
    this.dataSource.sortingDataAccessor = (row, column) =>
      column === 'product' ? (row.productName ?? '').toLowerCase() : ((row as unknown as Record<string, number>)[column] ?? 0);

    this.reload$
      .pipe(
        tap(() => {
          this.loading.set(true);
          this.error.set(null);
        }),
        switchMap(() =>
          this.inventoryApi.lowStock().pipe(
            switchMap((levels) => this.withProducts(levels)),
            catchError((err: unknown) => {
              this.error.set(apiErrorMessage(err, 'Could not load low-stock items.'));
              return of(null);
            }),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((rows) => {
        this.loading.set(false);
        if (rows) {
          const sorted = [...rows].sort((a, b) => a.sellableQuantity - b.sellableQuantity);
          this.rows.set(sorted);
          this.dataSource.data = sorted;
        }
      });

    this.search.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => this.applyFilter());
    this.outOfStockOnly.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => this.applyFilter());
  }

  ngAfterViewInit(): void {
    if (this.sort) {
      this.dataSource.sort = this.sort;
    }
  }

  reload(): void {
    this.reload$.next();
  }

  restock(row: LowStockRow): void {
    this.dialog
      .open<RestockDialogComponent, LowStockRow, number>(RestockDialogComponent, { data: row, width: '420px' })
      .afterClosed()
      .pipe(
        filter((qty): qty is number => typeof qty === 'number' && qty > 0),
        switchMap((qty) => this.inventoryApi.restock(row.productId, qty).pipe(map((level) => ({ qty, level })))),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: ({ qty, level }) => {
          this.notifier.success(
            `Added ${qty} units to ${row.productName ?? 'product #' + row.productId} — ${level.sellableQuantity} now sellable.`,
          );
          this.reload();
        },
        error: (err: unknown) => this.notifier.error(apiErrorMessage(err, 'Could not update stock.')),
      });
  }

  private applyFilter(): void {
    this.dataSource.filter = JSON.stringify({
      term: this.search.value.trim().toLowerCase(),
      outOnly: this.outOfStockOnly.value,
    });
  }

  /** Joins each stock row with its product (name, active flag). Missing products don't fail the page. */
  private withProducts(levels: StockLevel[]): Observable<LowStockRow[]> {
    if (levels.length === 0) {
      return of([]);
    }
    return forkJoin(
      levels.map((level) =>
        this.productApi.get(level.productId).pipe(
          map((p): LowStockRow => ({ ...level, productName: p.name, productActive: p.active })),
          catchError(() => of<LowStockRow>({ ...level, productName: null, productActive: null })),
        ),
      ),
    );
  }
}
