import { OrderDetail, OrderStatus, PaymentState, Shipment, ShipmentStatus } from './order.models';

export type Tone = 'ok' | 'warn' | 'info' | 'accent' | 'muted' | 'neutral';

export const ORDER_STATUS_LABELS: Record<OrderStatus, string> = {
  PENDING_PAYMENT: 'Awaiting payment',
  PAID: 'Paid',
  PAYMENT_FAILED: 'Payment failed',
  SHIPPED: 'Shipped',
  DELIVERED: 'Delivered',
  CANCELLED: 'Cancelled',
};

const ORDER_STATUS_TONES: Record<OrderStatus, Tone> = {
  PENDING_PAYMENT: 'neutral',
  PAID: 'info',
  PAYMENT_FAILED: 'warn',
  SHIPPED: 'accent',
  DELIVERED: 'ok',
  CANCELLED: 'muted',
};

export const PAYMENT_STATE_LABELS: Record<PaymentState, string> = {
  PENDING: 'Pending',
  PAID: 'Paid',
  FAILED: 'Failed',
};

const PAYMENT_STATE_TONES: Record<PaymentState, Tone> = {
  PENDING: 'neutral',
  PAID: 'ok',
  FAILED: 'warn',
};

export const SHIPMENT_STATUS_LABELS: Record<ShipmentStatus, string> = {
  PENDING: 'Ready to ship',
  SHIPPED: 'In transit',
  DELIVERED: 'Delivered',
  CANCELLED: 'Cancelled',
};

const SHIPMENT_STATUS_TONES: Record<ShipmentStatus, Tone> = {
  PENDING: 'info',
  SHIPPED: 'accent',
  DELIVERED: 'ok',
  CANCELLED: 'muted',
};

export function orderStatusLabel(status: OrderStatus): string {
  return ORDER_STATUS_LABELS[status] ?? status;
}

export function orderStatusChip(status: OrderStatus): string {
  return `chip chip--${ORDER_STATUS_TONES[status] ?? 'neutral'}`;
}

export function paymentStateChip(state: PaymentState): string {
  return `chip chip--${PAYMENT_STATE_TONES[state] ?? 'neutral'}`;
}

export function shipmentStatusChip(status: ShipmentStatus): string {
  return `chip chip--${SHIPMENT_STATUS_TONES[status] ?? 'neutral'}`;
}

/** Uses the server-derived state when present; falls back to the order status for older payloads. */
export function paymentStateOf(order: Pick<OrderDetail, 'status' | 'paymentState'>): PaymentState {
  if (order.paymentState) {
    return order.paymentState;
  }
  switch (order.status) {
    case 'PAID':
    case 'SHIPPED':
    case 'DELIVERED':
      return 'PAID';
    case 'PAYMENT_FAILED':
      return 'FAILED';
    default:
      return 'PENDING';
  }
}

/** Dispatch is only offered for paid orders whose shipment is still waiting. */
export function canShip(order: OrderDetail | null, shipment: Shipment | null): boolean {
  return order?.status === 'PAID' && shipment?.status === 'PENDING';
}

/** shipping-service is the source of truth: a SHIPPED shipment can be delivered even if the order lags. */
export function canDeliver(order: OrderDetail | null, shipment: Shipment | null): boolean {
  return !!order && order.status !== 'CANCELLED' && shipment?.status === 'SHIPPED';
}

/** Mirrors order-service's rule (PENDING_PAYMENT or PAID) and never cancels goods already on the way. */
export function canCancel(order: OrderDetail | null, shipment: Shipment | null): boolean {
  if (!order || (order.status !== 'PENDING_PAYMENT' && order.status !== 'PAID')) {
    return false;
  }
  return !shipment || shipment.status === 'PENDING' || shipment.status === 'CANCELLED';
}
