/** Mirrors product-service CategoryResponse. */
export interface Category {
  id: number;
  name: string;
  description: string | null;
  createdAt: string | null;
  /** Present on category endpoints; omitted when nested inside a product. */
  productCount?: number;
}

export interface CategoryRequest {
  name: string;
  description: string | null;
}

/** Mirrors product-service ProductResponse. */
export interface Product {
  id: number;
  name: string;
  description: string | null;
  price: number;
  category: Category | null;
  stockQuantity: number;
  active: boolean;
  createdAt: string | null;
  updatedAt: string | null;
}

/** Mirrors product-service ProductRequest (PUT replaces all mutable fields). */
export interface ProductRequest {
  name: string;
  description: string | null;
  price: number;
  categoryId: number | null;
  stockQuantity: number;
  active: boolean;
}
