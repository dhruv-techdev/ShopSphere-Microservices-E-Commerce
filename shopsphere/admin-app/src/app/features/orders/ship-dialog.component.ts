import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialogActions,
  MatDialogClose,
  MatDialogContent,
  MatDialogRef,
  MatDialogTitle,
} from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';

import { ShipRequest } from './order.models';

export interface ShipDialogData {
  orderId: number;
}

/** Same rule shipping-service enforces: letters, digits and dashes, 4–100 chars. */
export const TRACKING_NUMBER_PATTERN = /^[A-Za-z0-9-]{4,100}$/;

/** Optional field: blank is fine, otherwise the trimmed value must match the pattern. */
export function trackingNumberValidator(control: AbstractControl): ValidationErrors | null {
  const value = String(control.value ?? '').trim();
  return value === '' || TRACKING_NUMBER_PATTERN.test(value) ? null : { pattern: true };
}

/** Collects optional carrier / tracking number; closes with a ShipRequest (or undefined on cancel). */
@Component({
  selector: 'app-ship-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatDialogActions,
    MatDialogClose,
    MatDialogContent,
    MatDialogTitle,
    MatFormFieldModule,
    MatInputModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>Ship order #{{ data.orderId }}</h2>
    <form [formGroup]="form" (ngSubmit)="confirm()" novalidate>
      <mat-dialog-content class="dialog-body">
        <p class="muted">
          Leave both fields blank to use the default carrier and generate a tracking number.
          The customer is emailed once the shipment is dispatched.
        </p>
        <mat-form-field appearance="outline">
          <mat-label>Carrier</mat-label>
          <input matInput formControlName="carrier" maxlength="100" placeholder="ShopSphere Express" />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Tracking number</mat-label>
          <input matInput formControlName="trackingNumber" maxlength="100" autocomplete="off" data-testid="tracking-input" />
          @if (form.controls.trackingNumber.hasError('pattern')) {
            <mat-error>Use 4–100 letters, digits or dashes</mat-error>
          }
        </mat-form-field>
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-button type="button" mat-dialog-close>Cancel</button>
        <button mat-flat-button type="submit" data-testid="confirm-ship">Mark as shipped</button>
      </mat-dialog-actions>
    </form>
  `,
  styles: `
    .dialog-body { display: flex; flex-direction: column; gap: 4px; min-width: min(400px, 80vw); }
    .dialog-body p { margin: 0 0 12px; }
  `,
})
export class ShipDialogComponent {
  private readonly fb = inject(FormBuilder);
  private readonly dialogRef = inject<MatDialogRef<ShipDialogComponent, ShipRequest>>(MatDialogRef);
  protected readonly data = inject<ShipDialogData>(MAT_DIALOG_DATA);

  readonly form = this.fb.nonNullable.group({
    carrier: ['', [Validators.maxLength(100)]],
    trackingNumber: ['', [trackingNumberValidator]],
  });

  confirm(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    this.dialogRef.close({
      carrier: v.carrier.trim() || null,
      trackingNumber: v.trackingNumber.trim().toUpperCase() || null,
    });
  }
}
