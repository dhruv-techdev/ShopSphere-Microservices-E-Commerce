import { HttpParams } from '@angular/common/http';
import { ParamMap, Params } from '@angular/router';

import { ORDER_STATUSES, OrderStatus } from './order.models';

export const ORDER_SORT_FIELDS = ['id', 'totalAmount', 'status', 'createdAt', 'updatedAt'] as const;
export type OrderSortField = (typeof ORDER_SORT_FIELDS)[number];

export interface OrderSort {
  field: OrderSortField;
  direction: 'asc' | 'desc';
}

export const DEFAULT_ORDER_SORT: OrderSort = { field: 'createdAt', direction: 'desc' };
export const ORDER_PAGE_SIZE_OPTIONS = [10, 20, 50, 100];
export const DEFAULT_ORDER_PAGE_SIZE = 20;

/** Order list state; lives in the URL (?status=PAID&userId=7&page=1&sort=totalAmount,desc). */
export interface OrderQuery {
  status: OrderStatus | null;
  userId: number | null;
  page: number;
  size: number;
  sort: OrderSort;
}

export function parseOrderQuery(params: ParamMap): OrderQuery {
  const status = params.get('status');
  const size = toInt(params.get('size'));
  return {
    status: (ORDER_STATUSES as readonly string[]).includes(status ?? '') ? (status as OrderStatus) : null,
    userId: toPositiveInt(params.get('userId')),
    page: Math.max(0, toInt(params.get('page')) ?? 0),
    size: size !== null && size >= 1 && size <= 100 ? size : DEFAULT_ORDER_PAGE_SIZE,
    sort: parseOrderSort(params.get('sort')),
  };
}

export function parseOrderSort(raw: string | null): OrderSort {
  if (!raw) {
    return DEFAULT_ORDER_SORT;
  }
  const [field, direction = 'asc'] = raw.split(',');
  if (!(ORDER_SORT_FIELDS as readonly string[]).includes(field) || (direction !== 'asc' && direction !== 'desc')) {
    return DEFAULT_ORDER_SORT;
  }
  return { field: field as OrderSortField, direction };
}

export function formatOrderSort(sort: OrderSort): string {
  return `${sort.field},${sort.direction}`;
}

export function orderFilterParams(q: Pick<OrderQuery, 'status' | 'userId'>): Params {
  return { status: q.status, userId: q.userId };
}

/** GET /api/v1/admin/orders?status=&userId=&page=&size=&sort= */
export function toOrderHttpParams(q: OrderQuery): HttpParams {
  let params = new HttpParams()
    .set('page', String(q.page))
    .set('size', String(q.size))
    .set('sort', formatOrderSort(q.sort));
  if (q.status) {
    params = params.set('status', q.status);
  }
  if (q.userId !== null) {
    params = params.set('userId', String(q.userId));
  }
  return params;
}

export function toPositiveInt(raw: string | number | null | undefined): number | null {
  if (raw === null || raw === undefined) {
    return null;
  }
  const text = String(raw).trim();
  if (!/^\d+$/.test(text)) {
    return null;
  }
  const value = Number(text);
  return value > 0 && Number.isSafeInteger(value) ? value : null;
}

function toInt(raw: string | null): number | null {
  if (raw === null || !/^-?\d+$/.test(raw.trim())) {
    return null;
  }
  return Number(raw);
}
