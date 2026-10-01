export const DELIVERY_STATUSES = ['PENDING', 'RETRYING', 'SENT', 'DEAD_LETTERED', 'FAILED', 'SIMULATED', 'SKIPPED'] as const;
export type DeliveryStatus = (typeof DELIVERY_STATUSES)[number];

export const NOTIFICATION_TYPES = [
  'ORDER_PLACED',
  'ORDER_CANCELLED',
  'PAYMENT_SUCCESSFUL',
  'PAYMENT_FAILED',
  'LOW_STOCK_ALERT',
  'SHIPMENT_DISPATCHED',
  'SHIPMENT_DELIVERED',
  'EMAIL_VERIFICATION',
  'PASSWORD_RESET',
] as const;
export type NotificationType = (typeof NOTIFICATION_TYPES)[number];

/** Mirrors notification-service AdminNotificationResponse. */
export interface AdminNotification {
  id: number;
  type: NotificationType;
  userId: number | null;
  orderId: number | null;
  recipient: string | null;
  channel: string | null;
  subject: string;
  /** Null when redacted (one-time links in verification / reset emails). */
  body: string | null;
  bodyRedacted: boolean;
  deliveryStatus: DeliveryStatus;
  attempts: number;
  providerMessageId: string | null;
  failureReason: string | null;
  sentAt: string | null;
  lastAttemptAt: string | null;
  nextAttemptAt: string | null;
  deadLetteredAt: string | null;
  createdAt: string | null;
}

/** Mirrors notification-service NotificationSummaryResponse. */
export interface NotificationSummary {
  total: number;
  byStatus: Partial<Record<DeliveryStatus, number>>;
}

export interface NotificationQuery {
  status: DeliveryStatus | null;
  type: NotificationType | null;
  userId: number | null;
  orderId: number | null;
  recipient: string | null;
  page: number;
  size: number;
}

export const DELIVERY_STATUS_CHIPS: Record<DeliveryStatus, string> = {
  PENDING: 'chip chip--neutral',
  RETRYING: 'chip chip--info',
  SENT: 'chip chip--ok',
  DEAD_LETTERED: 'chip chip--warn',
  FAILED: 'chip chip--warn',
  SIMULATED: 'chip chip--muted',
  SKIPPED: 'chip chip--muted',
};

/** Emails carrying one-time links: re-sending an old link is pointless (it has likely expired). */
export const ONE_TIME_LINK_TYPES: readonly NotificationType[] = ['EMAIL_VERIFICATION', 'PASSWORD_RESET'];

/**
 * Same rule as notification-service: only terminal FAILED emails go back in the queue (RETRYING ones are
 * already scheduled), and never a one-time-link email.
 */
export function canRetry(n: Pick<AdminNotification, 'deliveryStatus' | 'type'>): boolean {
  return n.deliveryStatus === 'FAILED' && !ONE_TIME_LINK_TYPES.includes(n.type);
}

export function typeLabel(type: NotificationType): string {
  return type
    .toLowerCase()
    .split('_')
    .map((w, i) => (i === 0 ? w.charAt(0).toUpperCase() + w.slice(1) : w))
    .join(' ');
}
