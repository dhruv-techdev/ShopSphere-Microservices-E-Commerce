import { HttpErrorResponse } from '@angular/common/http';

import { NotAdminError } from '../../core/auth/auth.models';
import { DEFAULT_AFTER_LOGIN, loginErrorMessage, loginNotice, safeReturnUrl } from './login.utils';

describe('login utils', () => {
  describe('safeReturnUrl', () => {
    it('keeps same-app paths', () => {
      expect(safeReturnUrl('/orders?status=PAID')).toBe('/orders?status=PAID');
    });

    it('rejects missing, external and protocol-relative targets', () => {
      for (const bad of [null, undefined, '', 'https://evil.example', '//evil.example', '/\\evil.example', '/login']) {
        expect(safeReturnUrl(bad)).withContext(String(bad)).toBe(DEFAULT_AFTER_LOGIN);
      }
    });
  });

  describe('loginNotice', () => {
    it('maps known reasons', () => {
      expect(loginNotice('expired')).toContain('expired');
      expect(loginNotice('forbidden')).toContain('Administrator');
      expect(loginNotice(null)).toBeNull();
    });
  });

  describe('loginErrorMessage', () => {
    const httpError = (status: number, error: unknown = null) => new HttpErrorResponse({ status, error });

    it('maps 401 to invalid credentials', () => {
      expect(loginErrorMessage(httpError(401))).toBe('Invalid email or password.');
    });

    it('maps 403 EMAIL_NOT_VERIFIED to a verification hint', () => {
      expect(loginErrorMessage(httpError(403, { error: 'EMAIL_NOT_VERIFIED' }))).toContain('not verified');
    });

    it('uses the server message for 400', () => {
      expect(loginErrorMessage(httpError(400, { message: 'Email must be valid' }))).toBe('Email must be valid');
    });

    it('handles network, rate-limit and server errors', () => {
      expect(loginErrorMessage(httpError(0))).toContain('Cannot reach');
      expect(loginErrorMessage(httpError(429))).toContain('Too many');
      expect(loginErrorMessage(httpError(502, '<html>Bad Gateway</html>'))).toContain('try again shortly');
    });

    it('explains non-admin accounts', () => {
      expect(loginErrorMessage(new NotAdminError())).toContain('administrator access');
    });
  });
});
