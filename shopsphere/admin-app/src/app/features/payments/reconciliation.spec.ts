import { mergeReconciliation, periodStart } from './reconciliation';

describe('reconciliation helpers', () => {
  it('merges both sources with duplicate charges and refunds first', () => {
    const items = mergeReconciliation(
      [
        { orderId: 3, userId: 7, orderStatus: 'PENDING_PAYMENT', issue: 'PAYMENT_OVERDUE', totalAmount: 10, paymentReference: null, since: '2026-09-30T08:00:00Z' },
        { orderId: 2, userId: 7, orderStatus: 'CANCELLED', issue: 'REFUND_REQUIRED', totalAmount: 20, paymentReference: 'ref-2', since: '2026-09-30T09:00:00Z' },
      ],
      [{ orderId: 1, userId: 8, successfulPayments: 2, totalCaptured: 60, paymentReferences: ['a', 'b'], lastCapturedAt: '2026-09-30T10:00:00Z' }],
    );

    expect(items.map((i) => i.kind)).toEqual(['DUPLICATE_CHARGE', 'REFUND_REQUIRED', 'PAYMENT_OVERDUE']);
    expect(items[0]).toEqual(jasmine.objectContaining({ orderId: 1, amount: 60, references: ['a', 'b'] }));
    expect(items[2].references).toEqual([]);
  });

  it('computes period starts', () => {
    const now = new Date('2026-10-01T12:00:00Z');
    expect(periodStart('24h', now)).toBe('2026-09-30T12:00:00.000Z');
    expect(periodStart('7d', now)).toBe('2026-09-24T12:00:00.000Z');
    expect(periodStart('all', now)).toBeNull();
  });
});
