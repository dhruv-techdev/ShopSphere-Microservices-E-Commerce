import { HttpErrorResponse } from '@angular/common/http';
import { FormControl, FormGroup } from '@angular/forms';

import { apiErrorMessage, applyFieldErrors, fieldErrorsOf } from './api-error';

describe('api-error helpers', () => {
  const httpError = (status: number, error: unknown = null) => new HttpErrorResponse({ status, error });

  describe('apiErrorMessage', () => {
    it('uses the server message for 4xx responses', () => {
      expect(apiErrorMessage(httpError(409, { message: 'Category already exists with name: Toys' }), 'fallback')).toBe(
        'Category already exists with name: Toys',
      );
    });

    it('never shows server details for 5xx responses', () => {
      expect(apiErrorMessage(httpError(500, { message: 'NullPointerException at ...' }), 'Could not save.')).toBe(
        'Could not save.',
      );
    });

    it('explains network, auth and permission failures', () => {
      expect(apiErrorMessage(httpError(0), 'x')).toContain('Cannot reach');
      expect(apiErrorMessage(httpError(401), 'x')).toContain('session has expired');
      expect(apiErrorMessage(httpError(403), 'x')).toContain('permission');
    });

    it('points at highlighted fields for validation failures', () => {
      expect(apiErrorMessage(httpError(400, { fieldErrors: { name: 'required' } }), 'x')).toBe(
        'Please fix the highlighted fields.',
      );
    });

    it('reads ProblemDetails (detail) from the .NET shipping-service', () => {
      expect(apiErrorMessage(httpError(409, { title: 'Conflict', detail: 'Shipment 5 is CANCELLED' }), 'x')).toBe(
        'Shipment 5 is CANCELLED',
      );
    });

    it('falls back for non-HTTP errors', () => {
      expect(apiErrorMessage(new Error('boom'), 'fallback')).toBe('fallback');
    });
  });

  describe('fieldErrorsOf', () => {
    it('reads string messages and ignores anything else', () => {
      const err = httpError(400, { fieldErrors: { name: 'Name is required', price: 42 } });
      expect(fieldErrorsOf(err)).toEqual({ name: 'Name is required' });
      expect(fieldErrorsOf(httpError(400, { fieldErrors: ['x'] }))).toEqual({});
      expect(fieldErrorsOf(httpError(400, 'plain text'))).toEqual({});
    });

    it('reads .NET ValidationProblem errors (first message per field)', () => {
      const err = httpError(400, { errors: { trackingNumber: ['Too short', 'Bad chars'] } });
      expect(fieldErrorsOf(err)).toEqual({ trackingNumber: 'Too short' });
    });
  });

  describe('applyFieldErrors', () => {
    it('marks matching controls and returns unmatched messages', () => {
      const form = new FormGroup({ name: new FormControl('x'), price: new FormControl(1) });

      const unmatched = applyFieldErrors(form, { name: 'Name taken', sku: 'Unknown field' });

      expect(form.controls.name.getError('server')).toBe('Name taken');
      expect(form.controls.name.touched).toBeTrue();
      expect(form.controls.price.errors).toBeNull();
      expect(unmatched).toEqual(['Unknown field']);
    });

    it('clears the server error once the user edits the field', () => {
      const form = new FormGroup({ name: new FormControl('x') });
      applyFieldErrors(form, { name: 'Name taken' });

      form.controls.name.setValue('y');

      expect(form.controls.name.errors).toBeNull();
    });
  });
});
