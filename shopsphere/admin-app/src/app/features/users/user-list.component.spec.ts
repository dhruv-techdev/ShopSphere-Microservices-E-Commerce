import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';

import { AdminUser } from './user.models';
import { UserApiService } from './user-api.service';
import { UserListComponent } from './user-list.component';

function user(overrides: Partial<AdminUser>): AdminUser {
  return {
    id: 7,
    firstName: 'Jane',
    lastName: 'Doe',
    email: 'jane@example.com',
    role: 'CUSTOMER',
    enabled: true,
    emailVerified: true,
    emailVerifiedAt: null,
    createdAt: '2026-09-01T10:00:00Z',
    updatedAt: null,
    ...overrides,
  };
}

describe('UserListComponent', () => {
  let fixture: ComponentFixture<UserListComponent>;
  let component: UserListComponent;
  let api: jasmine.SpyObj<UserApiService>;

  beforeEach(() => {
    api = jasmine.createSpyObj<UserApiService>('UserApiService', ['list']);
    api.list.and.returnValue(
      of({
        content: [
          user({}),
          user({ id: 1, firstName: 'Ada', lastName: 'Admin', email: 'admin@shopsphere.local', role: 'ADMIN' }),
          user({ id: 9, email: 'new@example.com', emailVerified: false }),
          user({ id: 10, email: 'gone@example.com', enabled: false }),
        ],
        page: 0,
        size: 20,
        totalElements: 4,
        totalPages: 1,
      }),
    );
    TestBed.configureTestingModule({
      imports: [UserListComponent],
      providers: [provideNoopAnimations(), provideRouter([]), { provide: UserApiService, useValue: api }],
    });
    fixture = TestBed.createComponent(UserListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  function rows(): HTMLElement[] {
    return Array.from(fixture.nativeElement.querySelectorAll('[data-testid="user-row"]'));
  }

  it('renders role and account status', () => {
    expect(api.list).toHaveBeenCalledOnceWith({ q: null, role: null, status: null, page: 0, size: 20 });
    expect(rows().length).toBe(4);
    expect(rows()[0].textContent).toContain('Active');
    expect(rows()[1].textContent).toContain('Admin');
    expect(rows()[2].textContent).toContain('Unverified');
    expect(rows()[3].textContent).toContain('Disabled');
  });

  it('links each user to their orders and notifications', () => {
    const links: HTMLAnchorElement[] = Array.from(rows()[0].querySelectorAll('a'));
    expect(links.map((a) => a.getAttribute('href'))).toEqual(['/orders?userId=7', '/notifications?userId=7']);
  });

  it('debounces search and filters, resetting to the first page', fakeAsync(() => {
    component.onPage({ pageIndex: 2, pageSize: 20, length: 100 });
    component.filters.setValue({ q: '  jane ', role: 'CUSTOMER', status: 'UNVERIFIED' });
    tick(300);

    expect(api.list.calls.mostRecent().args[0]).toEqual({ q: 'jane', role: 'CUSTOMER', status: 'UNVERIFIED', page: 0, size: 20 });
  }));

  it('shows an error banner when loading fails', () => {
    api.list.and.returnValue(throwError(() => new HttpErrorResponse({ status: 503 })));

    component.reload();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="list-error"]')?.textContent).toContain('Could not load users');
  });
});
