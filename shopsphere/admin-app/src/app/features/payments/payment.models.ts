export const PAYMENT_STATUSES = ['PENDING', 'SUCCESSFUL', 'FAILED', 'REFUNDED'] as const;
export type PaymentStatus = (typeof PAYMENT_STATUSES)[number];

/** Mirrors payment-service PaymentResponse. */
export interface Payment {
  paymentReference: string;
  orderId: number;
  userId: number;
  amount: number;
  status: PaymentStatus;
  message: string | null;
  createdAt: string | null;
  updatedAt: string | null;
}

export interface PaymentStatusTotal {
  status: PaymentStatus;
  count: number;
  amount: number;
}

/** Mirrors payment-service PaymentSummaryResponse. */
export interface PaymentSummary {
  from: string | null;
  to: string | null;
  totalCount: number;
  byStatus: PaymentStatusTotal[];
  /** SUCCESSFUL / (SUCCESSFUL + FAILED), or null when nothing settled. */
  successRate: number | null;
  duplicateChargeOrders: number;
}

/** Mirrors payment-service DuplicateChargeResponse: one order captured more than once. */
export interface DuplicateCharge {
  orderId: number;
  userId: number;
  successfulPayments: number;
  totalCaptured: number;
  paymentReferences: string[];
  lastCapturedAt: string | null;
}

export type PaymentIssueType = 'REFUND_REQUIRED' | 'PAID_AFTER_FAILURE' | 'PAYMENT_OVERDUE';

/** Mirrors order-service PaymentIssueResponse. */
export interface OrderPaymentIssue {
  orderId: number;
  userId: number;
  orderStatus: string;
  issue: PaymentIssueType;
  totalAmount: number;
  paymentReference: string | null;
  since: string | null;
}

export type ReconciliationKind = PaymentIssueType | 'DUPLICATE_CHARGE';

/** One row in the "Needs attention" list, whichever service detected it. */
export interface ReconciliationItem {
  kind: ReconciliationKind;
  orderId: number;
  userId: number;
  amount: number;
  references: string[];
  since: string | null;
}

export const RECONCILIATION_LABELS: Record<ReconciliationKind, { title: string; action: string }> = {
  REFUND_REQUIRED: { title: 'Refund required', action: 'Order was cancelled after payment — refund the customer.' },
  PAID_AFTER_FAILURE: {
    title: 'Paid after failure',
    action: 'Order is marked failed but a payment was captured — refund or re-open the order.',
  },
  PAYMENT_OVERDUE: { title: 'Payment overdue', action: 'Order is still awaiting payment — check payment-service.' },
  DUPLICATE_CHARGE: { title: 'Charged twice', action: 'More than one successful capture — refund the extras.' },
};

export interface PaymentListQuery {
  status: PaymentStatus | null;
  orderId: number | null;
  page: number;
  size: number;
}
