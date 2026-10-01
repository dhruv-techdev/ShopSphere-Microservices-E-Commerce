import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of, throwError } from 'rxjs';

import { AdminNotification } from './notification.models';
import { NotificationApiService } from './notification-api.service';
import { NotificationDetailDialogComponent } from './notification-detail-dialog.component';
import { buildNotification } from './notifications.testing';

describe('NotificationDetailDialogComponent', () => {
  let fixture: ComponentFixture<NotificationDetailDialogComponent>;
  let api: jasmine.SpyObj<NotificationApiService>;
  let dialogRef: jasmine.SpyObj<MatDialogRef<NotificationDetailDialogComponent, AdminNotification>>;

  function setup(notification: AdminNotification): void {
    api = jasmine.createSpyObj<NotificationApiService>('NotificationApiService', ['retry']);
    dialogRef = jasmine.createSpyObj<MatDialogRef<NotificationDetailDialogComponent, AdminNotification>>('MatDialogRef', ['close']);
    TestBed.configureTestingModule({
      imports: [NotificationDetailDialogComponent],
      providers: [
        provideNoopAnimations(),
        { provide: NotificationApiService, useValue: api },
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: MAT_DIALOG_DATA, useValue: notification },
      ],
    });
    fixture = TestBed.createComponent(NotificationDetailDialogComponent);
    fixture.detectChanges();
  }

  function el(testId: string): HTMLElement | null {
    return fixture.nativeElement.querySelector(`[data-testid="${testId}"]`);
  }

  it('shows the body and no retry for sent or already-scheduled emails', () => {
    setup(buildNotification());

    expect(el('detail-body')?.textContent).toContain('Thanks for your order!');
    expect(el('retry')).toBeNull();
  });

  it('does not offer a retry while one is already scheduled', () => {
    setup(buildNotification({ deliveryStatus: 'RETRYING', nextAttemptAt: '2026-09-30T10:05:00Z' }));

    expect(el('retry')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Next attempt');
  });

  it('hides redacted bodies and never re-sends one-time links', () => {
    setup(buildNotification({ type: 'PASSWORD_RESET', body: null, bodyRedacted: true, deliveryStatus: 'FAILED' }));

    expect(el('body-redacted')).not.toBeNull();
    expect(el('detail-body')).toBeNull();
    expect(el('retry')).toBeNull();
    expect(el('no-retry-hint')).not.toBeNull();
  });

  it('retries a failed email and closes with the updated record', () => {
    const failed = buildNotification({ deliveryStatus: 'FAILED', failureReason: 'SMTP 550' });
    setup(failed);
    const queued = { ...failed, deliveryStatus: 'RETRYING' as const };
    api.retry.and.returnValue(of(queued));

    expect(el('detail-failure')?.textContent).toContain('SMTP 550');
    (el('retry') as HTMLButtonElement).click();

    expect(api.retry).toHaveBeenCalledOnceWith(15);
    expect(dialogRef.close).toHaveBeenCalledOnceWith(queued);
  });

  it('shows the server message when the retry is refused', () => {
    setup(buildNotification({ deliveryStatus: 'FAILED' }));
    api.retry.and.returnValue(
      throwError(() => new HttpErrorResponse({ status: 409, error: { message: 'Notification 15 is SENT; only FAILED notifications can be retried' } })),
    );

    (el('retry') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(el('retry-error')?.textContent).toContain('only FAILED notifications');
    expect(dialogRef.close).not.toHaveBeenCalled();
  });
});
