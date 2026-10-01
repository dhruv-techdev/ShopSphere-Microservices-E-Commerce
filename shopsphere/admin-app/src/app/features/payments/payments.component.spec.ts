import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';

import { PaymentSummary } from './payment.models';
import { PaymentApiService } from './payment-api.service';
import { OVERDUE_AFTER_MINUTES, PaymentsComponent } from './payments.component';

const SUMMARY: PaymentSummary = {
  from: null,
  to: null,
  totalCount: 12,
  byStatus: [
    { status: 'SUCCESSFUL', count: 9, amount: 1234.5 },
    { status: 'FAILED', count: 2, amount: 80 },
    { status: 'PENDING', count: 1, amount: 10 },
  ],
  successRate: 0.818,
  duplicateChargeOrders: 1,
};

describe('PaymentsComponent', () => {
  let fixture: ComponentFixture<PaymentsComponent>;
  let component: PaymentsComponent;
  let api: jasmine.SpyObj<PaymentApiService>;

  function setup(): void {
    api = jasmine.createSpyObj<PaymentApiService>('PaymentApiService', ['summary', 'list', 'duplicates', 'orderIssues']);
    api.summary.and.returnValue(of(SUMMARY));
    api.list.and.returnValue(
      of({
        content: [
          {
            paymentReference: 'ref-1',
            orderId: 42,
            userId: 7,
            amount: 129.97,
            status: 'SUCCESSFUL',
            message: 'Payment captured (simulated)',
            createdAt: '2026-09-30T10:00:00Z',
            updatedAt: null,
          },
        ],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
      }),
    );
    api.duplicates.and.returnValue(
      of([{ orderId: 50, userId: 8, successfulPayments: 2, totalCaptured: 60, paymentReferences: ['a', 'b'], lastCapturedAt: null }]),
    );
    api.orderIssues.and.returnValue(
      of([
        { orderId: 51, userId: 9, orderStatus: 'CANCELLED', issue: 'REFUND_REQUIRED', totalAmount: 20, paymentReference: 'ref-51', since: null },
      ]),
    );

    TestBed.configureTestingModule({
      imports: [PaymentsComponent],
      providers: [provideNoopAnimations(), provideRouter([]), { provide: PaymentApiService, useValue: api }],
    });
    fixture = TestBed.createComponent(PaymentsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  function text(testId: string): string {
    return (fixture.nativeElement.querySelector(`[data-testid="${testId}"]`) as HTMLElement | null)?.textContent ?? '';
  }

  it('shows captured, failed and duplicate-charge totals for the default period', () => {
    setup();

    expect(api.summary).toHaveBeenCalledOnceWith(jasmine.any(String));
    expect(text('stat-captured')).toContain('1,234.50');
    expect(text('stat-captured')).toContain('9 payments');
    expect(text('stat-failed')).toContain('2');
    expect(text('stat-duplicates')).toContain('1');
    expect(fixture.nativeElement.textContent).toContain('81.8%');
  });

  it('lists reconciliation issues from both services, duplicates first', () => {
    setup();

    expect(api.orderIssues).toHaveBeenCalledOnceWith(OVERDUE_AFTER_MINUTES);
    const rows: HTMLElement[] = Array.from(fixture.nativeElement.querySelectorAll('[data-testid="issue-row"]'));
    expect(rows.length).toBe(2);
    expect(rows[0].textContent).toContain('Charged twice');
    expect(rows[0].textContent).toContain('#50');
    expect(rows[1].textContent).toContain('Refund required');
  });

  it('keeps the partial list and warns when one source is down', () => {
    setup();
    api.orderIssues.and.returnValue(throwError(() => new HttpErrorResponse({ status: 503 })));

    component.refresh();
    fixture.detectChanges();

    expect(component.issues().length).toBe(1);
    expect(text('issues-error')).toContain('order-service');
  });

  it('reloads the summary when the period changes', () => {
    setup();

    component.setPeriod('all');

    expect(api.summary.calls.mostRecent().args[0]).toBeNull();
  });

  it('filters the payment list by status and order', fakeAsync(() => {
    setup();

    component.filters.setValue({ status: 'FAILED', orderId: 42 });
    tick(300);

    expect(api.list.calls.mostRecent().args[0]).toEqual({ status: 'FAILED', orderId: 42, page: 0, size: 20 });
  }));

  it('pages the payment list', () => {
    setup();

    component.onPage({ pageIndex: 2, pageSize: 50, length: 500 });

    expect(api.list.calls.mostRecent().args[0]).toEqual(jasmine.objectContaining({ page: 2, size: 50 }));
  });
});
