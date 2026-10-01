import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';

import { Notifier } from '../../core/ui/notifier.service';
import { buildProduct } from '../catalog/catalog.testing';
import { ProductApiService } from '../catalog/product-api.service';
import { StockLevel } from './inventory.models';
import { InventoryApiService } from './inventory-api.service';
import { LowStockComponent } from './low-stock.component';
import { RestockDialogComponent } from './restock-dialog.component';

function level(productId: number, available: number, reserved = 0): StockLevel {
  return {
    productId,
    availableQuantity: available,
    reservedQuantity: reserved,
    sellableQuantity: available - reserved,
    inStock: available - reserved > 0,
    lowStock: true,
    updatedAt: '2026-09-30T10:00:00Z',
  };
}

describe('LowStockComponent', () => {
  let fixture: ComponentFixture<LowStockComponent>;
  let component: LowStockComponent;
  let inventoryApi: jasmine.SpyObj<InventoryApiService>;
  let productApi: jasmine.SpyObj<ProductApiService>;
  let dialog: jasmine.SpyObj<MatDialog>;
  let notifier: jasmine.SpyObj<Notifier>;

  function setup(levels$: Observable<StockLevel[]> = of([level(5, 8, 2), level(9, 3, 3), level(12, 1)])): void {
    inventoryApi = jasmine.createSpyObj<InventoryApiService>('InventoryApiService', ['lowStock', 'restock']);
    inventoryApi.lowStock.and.returnValue(levels$);
    productApi = jasmine.createSpyObj<ProductApiService>('ProductApiService', ['get']);
    productApi.get.and.callFake((id: number) =>
      id === 12 ? throwError(() => new HttpErrorResponse({ status: 404 })) : of(buildProduct({ id, name: `Product ${id}` })),
    );
    dialog = jasmine.createSpyObj<MatDialog>('MatDialog', ['open']);
    notifier = jasmine.createSpyObj<Notifier>('Notifier', ['success', 'error']);

    TestBed.configureTestingModule({
      imports: [LowStockComponent],
      providers: [
        provideNoopAnimations(),
        provideRouter([]),
        { provide: InventoryApiService, useValue: inventoryApi },
        { provide: ProductApiService, useValue: productApi },
        { provide: MatDialog, useValue: dialog },
        { provide: Notifier, useValue: notifier },
      ],
    });
    fixture = TestBed.createComponent(LowStockComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  function rowTexts(): string[] {
    return Array.from(fixture.nativeElement.querySelectorAll('[data-testid="stock-row"]')).map(
      (r) => (r as HTMLElement).textContent ?? '',
    );
  }

  it('joins stock with product names, most urgent first, and counts out-of-stock items', () => {
    setup();

    const rows = rowTexts();
    expect(rows.length).toBe(3);
    expect(rows[0]).toContain('Product 9');
    expect(rows[0]).toContain('Out of stock');
    expect(rows[1]).toContain('Unknown product');
    expect(rows[2]).toContain('Product 5');
    expect(component.outOfStockCount()).toBe(1);
    expect(component.reservedUnits()).toBe(5);
  });

  it('filters by name and by out-of-stock only', () => {
    setup();

    component.search.setValue('product 5');
    fixture.detectChanges();
    expect(rowTexts().length).toBe(1);

    component.search.setValue('');
    component.outOfStockOnly.setValue(true);
    fixture.detectChanges();
    expect(rowTexts().length).toBe(1);
    expect(rowTexts()[0]).toContain('Product 9');
  });

  it('shows an empty state when nothing is low', () => {
    setup(of([]));

    expect(fixture.nativeElement.textContent).toContain('Nothing is running low.');
    expect(productApi.get).not.toHaveBeenCalled();
  });

  it('restocks through the dialog and reloads', () => {
    setup();
    dialog.open.and.returnValue({ afterClosed: () => of(25) } as MatDialogRef<RestockDialogComponent, number>);
    inventoryApi.restock.and.returnValue(of(level(9, 28, 3)));

    component.restock(component.rows()[0]);

    expect(inventoryApi.restock).toHaveBeenCalledOnceWith(9, 25);
    expect(notifier.success).toHaveBeenCalledWith('Added 25 units to Product 9 — 25 now sellable.');
    expect(inventoryApi.lowStock).toHaveBeenCalledTimes(2);
  });

  it('reports a failed restock', () => {
    setup();
    dialog.open.and.returnValue({ afterClosed: () => of(5) } as MatDialogRef<RestockDialogComponent, number>);
    inventoryApi.restock.and.returnValue(throwError(() => new HttpErrorResponse({ status: 403 })));

    component.restock(component.rows()[0]);

    expect(notifier.error).toHaveBeenCalledWith('You do not have permission to do that.');
  });

  it('shows an error banner when the list fails', () => {
    setup(throwError(() => new HttpErrorResponse({ status: 503 })));

    expect(fixture.nativeElement.querySelector('[data-testid="list-error"]')?.textContent).toContain('Could not load');
  });
});
