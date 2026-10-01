import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { RouterLink } from '@angular/router';
import { BehaviorSubject, catchError, debounceTime, of, switchMap, tap } from 'rxjs';

import { apiErrorMessage } from '../../core/api/api-error';
import { PageResult, emptyPage } from '../../core/api/page';
import { AdminUser, UserQuery, UserRole, UserStatusFilter } from './user.models';
import { UserApiService } from './user-api.service';

@Component({
  selector: 'app-user-list',
  standalone: true,
  imports: [
    DatePipe,
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatPaginatorModule,
    MatProgressBarModule,
    MatSelectModule,
    MatTableModule,
    MatTooltipModule,
  ],
  templateUrl: './user-list.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UserListComponent {
  private readonly api = inject(UserApiService);
  private readonly fb = inject(FormBuilder);

  readonly columns = ['name', 'email', 'role', 'status', 'createdAt', 'actions'];

  private readonly query$ = new BehaviorSubject<UserQuery>({ q: null, role: null, status: null, page: 0, size: 20 });
  readonly page = signal<PageResult<AdminUser>>(emptyPage(20));
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  readonly filters = this.fb.group({
    q: this.fb.nonNullable.control(''),
    role: this.fb.control<UserRole | null>(null),
    status: this.fb.control<UserStatusFilter | null>(null),
  });

  constructor() {
    this.query$
      .pipe(
        tap(() => {
          this.loading.set(true);
          this.error.set(null);
        }),
        switchMap((query) =>
          this.api.list(query).pipe(
            catchError((err: unknown) => {
              this.error.set(apiErrorMessage(err, 'Could not load users.'));
              return of(null);
            }),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((page) => {
        this.loading.set(false);
        if (page) {
          this.page.set(page);
        }
      });

    this.filters.valueChanges.pipe(debounceTime(300), takeUntilDestroyed()).subscribe(() => {
      const v = this.filters.getRawValue();
      this.query$.next({ ...this.query$.value, q: v.q.trim() || null, role: v.role, status: v.status, page: 0 });
    });
  }

  onPage(event: PageEvent): void {
    this.query$.next({ ...this.query$.value, page: event.pageIndex, size: event.pageSize });
  }

  reload(): void {
    this.query$.next({ ...this.query$.value });
  }
}
