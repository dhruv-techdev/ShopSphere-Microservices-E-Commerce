import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { ActivatedRoute, ParamMap, Params, Router, convertToParamMap, provideRouter } from '@angular/router';
import { BehaviorSubject, of, throwError } from 'rxjs';

import { PageResult } from '../../core/api/page';
import { AdminOrderSummary } from './order.models';
import { OrderApiService } from './order-api.service';
import { OrderListComponent } from './order-list.component';
import { buildOrderSummary } from './orders.testing';

describe('OrderListComponent', () => {
  let fixture: ComponentFixture<OrderListComponent>;
  let component: OrderListComponent;
  let orderApi: jasmine.SpyObj<OrderApiService>;
  let router: Router;
  let params$: BehaviorSubject<ParamMap>;

  function page(content: AdminOrderSummary[]): PageResult<AdminOrderSummary> {
    return { content, page: 0, size: 20, totalElements: content.length, totalPages: 1 };
  }

  function setup(params: Params = {}): void {
    orderApi = jasmine.createSpyObj<OrderApiService>('OrderApiService', ['list']);
    orderApi.list.and.returnValue(
      of(page([buildOrderSummary(), buildOrderSummary({ id: 43, status: 'SHIPPED', trackingNumber: 'SSX2609284K7QZ9M2PA' })])),
    );
    params$ = new BehaviorSubject<ParamMap>(convertToParamMap(params));

    TestBed.configureTestingModule({
      imports: [OrderListComponent],
      providers: [
        provideNoopAnimations(),
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { queryParamMap: params$.asObservable() } },
        { provide: OrderApiService, useValue: orderApi },
      ],
    });
    router = TestBed.inject(Router);
    spyOn(router, 'navigate').and.resolveTo(true);
    fixture = TestBed.createComponent(OrderListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  function lastQueryParams(): Params {
    return ((router.navigate as jasmine.Spy).calls.mostRecent().args[1] as { queryParams: Params }).queryParams;
  }

  it('loads orders for the URL filters and shows status, payment and tracking', () => {
    setup({ status: 'SHIPPED', userId: '7' });

    expect(orderApi.list).toHaveBeenCalledOnceWith(jasmine.objectContaining({ status: 'SHIPPED', userId: 7 }));
    expect(component.filters.getRawValue()).toEqual({ status: 'SHIPPED', userId: 7 });

    const rows: HTMLElement[] = Array.from(fixture.nativeElement.querySelectorAll('[data-testid="order-row"]'));
    expect(rows.length).toBe(2);
    expect(rows[0].textContent).toContain('#42');
    expect(rows[0].textContent).toContain('Paid');
    expect(rows[1].textContent).toContain('Shipped');
    expect(rows[1].textContent).toContain('SSX2609284K7QZ9M2PA');
  });

  it('writes the status filter to the URL and resets paging', fakeAsync(() => {
    setup({ page: '2' });

    component.filters.controls.status.setValue('PAID');
    tick(250);

    expect(lastQueryParams()).toEqual({ status: 'PAID', userId: null, page: null });
  }));

  it('maps the "All" chip back to no status filter', fakeAsync(() => {
    setup({ status: 'PAID' });

    component.filters.controls.status.setValue('ALL');
    tick(250);

    expect(lastQueryParams()).toEqual(jasmine.objectContaining({ status: null }));
  }));

  it('filters by customer id', fakeAsync(() => {
    setup();

    component.filters.controls.userId.setValue(7);
    tick(250);

    expect(lastQueryParams()).toEqual(jasmine.objectContaining({ userId: 7 }));
  }));

  it('opens an order by number', () => {
    setup();

    component.jump.setValue(1234);
    component.openOrder();

    expect(router.navigate).toHaveBeenCalledWith(['/orders', 1234]);
  });

  it('opens an order when the "Order #" form is submitted, without a native page submit', () => {
    setup();
    component.jump.setValue(77);

    const form: HTMLFormElement = fixture.nativeElement.querySelector('form.jump');
    const submit = new Event('submit', { cancelable: true });
    form.dispatchEvent(submit);

    expect(submit.defaultPrevented).toBeTrue();
    expect(router.navigate).toHaveBeenCalledWith(['/orders', 77]);
  });

  it('ignores an invalid order number', () => {
    setup();

    component.jump.setValue(0);
    component.openOrder();

    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('writes sort changes to the URL', () => {
    setup();

    component.onSortChange({ active: 'totalAmount', direction: 'desc' });
    expect(lastQueryParams()).toEqual({ sort: 'totalAmount,desc', page: null });

    component.onSortChange({ active: 'createdAt', direction: 'desc' });
    expect(lastQueryParams()).toEqual({ sort: null, page: null });
  });

  it('shows an error banner when loading fails', () => {
    setup();
    orderApi.list.and.returnValue(throwError(() => new HttpErrorResponse({ status: 403 })));

    component.reload();
    fixture.detectChanges();

    const banner: HTMLElement | null = fixture.nativeElement.querySelector('[data-testid="list-error"]');
    expect(banner?.textContent).toContain('permission');
  });
});
