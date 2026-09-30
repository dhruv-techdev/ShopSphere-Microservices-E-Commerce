import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
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
import { finalize } from 'rxjs';

import { apiErrorMessage, applyFieldErrors, fieldErrorsOf } from '../../../core/api/api-error';
import { Category, CategoryRequest } from '../catalog.models';
import { CategoryApiService } from '../category-api.service';

export interface CategoryDialogData {
  /** Present when editing; absent when creating. */
  category?: Category;
}

/** Create/edit a category. Saves itself and closes with the saved Category (or undefined on cancel). */
@Component({
  selector: 'app-category-dialog',
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
    <h2 mat-dialog-title>{{ category ? 'Edit category' : 'New category' }}</h2>
    <form [formGroup]="form" (ngSubmit)="save()" novalidate>
      <mat-dialog-content class="dialog-body">
        @if (errorMessage()) {
          <div class="banner banner--error" role="alert" data-testid="dialog-error">{{ errorMessage() }}</div>
        }
        <mat-form-field appearance="outline">
          <mat-label>Name</mat-label>
          <input matInput formControlName="name" maxlength="100" required cdkFocusInitial data-testid="category-name" />
          @if (form.controls.name.hasError('required')) {
            <mat-error>Name is required</mat-error>
          } @else if (form.controls.name.hasError('server')) {
            <mat-error>{{ form.controls.name.getError('server') }}</mat-error>
          }
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Description</mat-label>
          <textarea matInput formControlName="description" rows="3" maxlength="500"></textarea>
          <mat-hint align="end">{{ form.controls.description.value.length }} / 500</mat-hint>
        </mat-form-field>
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-button type="button" mat-dialog-close [disabled]="saving()">Cancel</button>
        <button mat-flat-button type="submit" [disabled]="saving()" data-testid="save-category">
          {{ category ? 'Save' : 'Create' }}
        </button>
      </mat-dialog-actions>
    </form>
  `,
  styles: `
    .dialog-body { display: flex; flex-direction: column; gap: 4px; min-width: min(420px, 80vw); }
  `,
})
export class CategoryDialogComponent {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(CategoryApiService);
  private readonly dialogRef = inject<MatDialogRef<CategoryDialogComponent, Category>>(MatDialogRef);
  private readonly destroyRef = inject(DestroyRef);

  readonly category = inject<CategoryDialogData>(MAT_DIALOG_DATA)?.category;
  readonly saving = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    name: [this.category?.name ?? '', [Validators.required, Validators.maxLength(100)]],
    description: [this.category?.description ?? '', [Validators.maxLength(500)]],
  });

  save(): void {
    if (this.saving()) {
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const v = this.form.getRawValue();
    const request: CategoryRequest = { name: v.name.trim(), description: v.description.trim() || null };
    const save$ = this.category ? this.api.update(this.category.id, request) : this.api.create(request);

    this.saving.set(true);
    this.errorMessage.set(null);
    this.dialogRef.disableClose = true;
    save$
      .pipe(
        finalize(() => {
          this.saving.set(false);
          this.dialogRef.disableClose = false;
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (saved) => this.dialogRef.close(saved),
        error: (err: unknown) => {
          if (err instanceof HttpErrorResponse && err.status === 409) {
            this.form.controls.name.setErrors({ server: 'A category with this name already exists.' });
            this.form.controls.name.markAsTouched();
            this.errorMessage.set(null);
            return;
          }
          const unmatched = applyFieldErrors(this.form, fieldErrorsOf(err));
          this.errorMessage.set([apiErrorMessage(err, 'Could not save the category.'), ...unmatched].join(' '));
        },
      });
  }
}
