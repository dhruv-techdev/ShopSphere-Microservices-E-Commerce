import { HttpParams } from '@angular/common/http';
import { ParamMap, Params } from '@angular/router';

/** Columns product-service can sort on (Spring Data property names). */
export const PRODUCT_SORT_FIELDS = ['name', 'price', 'stockQuantity', 'active', 'createdAt', 'updatedAt'] as const;
export type ProductSortField = (typeof PRODUCT_SORT_FIELDS)[number];
export type SortDirection = 'asc' | 'desc';

export interface ProductSort {
  field: ProductSortField;
  direction: SortDirection;
}

export const DEFAULT_PRODUCT_SORT: ProductSort = { field: 'updatedAt', direction: 'desc' };
export const PAGE_SIZE_OPTIONS = [10, 20, 50, 100];
export const DEFAULT_PAGE_SIZE = 20;
const MAX_PAGE_SIZE = 100;

/** List state. Lives in the URL so filters survive reloads, back/forward and shared links. */
export interface ProductQuery {
  q: string | null;
  categoryId: number | null;
  active: boolean | null;
  inStock: boolean | null;
  minPrice: number | null;
  maxPrice: number | null;
  page: number;
  size: number;
  sort: ProductSort;
}

export type ProductFilterKey = 'q' | 'categoryId' | 'active' | 'inStock' | 'minPrice' | 'maxPrice';
export const PRODUCT_FILTER_KEYS: readonly ProductFilterKey[] = [
  'q',
  'categoryId',
  'active',
  'inStock',
  'minPrice',
  'maxPrice',
];

export function parseProductQuery(params: ParamMap): ProductQuery {
  const size = toInt(params.get('size'));
  return {
    q: params.get('q')?.trim() || null,
    categoryId: toPositiveInt(params.get('categoryId')),
    active: toBool(params.get('active')),
    inStock: toBool(params.get('inStock')),
    minPrice: toNonNegativeNumber(params.get('minPrice')),
    maxPrice: toNonNegativeNumber(params.get('maxPrice')),
    page: Math.max(0, toInt(params.get('page')) ?? 0),
    size: size !== null && size >= 1 && size <= MAX_PAGE_SIZE ? size : DEFAULT_PAGE_SIZE,
    sort: parseSort(params.get('sort')),
  };
}

export function parseSort(raw: string | null): ProductSort {
  if (!raw) {
    return DEFAULT_PRODUCT_SORT;
  }
  const [field, direction = 'asc'] = raw.split(',');
  const known = (PRODUCT_SORT_FIELDS as readonly string[]).includes(field);
  if (!known || (direction !== 'asc' && direction !== 'desc')) {
    return DEFAULT_PRODUCT_SORT;
  }
  return { field: field as ProductSortField, direction };
}

export function formatSort(sort: ProductSort): string {
  return `${sort.field},${sort.direction}`;
}

/** URL params for the filter part of a query; null removes the param when merged. */
export function filterUrlParams(q: Pick<ProductQuery, ProductFilterKey>): Params {
  return {
    q: q.q || null,
    categoryId: q.categoryId,
    active: q.active === null ? null : String(q.active),
    inStock: q.inStock === null ? null : String(q.inStock),
    minPrice: q.minPrice,
    maxPrice: q.maxPrice,
  };
}

export function sameFilters(a: Params, b: Params): boolean {
  return PRODUCT_FILTER_KEYS.every((key) => String(a[key] ?? '') === String(b[key] ?? ''));
}

export function hasActiveFilters(q: ProductQuery): boolean {
  return PRODUCT_FILTER_KEYS.some((key) => q[key] !== null);
}

/** Query string for GET /api/v1/products (product-service names the search param `name`). */
export function toProductHttpParams(q: ProductQuery): HttpParams {
  let params = new HttpParams()
    .set('page', String(q.page))
    .set('size', String(q.size))
    .set('sort', formatSort(q.sort));
  if (q.q) {
    params = params.set('name', q.q);
  }
  if (q.categoryId !== null) {
    params = params.set('categoryId', String(q.categoryId));
  }
  if (q.active !== null) {
    params = params.set('active', String(q.active));
  }
  if (q.inStock !== null) {
    params = params.set('inStock', String(q.inStock));
  }
  if (q.minPrice !== null) {
    params = params.set('minPrice', String(q.minPrice));
  }
  if (q.maxPrice !== null) {
    params = params.set('maxPrice', String(q.maxPrice));
  }
  return params;
}

function toInt(raw: string | null): number | null {
  if (raw === null || !/^-?\d+$/.test(raw.trim())) {
    return null;
  }
  return Number(raw);
}

function toPositiveInt(raw: string | null): number | null {
  const value = toInt(raw);
  return value !== null && value > 0 ? value : null;
}

function toNonNegativeNumber(raw: string | null): number | null {
  if (raw === null || raw.trim() === '') {
    return null;
  }
  const value = Number(raw);
  return Number.isFinite(value) && value >= 0 ? value : null;
}

function toBool(raw: string | null): boolean | null {
  if (raw === 'true') {
    return true;
  }
  if (raw === 'false') {
    return false;
  }
  return null;
}
