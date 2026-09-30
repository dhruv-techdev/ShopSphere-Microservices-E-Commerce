/**
 * Spring Data pages come in two JSON shapes depending on the service config:
 *  - DIRECT (Boot default): { content, number, size, totalElements, totalPages, ... }
 *  - VIA_DTO:               { content, page: { number, size, totalElements, totalPages } }
 * The admin app normalises both into PageResult.
 */
export interface SpringPage<T> {
  content?: T[];
  number?: number;
  size?: number;
  totalElements?: number;
  totalPages?: number;
  page?: {
    number?: number;
    size?: number;
    totalElements?: number;
    totalPages?: number;
  };
}

export interface PageResult<T> {
  content: T[];
  /** Zero-based page index. */
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export function toPageResult<T>(raw: SpringPage<T> | null | undefined): PageResult<T> {
  const meta = raw?.page ?? raw ?? {};
  const content = raw?.content ?? [];
  return {
    content,
    page: meta.number ?? 0,
    size: meta.size ?? content.length,
    totalElements: meta.totalElements ?? content.length,
    totalPages: meta.totalPages ?? (content.length > 0 ? 1 : 0),
  };
}

export function emptyPage<T>(size: number): PageResult<T> {
  return { content: [], page: 0, size, totalElements: 0, totalPages: 0 };
}
