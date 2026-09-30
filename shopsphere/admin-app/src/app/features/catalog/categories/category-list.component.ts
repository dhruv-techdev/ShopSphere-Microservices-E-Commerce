import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { RouterLink } from '@angular/router';
import { BehaviorSubject, catchError, filter, of, switchMap, tap } from 'rxjs';

import { apiErrorMessage } from '../../../core/api/api-error';
import { ConfirmService } from '../../../core/ui/confirm-dialog.component';
import { Notifier } from '../../../core/ui/notifier.service';
import { Category } from '../catalog.models';
import { CategoryApiService } from '../category-api.service';
import { CategoryDialogComponent, CategoryDialogData } from './category-dialog.component';

@Component({
  selector: 'app-category-list',
  standalone: true,
  imports: [
    DatePipe,
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressBarModule,
    MatTableModule,
    MatTooltipModule,
  ],
  templateUrl: './category-list.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CategoryListComponent {
  private readonly api = inject(CategoryApiService);
  private readonly dialog = inject(MatDialog);
  private readonly confirm = inject(ConfirmService);
  private readonly notifier = inject(Notifier);
  private readonly destroyRef = inject(DestroyRef);
  private readonly reload$ = new BehaviorSubject<void>(undefined);

  readonly columns = ['name', 'description', 'productCount', 'createdAt', 'actions'];
  readonly categories = signal<Category[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  readonly search = new FormControl('', { nonNullable: true });
  private readonly searchTerm = toSignal(this.search.valueChanges, { initialValue: '' });

  readonly visible = computed(() => {
    const term = this.searchTerm().trim().toLowerCase();
    const all = this.categories();
    if (!term) {
      return all;
    }
    return all.filter(
      (c) => c.name.toLowerCase().includes(term) || (c.description ?? '').toLowerCase().includes(term),
    );
  });

  constructor() {
    this.reload$
      .pipe(
        tap(() => {
          this.loading.set(true);
          this.error.set(null);
        }),
        switchMap(() =>
          this.api.list().pipe(
            catchError((err: unknown) => {
              this.error.set(apiErrorMessage(err, 'Could not load categories. Please try again.'));
              return of(null);
            }),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((categories) => {
        this.loading.set(false);
        if (categories) {
          this.categories.set([...categories].sort((a, b) => a.name.localeCompare(b.name)));
        }
      });
  }

  reload(): void {
    this.reload$.next();
  }

  openEditor(category?: Category): void {
    this.dialog
      .open<CategoryDialogComponent, CategoryDialogData, Category>(CategoryDialogComponent, {
        data: { category },
        width: '520px',
        autoFocus: 'first-tabbable',
      })
      .afterClosed()
      .pipe(
        filter((saved): saved is Category => !!saved),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((saved) => {
        this.notifier.success(category ? `Saved "${saved.name}".` : `Created "${saved.name}".`);
        this.reload();
      });
  }

  deleteCategory(category: Category): void {
    this.confirm
      .confirm({
        title: 'Delete category?',
        message: `"${category.name}" will be permanently deleted.`,
        confirmText: 'Delete',
        destructive: true,
      })
      .pipe(
        filter(Boolean),
        switchMap(() => this.api.delete(category.id)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => {
          this.notifier.success(`Deleted "${category.name}".`);
          this.reload();
        },
        error: (err: unknown) => {
          if (err instanceof HttpErrorResponse && err.status === 409) {
            this.notifier.error(`"${category.name}" still has products. Move or delete them first.`);
            this.reload();
            return;
          }
          this.notifier.error(apiErrorMessage(err, 'Could not delete the category.'));
        },
      });
  }

  inUse(category: Category): boolean {
    return (category.productCount ?? 0) > 0;
  }
}
