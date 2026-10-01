// Test helpers only — not referenced from the app bundle.
import { AdminNotification } from './notification.models';

export function buildNotification(overrides: Partial<AdminNotification> = {}): AdminNotification {
  return {
    id: 15,
    type: 'ORDER_PLACED',
    userId: 7,
    orderId: 42,
    recipient: 'jane@example.com',
    channel: 'smtp',
    subject: 'Your order #42 is confirmed',
    body: 'Thanks for your order!',
    bodyRedacted: false,
    deliveryStatus: 'SENT',
    attempts: 1,
    providerMessageId: '<abc@mailpit>',
    failureReason: null,
    sentAt: '2026-09-30T10:00:01Z',
    lastAttemptAt: '2026-09-30T10:00:01Z',
    nextAttemptAt: null,
    deadLetteredAt: null,
    createdAt: '2026-09-30T10:00:00Z',
    ...overrides,
  };
}
