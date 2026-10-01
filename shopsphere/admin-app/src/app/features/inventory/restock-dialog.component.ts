import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
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

import { integer } from '../catalog/products/product-form.component';
import { LowStockRow } from './inventory.models';

/** Asks how many units arrived; closes with the quantity (or undefined on cancel). */
@Component({
  selector: 'app-restock-dialog',
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
    <h2 mat-dialog-title>Restock {{ data.productName ?? 'product #' + data.productId }}</h2>
    <form [formGroup]="form" (ngSubmit)="confirm()" novalidate>
      <mat-dialog-content class="dialog-body">
        <p class="muted">
          Available {{ data.availableQuantity }} · reserved {{ data.reservedQuantity }} · sellable {{ data.sellableQuantity }}
        </p>
        <mat-form-field appearance="outline">
          <mat-label>Units received</mat-label>
          <input matInput type="number" min="1" step="1" formControlName="quantity" data-testid="restock-qty" />
          @if (form.controls.quantity.hasError('required')) {
            <mat-error>Enter a quantity</mat-error>
          } @else if (form.controls.quantity.hasError('min') || form.controls.quantity.hasError('integer')) {
            <mat-error>Use a whole number of at least 1</mat-error>
          } @else if (form.controls.quantity.hasError('max')) {
            <mat-error>That's more than 100,000 units — check the number</mat-error>
          }
        </mat-form-field>
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-button type="button" mat-dialog-close>Cancel</button>
        <button mat-flat-button type="submit" data-testid="confirm-restock">Add stock</button>
      </mat-dialog-actions>
    </form>
  `,
  styles: `
    .dialog-body { display: flex; flex-direction: column; min-width: min(360px, 80vw); }
    .dialog-body p { margin: 0 0 12px; }
  `,
})
export class RestockDialogComponent {
  private readonly fb = inject(FormBuilder);
  private readonly dialogRef = inject<MatDialogRef<RestockDialogComponent, number>>(MatDialogRef);
  protected readonly data = inject<LowStockRow>(MAT_DIALOG_DATA);

  readonly form = this.fb.group({
    quantity: this.fb.control<number | null>(null, [Validators.required, Validators.min(1), Validators.max(100_000), integer]),
  });

  confirm(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.dialogRef.close(this.form.getRawValue().quantity as number);
  }
}
