import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  computed,
  inject,
  input,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { Router, RouterLink } from '@angular/router';
import { Observable, catchError, filter, finalize, of, switchMap } from 'rxjs';

import { apiErrorMessage, applyFieldErrors, fieldErrorsOf } from '../../../core/api/api-error';
import { ConfirmService } from '../../../core/ui/confirm-dialog.component';
import { Notifier } from '../../../core/ui/notifier.service';
import { Category, Product, ProductRequest } from '../catalog.models';
import { CategoryApiService } from '../category-api.service';
import { ProductApiService } from '../product-api.service';

export function maxDecimals(places: number) {
  return (control: AbstractControl): ValidationErrors | null => {
    const value = control.value as number | null;
    if (value === null || value === undefined || Number.isNaN(value)) {
      return null;
    }
    const [, fraction = ''] = String(value).split('.');
    return fraction.length > places ? { maxDecimals: { places } } : null;
  };
}

export function integer(control: AbstractControl): ValidationErrors | null {
  const value = control.value as number | null;
  return value === null || value === undefined || Number.isInteger(value) ? null : { integer: true };
}

/** Create (/products/new) and edit (/products/:id/edit) — `id` is bound from the route. */
@Component({
  selector: 'app-product-form',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressBarModule,
    MatSelectModule,
    MatSlideToggleModule,
  ],
  templateUrl: './product-form.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProductFormComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly productApi = inject(ProductApiService);
  private readonly categoryApi = inject(CategoryApiService);
  private readonly confirm = inject(ConfirmService);
  private readonly notifier = inject(Notifier);

  readonly id = input<string | undefined>(undefined);

  readonly isEdit = computed(() => this.id() !== undefined);
  readonly productId = computed(() => {
    const raw = this.id();
    return raw !== undefined && /^\d+$/.test(raw) ? Number(raw) : null;
  });

  readonly categories = signal<Category[]>([]);
  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly loadError = signal<string | null>(null);
  readonly errorMessage = signal<string | null>(null);
  readonly productName = signal<string | null>(null);

  readonly form = this.fb.group({
    name: this.fb.nonNullable.control('', [Validators.required, Validators.maxLength(200)]),
    description: this.fb.nonNullable.control('', [Validators.maxLength(2000)]),
    price: this.fb.control<number | null>(null, [Validators.required, Validators.min(0.01), maxDecimals(2)]),
    stockQuantity: this.fb.control<number | null>(0, [Validators.required, Validators.min(0), integer]),
    categoryId: this.fb.control<number | null>(null),
    active: this.fb.nonNullable.control(true),
  });

  ngOnInit(): void {
    this.categoryApi
      .list()
      .pipe(
        catchError(() => of([] as Category[])),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((categories) => this.categories.set(categories));

    if (!this.isEdit()) {
      return;
    }
    const id = this.productId();
    if (id === null) {
      this.loadError.set('Product not found.');
      return;
    }

    this.loading.set(true);
    this.productApi
      .get(id)
      .pipe(
        finalize(() => this.loading.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (product) => this.fill(product),
        error: (err: unknown) =>
          this.loadError.set(
            err instanceof HttpErrorResponse && err.status === 404
              ? 'Product not found. It may have been deleted.'
              : apiErrorMessage(err, 'Could not load the product.'),
          ),
      });
  }

  save(): void {
    if (this.saving() || this.loading()) {
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const request = this.toRequest();
    const id = this.productId();
    const save$: Observable<Product> = id !== null ? this.productApi.update(id, request) : this.productApi.create(request);

    this.saving.set(true);
    this.errorMessage.set(null);
    save$
      .pipe(
        finalize(() => this.saving.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (saved) => {
          this.form.markAsPristine();
          this.notifier.success(id !== null ? `Saved "${saved.name}".` : `Created "${saved.name}".`);
          void this.router.navigate(['/products']);
        },
        error: (err: unknown) => {
          const unmatched = applyFieldErrors(this.form, fieldErrorsOf(err));
          this.errorMessage.set([apiErrorMessage(err, 'Could not save the product.'), ...unmatched].join(' '));
        },
      });
  }

  delete(): void {
    const id = this.productId();
    if (id === null || this.saving()) {
      return;
    }
    const name = this.productName() ?? 'This product';
    this.confirm
      .confirm({
        title: 'Delete product?',
        message: `"${name}" will be permanently removed from the catalog. To hide it from customers instead, switch it to inactive.`,
        confirmText: 'Delete',
        destructive: true,
      })
      .pipe(
        filter(Boolean),
        switchMap(() => {
          this.saving.set(true);
          return this.productApi.delete(id).pipe(finalize(() => this.saving.set(false)));
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => {
          this.notifier.success(`Deleted "${name}".`);
          void this.router.navigate(['/products']);
        },
        error: (err: unknown) => this.errorMessage.set(apiErrorMessage(err, 'Could not delete the product.')),
      });
  }

  private fill(product: Product): void {
    this.productName.set(product.name);
    this.form.reset({
      name: product.name,
      description: product.description ?? '',
      price: product.price,
      stockQuantity: product.stockQuantity,
      categoryId: product.category?.id ?? null,
      active: product.active,
    });
  }

  private toRequest(): ProductRequest {
    const v = this.form.getRawValue();
    return {
      name: v.name.trim(),
      description: v.description.trim() || null,
      price: v.price as number,
      stockQuantity: v.stockQuantity as number,
      categoryId: v.categoryId,
      active: v.active,
    };
  }
}
