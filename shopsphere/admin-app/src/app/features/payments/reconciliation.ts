import { DuplicateCharge, OrderPaymentIssue, ReconciliationItem } from './payment.models';

/** Merges order-service and payment-service findings into one list, most urgent first. */
export function mergeReconciliation(issues: OrderPaymentIssue[], duplicates: DuplicateCharge[]): ReconciliationItem[] {
  const priority: Record<ReconciliationItem['kind'], number> = {
    DUPLICATE_CHARGE: 0,
    REFUND_REQUIRED: 1,
    PAID_AFTER_FAILURE: 2,
    PAYMENT_OVERDUE: 3,
  };
  const items: ReconciliationItem[] = [
    ...duplicates.map(
      (d): ReconciliationItem => ({
        kind: 'DUPLICATE_CHARGE',
        orderId: d.orderId,
        userId: d.userId,
        amount: d.totalCaptured,
        references: d.paymentReferences,
        since: d.lastCapturedAt,
      }),
    ),
    ...issues.map(
      (i): ReconciliationItem => ({
        kind: i.issue,
        orderId: i.orderId,
        userId: i.userId,
        amount: i.totalAmount,
        references: i.paymentReference ? [i.paymentReference] : [],
        since: i.since,
      }),
    ),
  ];
  return items.sort((a, b) => priority[a.kind] - priority[b.kind] || (a.since ?? '').localeCompare(b.since ?? ''));
}

export type Period = '24h' | '7d' | '30d' | 'all';

export const PERIOD_LABELS: Record<Period, string> = {
  '24h': 'Last 24 hours',
  '7d': 'Last 7 days',
  '30d': 'Last 30 days',
  all: 'All time',
};

/** ISO start of the period, or null for all time. */
export function periodStart(period: Period, now: Date = new Date()): string | null {
  const hours = { '24h': 24, '7d': 24 * 7, '30d': 24 * 30, all: 0 }[period];
  return hours ? new Date(now.getTime() - hours * 3_600_000).toISOString() : null;
}
