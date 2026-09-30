import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';

import { NotAdminError } from '../../core/auth/auth.models';
import { AuthService } from '../../core/auth/auth.service';
import { buildSession } from '../../core/auth/auth.testing';
import { LoginComponent } from './login.component';

describe('LoginComponent', () => {
  let fixture: ComponentFixture<LoginComponent>;
  let component: LoginComponent;
  let auth: jasmine.SpyObj<AuthService>;
  let router: Router;

  async function create(queryParams: Record<string, string> = {}): Promise<void> {
    auth = jasmine.createSpyObj<AuthService>('AuthService', ['login']);
    await TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [
        provideNoopAnimations(),
        provideRouter([]),
        { provide: AuthService, useValue: auth },
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap(queryParams) } } },
      ],
    }).compileComponents();

    router = TestBed.inject(Router);
    spyOn(router, 'navigateByUrl').and.resolveTo(true);
    fixture = TestBed.createComponent(LoginComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  function fillAndSubmit(): void {
    component.form.setValue({ email: 'admin@shopsphere.io', password: 'Secret123!' });
    component.submit();
    fixture.detectChanges();
  }

  function text(testId: string): string | undefined {
    const el: HTMLElement | null = fixture.nativeElement.querySelector(`[data-testid="${testId}"]`);
    return el?.textContent?.trim();
  }

  it('does not call the API when the form is invalid', async () => {
    await create();

    component.submit();

    expect(auth.login).not.toHaveBeenCalled();
    expect(component.form.controls.email.touched).toBeTrue();
  });

  it('signs in and navigates to the returnUrl', async () => {
    await create({ returnUrl: '/orders' });
    auth.login.and.returnValue(of(buildSession()));

    fillAndSubmit();

    expect(auth.login).toHaveBeenCalledOnceWith('admin@shopsphere.io', 'Secret123!');
    expect(router.navigateByUrl).toHaveBeenCalledWith('/orders');
    expect(component.submitting()).toBeFalse();
  });

  it('ignores an unsafe returnUrl', async () => {
    await create({ returnUrl: '//evil.example' });
    auth.login.and.returnValue(of(buildSession()));

    fillAndSubmit();

    expect(router.navigateByUrl).toHaveBeenCalledWith('/dashboard');
  });

  it('shows invalid-credentials on 401 and clears the password', async () => {
    await create();
    auth.login.and.returnValue(throwError(() => new HttpErrorResponse({ status: 401 })));

    fillAndSubmit();

    expect(text('login-error')).toBe('Invalid email or password.');
    expect(component.form.controls.password.value).toBe('');
    expect(router.navigateByUrl).not.toHaveBeenCalled();
  });

  it('explains unverified email on 403 EMAIL_NOT_VERIFIED', async () => {
    await create();
    auth.login.and.returnValue(
      throwError(() => new HttpErrorResponse({ status: 403, error: { error: 'EMAIL_NOT_VERIFIED' } })),
    );

    fillAndSubmit();

    expect(text('login-error')).toContain('not verified');
  });

  it('explains that non-admin accounts cannot use the app', async () => {
    await create();
    auth.login.and.returnValue(throwError(() => new NotAdminError()));

    fillAndSubmit();

    expect(text('login-error')).toContain('administrator access');
  });

  it('shows a notice after the session expired', async () => {
    await create({ reason: 'expired' });

    expect(text('login-notice')).toContain('expired');
  });
});
