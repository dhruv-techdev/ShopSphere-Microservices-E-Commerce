import { HttpContext, HttpContextToken } from '@angular/common/http';

/** Requests carrying this flag bypass the JWT interceptor (login/refresh/logout). */
export const SKIP_AUTH = new HttpContextToken<boolean>(() => false);

export function skipAuth(): HttpContext {
  return new HttpContext().set(SKIP_AUTH, true);
}
