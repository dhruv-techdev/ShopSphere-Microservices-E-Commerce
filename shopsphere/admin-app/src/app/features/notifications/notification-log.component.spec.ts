import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { of, throwError } from 'rxjs';

import { Notifier } from '../../core/ui/notifier.service';
import { AdminNotification } from './notification.models';
import { NotificationApiService } from './notification-api.service';
import { NotificationDetailDialogComponent } from './notification-detail-dialog.component';
import { NotificationLogComponent } from './notification-log.component';
import { buildNotification } from './notifications.testing';

describe('NotificationLogComponent', () => {
  let fixture: ComponentFixture<NotificationLogComponent>;
  let component: NotificationLogComponent;
  let api: jasmine.SpyObj<NotificationApiService>;
  let dialog: jasmine.SpyObj<MatDialog>;
  let notifier: jasmine.SpyObj<Notifier>;

  function setup(queryParams: Record<string, string> = {}): void {
    api = jasmine.createSpyObj<NotificationApiService>('NotificationApiService', ['list', 'summary']);
    api.list.and.returnValue(
      of({
        content: [
          buildNotification(),
          buildNotification({ id: 16, deliveryStatus: 'FAILED', failureReason: 'SMTP 550 mailbox unavailable', attempts: 5 }),
        ],
        page: 0,
        size: 25,
        totalElements: 2,
        totalPages: 1,
      }),
    );
    api.summary.and.returnValue(of({ total: 12, byStatus: { SENT: 10, FAILED: 2 } }));
    dialog = jasmine.createSpyObj<MatDialog>('MatDialog', ['open']);
    notifier = jasmine.createSpyObj<Notifier>('Notifier', ['success', 'error']);

    TestBed.configureTestingModule({
      imports: [NotificationLogComponent],
      providers: [
        provideNoopAnimations(),
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap(queryParams) } } },
        { provide: NotificationApiService, useValue: api },
        { provide: MatDialog, useValue: dialog },
        { provide: Notifier, useValue: notifier },
      ],
    });
    fixture = TestBed.createComponent(NotificationLogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  it('lists notifications with status counts and failure reasons', () => {
    setup();

    const rows: HTMLElement[] = Array.from(fixture.nativeElement.querySelectorAll('[data-testid="notification-row"]'));
    expect(rows.length).toBe(2);
    expect(rows[1].textContent).toContain('SMTP 550 mailbox unavailable');
    expect((fixture.nativeElement.querySelector('[data-testid="status-FAILED"]') as HTMLElement).textContent).toContain('2');
    expect(fixture.nativeElement.textContent).toContain('12 emails');
  });

  it('starts filtered by ?userId= from the user list', () => {
    setup({ userId: '7' });

    expect(api.list).toHaveBeenCalledOnceWith(jasmine.objectContaining({ userId: 7, orderId: null, page: 0 }));
    expect(component.filters.controls.userId.value).toBe(7);
  });

  it('applies filters after a pause', fakeAsync(() => {
    setup();

    component.filters.patchValue({ status: 'FAILED', type: 'PASSWORD_RESET', recipient: ' jane ' });
    tick(300);

    expect(api.list.calls.mostRecent().args[0]).toEqual(
      jasmine.objectContaining({ status: 'FAILED', type: 'PASSWORD_RESET', recipient: 'jane', page: 0 }),
    );
  }));

  it('"All" status clears the status filter', fakeAsync(() => {
    setup();

    component.filters.controls.status.setValue('ALL');
    tick(300);

    expect(api.list.calls.mostRecent().args[0].status).toBeNull();
  }));

  it('opens the detail and reloads after a retry', () => {
    setup();
    const failed = buildNotification({ id: 16, deliveryStatus: 'FAILED' });
    dialog.open.and.returnValue({
      afterClosed: () => of({ ...failed, deliveryStatus: 'RETRYING' }),
    } as MatDialogRef<NotificationDetailDialogComponent, AdminNotification>);

    component.open(failed);

    expect(dialog.open).toHaveBeenCalledWith(NotificationDetailDialogComponent, jasmine.objectContaining({ data: failed }));
    expect(notifier.success).toHaveBeenCalledWith('Notification #16 queued for another attempt.');
    expect(api.list).toHaveBeenCalledTimes(2);
    expect(api.summary).toHaveBeenCalledTimes(2);
  });

  it('shows an error banner when loading fails', () => {
    setup();
    api.list.and.returnValue(throwError(() => new HttpErrorResponse({ status: 503 })));

    component.reload();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="list-error"]')?.textContent).toContain('Could not load notifications');
  });
});
