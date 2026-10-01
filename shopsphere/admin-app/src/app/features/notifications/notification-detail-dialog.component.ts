import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialogActions,
  MatDialogClose,
  MatDialogContent,
  MatDialogRef,
  MatDialogTitle,
} from '@angular/material/dialog';
import { finalize } from 'rxjs';

import { apiErrorMessage } from '../../core/api/api-error';
import { AdminNotification, DELIVERY_STATUS_CHIPS, canRetry, typeLabel } from './notification.models';
import { NotificationApiService } from './notification-api.service';

/** Full notification record; closes with the updated record after a successful retry. */
@Component({
  selector: 'app-notification-detail-dialog',
  standalone: true,
  imports: [DatePipe, MatButtonModule, MatDialogActions, MatDialogClose, MatDialogContent, MatDialogTitle],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>{{ n().subject }}</h2>
    <mat-dialog-content>
      @if (error()) {
        <div class="banner banner--error" role="alert" data-testid="retry-error">{{ error() }}</div>
      }
      <dl class="facts">
        <dt>Status</dt>
        <dd><span [class]="chips[n().deliveryStatus]" data-testid="detail-status">{{ n().deliveryStatus }}</span></dd>
        <dt>Type</dt>
        <dd>{{ label(n().type) }}</dd>
        <dt>Recipient</dt>
        <dd>{{ n().recipient ?? '—' }}{{ n().userId ? ' (user #' + n().userId + ')' : '' }}</dd>
        @if (n().orderId) {
          <dt>Order</dt>
          <dd>#{{ n().orderId }}</dd>
        }
        <dt>Channel</dt>
        <dd>{{ n().channel ?? '—' }}</dd>
        <dt>Attempts</dt>
        <dd>{{ n().attempts }}</dd>
        <dt>Created</dt>
        <dd>{{ n().createdAt | date: 'medium' }}</dd>
        @if (n().sentAt) {
          <dt>Sent</dt>
          <dd>{{ n().sentAt | date: 'medium' }}</dd>
        }
        @if (n().lastAttemptAt) {
          <dt>Last attempt</dt>
          <dd>{{ n().lastAttemptAt | date: 'medium' }}</dd>
        }
        @if (n().nextAttemptAt && n().deliveryStatus === 'RETRYING') {
          <dt>Next attempt</dt>
          <dd>{{ n().nextAttemptAt | date: 'medium' }}</dd>
        }
        @if (n().deadLetteredAt) {
          <dt>Dead-lettered</dt>
          <dd>{{ n().deadLetteredAt | date: 'medium' }}</dd>
        }
        @if (n().providerMessageId) {
          <dt>Provider id</dt>
          <dd class="mono small">{{ n().providerMessageId }}</dd>
        }
        @if (n().failureReason) {
          <dt>Failure</dt>
          <dd class="failure" data-testid="detail-failure">{{ n().failureReason }}</dd>
        }
      </dl>
      <h3 class="body-title">Body</h3>
      @if (n().bodyRedacted) {
        <p class="muted" data-testid="body-redacted">Hidden — this email contains a one-time sign-in link.</p>
      } @else {
        <pre class="body" data-testid="detail-body">{{ n().body }}</pre>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" mat-dialog-close>Close</button>
      @if (n().bodyRedacted && n().deliveryStatus === 'FAILED') {
        <span class="muted small hint" data-testid="no-retry-hint">Ask the user to request a new link instead of re-sending.</span>
      }
      @if (retryable()) {
        <button mat-flat-button type="button" (click)="retry()" [disabled]="busy()" data-testid="retry">Retry now</button>
      }
    </mat-dialog-actions>
  `,
  styles: `
    .body-title { margin: 16px 0 8px; font-size: 14px; font-weight: 500; }
    .body { white-space: pre-wrap; background: #f6f7fb; border-radius: 8px; padding: 12px; font-size: 13px; max-height: 280px; overflow: auto; }
    .failure { color: #8a1c1c; }
    .hint { margin-right: auto; padding-left: 8px; }
  `,
})
export class NotificationDetailDialogComponent {
  private readonly api = inject(NotificationApiService);
  private readonly dialogRef = inject<MatDialogRef<NotificationDetailDialogComponent, AdminNotification>>(MatDialogRef);

  protected readonly chips = DELIVERY_STATUS_CHIPS;
  protected readonly label = typeLabel;

  readonly n = signal<AdminNotification>(inject<AdminNotification>(MAT_DIALOG_DATA));
  readonly busy = signal(false);
  readonly error = signal<string | null>(null);

  retryable(): boolean {
    return canRetry(this.n());
  }

  retry(): void {
    this.busy.set(true);
    this.error.set(null);
    this.api
      .retry(this.n().id)
      .pipe(finalize(() => this.busy.set(false)))
      .subscribe({
        next: (updated) => this.dialogRef.close(updated),
        error: (err: unknown) => this.error.set(apiErrorMessage(err, 'Could not queue the retry.')),
      });
  }
}
