import { convertToParamMap } from '@angular/router';

import {
  DEFAULT_PAGE_SIZE,
  DEFAULT_PRODUCT_SORT,
  filterUrlParams,
  hasActiveFilters,
  parseProductQuery,
  sameFilters,
  toProductHttpParams,
} from './product-query';

describe('product-query', () => {
  describe('parseProductQuery', () => {
    it('uses defaults for an empty URL', () => {
      const q = parseProductQuery(convertToParamMap({}));

      expect(q).toEqual({
        q: null,
        categoryId: null,
        active: null,
        inStock: null,
        minPrice: null,
        maxPrice: null,
        page: 0,
        size: DEFAULT_PAGE_SIZE,
        sort: DEFAULT_PRODUCT_SORT,
      });
      expect(hasActiveFilters(q)).toBeFalse();
    });

    it('parses every supported parameter', () => {
      const q = parseProductQuery(
        convertToParamMap({
          q: ' mouse ',
          categoryId: '3',
          active: 'true',
          inStock: 'false',
          minPrice: '10.5',
          maxPrice: '99',
          page: '2',
          size: '50',
          sort: 'price,asc',
        }),
      );

      expect(q).toEqual({
        q: 'mouse',
        categoryId: 3,
        active: true,
        inStock: false,
        minPrice: 10.5,
        maxPrice: 99,
        page: 2,
        size: 50,
        sort: { field: 'price', direction: 'asc' },
      });
      expect(hasActiveFilters(q)).toBeTrue();
    });

    it('drops malformed or unsupported values', () => {
      const q = parseProductQuery(
        convertToParamMap({
          categoryId: 'abc',
          active: 'yes',
          minPrice: '-1',
          maxPrice: 'NaN',
          page: '-4',
          size: '5000',
          sort: 'password,asc',
        }),
      );

      expect(q.categoryId).toBeNull();
      expect(q.active).toBeNull();
      expect(q.minPrice).toBeNull();
      expect(q.maxPrice).toBeNull();
      expect(q.page).toBe(0);
      expect(q.size).toBe(DEFAULT_PAGE_SIZE);
      expect(q.sort).toEqual(DEFAULT_PRODUCT_SORT);
    });
  });

  it('maps the query onto product-service parameter names', () => {
    const params = toProductHttpParams(
      parseProductQuery(convertToParamMap({ q: 'mouse', categoryId: '3', inStock: 'true', page: '1' })),
    );

    expect(params.get('name')).toBe('mouse');
    expect(params.get('categoryId')).toBe('3');
    expect(params.get('inStock')).toBe('true');
    expect(params.has('active')).toBeFalse();
    expect(params.has('minPrice')).toBeFalse();
    expect(params.get('page')).toBe('1');
    expect(params.get('size')).toBe(String(DEFAULT_PAGE_SIZE));
    expect(params.get('sort')).toBe('updatedAt,desc');
  });

  it('compares filter params loosely (null, undefined and "" are equal)', () => {
    const fromUrl = filterUrlParams(parseProductQuery(convertToParamMap({ categoryId: '3' })));

    expect(sameFilters(fromUrl, { categoryId: '3' })).toBeTrue();
    expect(sameFilters(fromUrl, { categoryId: 3, q: '' })).toBeTrue();
    expect(sameFilters(fromUrl, { categoryId: 4 })).toBeFalse();
  });
});
