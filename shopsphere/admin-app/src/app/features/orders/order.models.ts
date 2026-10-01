export const ORDER_STATUSES = ['PENDING_PAYMENT', 'PAID', 'PAYMENT_FAILED', 'SHIPPED', 'DELIVERED', 'CANCELLED'] as const;
export type OrderStatus = (typeof ORDER_STATUSES)[number];

/** Derived by order-service from payment.successful / payment.failed (US45). */
export type PaymentState = 'PENDING' | 'PAID' | 'FAILED';

/** Mirrors order-service AdminOrderSummaryResponse. */
export interface AdminOrderSummary {
  id: number;
  userId: number;
  status: OrderStatus;
  paymentState?: PaymentState;
  totalAmount: number;
  itemCount: number;
  trackingNumber: string | null;
  cancellationReason: string | null;
  createdAt: string | null;
  updatedAt: string | null;
}

export interface OrderAddress {
  recipientName: string;
  phone: string | null;
  line1: string;
  line2: string | null;
  city: string;
  state: string | null;
  postalCode: string;
  country: string;
}

export interface OrderItem {
  id: number;
  productId: number;
  productName: string;
  unitPrice: number;
  quantity: number;
  lineTotal: number;
}

/** Mirrors order-service OrderResponse. */
export interface OrderDetail {
  id: number;
  userId: number;
  status: OrderStatus;
  totalAmount: number;
  itemCount: number;
  shippingAddress: OrderAddress | null;
  shipmentId: number | null;
  carrier: string | null;
  trackingNumber: string | null;
  shippedAt: string | null;
  deliveredAt: string | null;
  cancellationReason: string | null;
  cancellationDescription: string | null;
  cancelledAt: string | null;
  paymentState?: PaymentState;
  paymentReference?: string | null;
  paidAt?: string | null;
  paymentFailedAt?: string | null;
  paymentFailureReason?: string | null;
  items: OrderItem[];
  createdAt: string | null;
  updatedAt: string | null;
}

export type ShipmentStatus = 'PENDING' | 'SHIPPED' | 'DELIVERED' | 'CANCELLED';

/** Mirrors shipping-service ShipmentResponse. */
export interface Shipment {
  id: number;
  orderId: number;
  userId: number;
  status: ShipmentStatus;
  carrier: string | null;
  trackingNumber: string | null;
  shippingAddress: OrderAddress | null;
  createdAt: string | null;
  updatedAt: string | null;
  shippedAt: string | null;
  deliveredAt: string | null;
}

/** Body of POST /api/v1/shipments/{id}/ship — blanks mean "use the defaults". */
export interface ShipRequest {
  carrier: string | null;
  trackingNumber: string | null;
}
