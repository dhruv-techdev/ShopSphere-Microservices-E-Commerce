// Test helpers only — not referenced from the app bundle.
import { PageResult } from '../../core/api/page';
import { Category, Product } from './catalog.models';

export function buildCategory(overrides: Partial<Category> = {}): Category {
  return {
    id: 3,
    name: 'Electronics',
    description: 'Gadgets and devices',
    createdAt: '2026-09-01T10:00:00Z',
    productCount: 0,
    ...overrides,
  };
}

export function buildProduct(overrides: Partial<Product> = {}): Product {
  return {
    id: 5,
    name: 'Wireless Mouse',
    description: 'Bluetooth, ergonomic',
    price: 29.99,
    category: { id: 3, name: 'Electronics', description: null, createdAt: null },
    stockQuantity: 12,
    active: true,
    createdAt: '2026-09-01T10:00:00Z',
    updatedAt: '2026-09-02T10:00:00Z',
    ...overrides,
  };
}

export function pageOf<T>(content: T[], overrides: Partial<PageResult<T>> = {}): PageResult<T> {
  return { content, page: 0, size: 20, totalElements: content.length, totalPages: 1, ...overrides };
}
