import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed, fakeAsync, flush, tick } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { ActivatedRoute, ParamMap, Params, Router, convertToParamMap, provideRouter } from '@angular/router';
import { BehaviorSubject, of, throwError } from 'rxjs';

import { PageResult } from '../../../core/api/page';
import { ConfirmService } from '../../../core/ui/confirm-dialog.component';
import { Notifier } from '../../../core/ui/notifier.service';
import { Product } from '../catalog.models';
import { buildCategory, buildProduct, pageOf } from '../catalog.testing';
import { CategoryApiService } from '../category-api.service';
import { ProductApiService } from '../product-api.service';
import { ProductListComponent } from './product-list.component';

describe('ProductListComponent', () => {
  let fixture: ComponentFixture<ProductListComponent>;
  let component: ProductListComponent;
  let productApi: jasmine.SpyObj<ProductApiService>;
  let categoryApi: jasmine.SpyObj<CategoryApiService>;
  let confirm: jasmine.SpyObj<ConfirmService>;
  let notifier: jasmine.SpyObj<Notifier>;
  let router: Router;
  let params$: BehaviorSubject<ParamMap>;

  function setup(params: Params = {}, page: PageResult<Product> = pageOf([buildProduct()])): void {
    productApi = jasmine.createSpyObj<ProductApiService>('ProductApiService', ['search', 'delete']);
    productApi.search.and.returnValue(of(page));
    categoryApi = jasmine.createSpyObj<CategoryApiService>('CategoryApiService', ['list']);
    categoryApi.list.and.returnValue(of([buildCategory()]));
    confirm = jasmine.createSpyObj<ConfirmService>('ConfirmService', ['confirm']);
    notifier = jasmine.createSpyObj<Notifier>('Notifier', ['success', 'error']);
    params$ = new BehaviorSubject<ParamMap>(convertToParamMap(params));

    TestBed.configureTestingModule({
      imports: [ProductListComponent],
      providers: [
        provideNoopAnimations(),
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { queryParamMap: params$.asObservable() } },
        { provide: ProductApiService, useValue: productApi },
        { provide: CategoryApiService, useValue: categoryApi },
        { provide: ConfirmService, useValue: confirm },
        { provide: Notifier, useValue: notifier },
      ],
    });
    router = TestBed.inject(Router);
    spyOn(router, 'navigate').and.resolveTo(true);
    fixture = TestBed.createComponent(ProductListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  function rows(): number {
    return fixture.nativeElement.querySelectorAll('[data-testid="product-row"]').length;
  }

  function lastNavigation(): Params {
    const extras = (router.navigate as jasmine.Spy).calls.mostRecent().args[1] as { queryParams: Params };
    return extras.queryParams;
  }

  it('loads products for the URL query and renders them', () => {
    setup({ q: 'mouse', categoryId: '3', active: 'true', page: '1', size: '50', sort: 'price,asc' });

    expect(productApi.search).toHaveBeenCalledOnceWith(
      jasmine.objectContaining({
        q: 'mouse',
        categoryId: 3,
        active: true,
        page: 1,
        size: 50,
        sort: { field: 'price', direction: 'asc' },
      }),
    );
    expect(rows()).toBe(1);
    expect(component.filters.getRawValue()).toEqual(
      jasmine.objectContaining({ q: 'mouse', categoryId: 3, active: 'true', inStock: '' }),
    );
    expect(component.categories().length).toBe(1);
  });

  it('reloads when the URL changes (back/forward, shared links)', () => {
    setup();

    params$.next(convertToParamMap({ inStock: 'false' }));

    expect(productApi.search).toHaveBeenCalledTimes(2);
    expect(productApi.search.calls.mostRecent().args[0].inStock).toBeFalse();
  });

  it('debounces filter edits into the URL and resets paging', fakeAsync(() => {
    setup({ page: '3' });

    component.filters.controls.q.setValue('mou');
    tick(100);
    component.filters.controls.q.setValue('mouse');
    tick(300);

    expect(router.navigate).toHaveBeenCalledTimes(1);
    expect(lastNavigation()).toEqual(jasmine.objectContaining({ q: 'mouse', page: null }));
  }));

  it('does not navigate while the price range is invalid', fakeAsync(() => {
    setup();

    component.filters.patchValue({ minPrice: 50, maxPrice: 10 });
    tick(300);
    fixture.detectChanges();

    expect(router.navigate).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain("Min price can't be higher than max price.");
    flush(); // Material form-field timers started by the re-render
  }));

  it('clears all filters in one navigation', () => {
    setup({ q: 'mouse', inStock: 'true', page: '2' });

    component.clearFilters();

    expect(lastNavigation()).toEqual(
      jasmine.objectContaining({ q: null, inStock: null, categoryId: null, page: null }),
    );
  });

  it('pages through results and resets to the first page when the size changes', () => {
    setup({}, pageOf([buildProduct()], { totalElements: 100, totalPages: 5 }));

    component.onPage({ pageIndex: 2, previousPageIndex: 0, pageSize: 20, length: 100 });
    expect(lastNavigation()).toEqual({ page: 2, size: null });

    component.onPage({ pageIndex: 2, previousPageIndex: 2, pageSize: 50, length: 100 });
    expect(lastNavigation()).toEqual({ page: null, size: 50 });
  });

  it('writes sort changes to the URL (default sort is omitted)', () => {
    setup();

    component.onSortChange({ active: 'price', direction: 'desc' });
    expect(lastNavigation()).toEqual({ sort: 'price,desc', page: null });

    component.onSortChange({ active: 'updatedAt', direction: 'desc' });
    expect(lastNavigation()).toEqual({ sort: null, page: null });
  });

  it('deletes after confirmation, notifies and reloads', () => {
    setup();
    confirm.confirm.and.returnValue(of(true));
    productApi.delete.and.returnValue(of(undefined));

    component.deleteProduct(buildProduct());

    expect(confirm.confirm).toHaveBeenCalledWith(jasmine.objectContaining({ destructive: true }));
    expect(productApi.delete).toHaveBeenCalledOnceWith(5);
    expect(notifier.success).toHaveBeenCalled();
    expect(productApi.search).toHaveBeenCalledTimes(2);
  });

  it('does nothing when the deletion is cancelled', () => {
    setup();
    confirm.confirm.and.returnValue(of(false));

    component.deleteProduct(buildProduct());

    expect(productApi.delete).not.toHaveBeenCalled();
  });

  it('steps back a page after deleting the last row of a later page', () => {
    setup({ page: '2' }, pageOf([buildProduct()], { page: 2, totalElements: 41, totalPages: 3 }));
    confirm.confirm.and.returnValue(of(true));
    productApi.delete.and.returnValue(of(undefined));

    component.deleteProduct(buildProduct());

    expect(lastNavigation()).toEqual({ page: 1 });
  });

  it('reports a failed delete', () => {
    setup();
    confirm.confirm.and.returnValue(of(true));
    productApi.delete.and.returnValue(throwError(() => new HttpErrorResponse({ status: 404, error: { message: 'Product not found with id: 5' } })));

    component.deleteProduct(buildProduct());

    expect(notifier.error).toHaveBeenCalledWith('Product not found with id: 5');
  });

  it('shows an error banner when loading fails', () => {
    setup();
    productApi.search.and.returnValue(throwError(() => new HttpErrorResponse({ status: 503 })));

    component.reload();
    fixture.detectChanges();

    const banner: HTMLElement | null = fixture.nativeElement.querySelector('[data-testid="list-error"]');
    expect(banner?.textContent).toContain('Could not load products');
    expect(component.loading()).toBeFalse();
  });
});
