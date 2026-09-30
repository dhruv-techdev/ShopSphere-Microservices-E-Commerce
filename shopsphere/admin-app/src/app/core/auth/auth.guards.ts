import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthService, REQUIRED_ROLE } from './auth.service';

/** Requires a live (or refreshable) session; otherwise /login?returnUrl=... */
export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  if (auth.isAuthenticated()) {
    return true;
  }
  return inject(Router).createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};

/** Requires role ADMIN. A non-admin session is revoked and cleared. */
export const adminGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  if (auth.hasRole(REQUIRED_ROLE)) {
    return true;
  }
  const router = inject(Router);
  if (!auth.session()) {
    return router.createUrlTree(['/login']);
  }
  auth.logout({ redirect: false });
  return router.createUrlTree(['/login'], { queryParams: { reason: 'forbidden' } });
};

/** Keeps signed-in admins off the login page. */
export const guestGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  if (auth.isAuthenticated() && auth.hasRole(REQUIRED_ROLE)) {
    return inject(Router).createUrlTree(['/dashboard']);
  }
  return true;
};
