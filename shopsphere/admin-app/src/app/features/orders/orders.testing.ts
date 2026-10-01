// Test helpers only — not referenced from the app bundle.
import { AdminOrderSummary, OrderDetail, Shipment } from './order.models';

export function buildOrderSummary(overrides: Partial<AdminOrderSummary> = {}): AdminOrderSummary {
  return {
    id: 42,
    userId: 7,
    status: 'PAID',
    paymentState: 'PAID',
    totalAmount: 129.97,
    itemCount: 3,
    trackingNumber: null,
    cancellationReason: null,
    createdAt: '2026-09-28T14:00:00Z',
    updatedAt: '2026-09-28T14:01:00Z',
    ...overrides,
  };
}

export function buildOrder(overrides: Partial<OrderDetail> = {}): OrderDetail {
  return {
    id: 42,
    userId: 7,
    status: 'PAID',
    totalAmount: 129.97,
    itemCount: 3,
    shippingAddress: {
      recipientName: 'Jane Doe',
      phone: '+1 416 555 0199',
      line1: '123 King St W',
      line2: null,
      city: 'Toronto',
      state: 'ON',
      postalCode: 'M5V 3L9',
      country: 'CA',
    },
    shipmentId: null,
    carrier: null,
    trackingNumber: null,
    shippedAt: null,
    deliveredAt: null,
    cancellationReason: null,
    cancellationDescription: null,
    cancelledAt: null,
    paymentState: 'PAID',
    paymentReference: '3f1c2a9e-5b7d-4c11-9a0e-6d2f8b7c1e44',
    paidAt: '2026-09-28T14:01:00Z',
    paymentFailedAt: null,
    paymentFailureReason: null,
    items: [
      { id: 1, productId: 5, productName: 'Wireless Mouse', unitPrice: 29.99, quantity: 2, lineTotal: 59.98 },
      { id: 2, productId: 9, productName: 'USB-C Hub', unitPrice: 69.99, quantity: 1, lineTotal: 69.99 },
    ],
    createdAt: '2026-09-28T14:00:00Z',
    updatedAt: '2026-09-28T14:01:00Z',
    ...overrides,
  };
}

export function buildShipment(overrides: Partial<Shipment> = {}): Shipment {
  return {
    id: 11,
    orderId: 42,
    userId: 7,
    status: 'PENDING',
    carrier: null,
    trackingNumber: null,
    shippingAddress: null,
    createdAt: '2026-09-28T14:01:05Z',
    updatedAt: '2026-09-28T14:01:05Z',
    shippedAt: null,
    deliveredAt: null,
    ...overrides,
  };
}
