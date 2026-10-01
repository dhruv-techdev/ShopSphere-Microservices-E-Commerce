/** Mirrors inventory-service StockResponse. */
export interface StockLevel {
  productId: number;
  availableQuantity: number;
  reservedQuantity: number;
  sellableQuantity: number;
  inStock: boolean;
  lowStock: boolean;
  updatedAt: string | null;
}

/** Stock row joined with catalog data for display. */
export interface LowStockRow extends StockLevel {
  productName: string | null;
  productActive: boolean | null;
}
