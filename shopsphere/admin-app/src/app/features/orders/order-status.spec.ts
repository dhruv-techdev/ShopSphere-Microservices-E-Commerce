import { buildOrder, buildShipment } from './orders.testing';
import { canCancel, canDeliver, canShip, orderStatusChip, paymentStateOf } from './order-status';

describe('order-status rules', () => {
  it('offers dispatch only for PAID orders with a PENDING shipment', () => {
    expect(canShip(buildOrder({ status: 'PAID' }), buildShipment({ status: 'PENDING' }))).toBeTrue();
    expect(canShip(buildOrder({ status: 'PENDING_PAYMENT' }), buildShipment({ status: 'PENDING' }))).toBeFalse();
    expect(canShip(buildOrder({ status: 'PAID' }), buildShipment({ status: 'SHIPPED' }))).toBeFalse();
    expect(canShip(buildOrder({ status: 'PAID' }), null)).toBeFalse();
  });

  it('offers delivery for SHIPPED shipments even if the order status still lags', () => {
    expect(canDeliver(buildOrder({ status: 'PAID' }), buildShipment({ status: 'SHIPPED' }))).toBeTrue();
    expect(canDeliver(buildOrder({ status: 'SHIPPED' }), buildShipment({ status: 'SHIPPED' }))).toBeTrue();
    expect(canDeliver(buildOrder({ status: 'CANCELLED' }), buildShipment({ status: 'SHIPPED' }))).toBeFalse();
    expect(canDeliver(buildOrder({ status: 'DELIVERED' }), buildShipment({ status: 'DELIVERED' }))).toBeFalse();
  });

  it('allows cancelling unpaid/paid orders that have not left the warehouse', () => {
    expect(canCancel(buildOrder({ status: 'PENDING_PAYMENT' }), null)).toBeTrue();
    expect(canCancel(buildOrder({ status: 'PAID' }), buildShipment({ status: 'PENDING' }))).toBeTrue();
    expect(canCancel(buildOrder({ status: 'PAID' }), buildShipment({ status: 'SHIPPED' }))).toBeFalse();
    expect(canCancel(buildOrder({ status: 'SHIPPED' }), null)).toBeFalse();
    expect(canCancel(buildOrder({ status: 'PAYMENT_FAILED' }), null)).toBeFalse();
  });

  it('derives payment state for payloads without paymentState', () => {
    expect(paymentStateOf({ status: 'SHIPPED' })).toBe('PAID');
    expect(paymentStateOf({ status: 'PAYMENT_FAILED' })).toBe('FAILED');
    expect(paymentStateOf({ status: 'PENDING_PAYMENT' })).toBe('PENDING');
    expect(paymentStateOf({ status: 'CANCELLED', paymentState: 'PAID' })).toBe('PAID');
  });

  it('maps statuses to chip tones', () => {
    expect(orderStatusChip('DELIVERED')).toBe('chip chip--ok');
    expect(orderStatusChip('PAYMENT_FAILED')).toBe('chip chip--warn');
  });
});
