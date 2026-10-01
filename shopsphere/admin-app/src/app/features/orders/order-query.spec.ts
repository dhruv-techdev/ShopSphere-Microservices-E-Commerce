import { convertToParamMap } from '@angular/router';

import { DEFAULT_ORDER_PAGE_SIZE, DEFAULT_ORDER_SORT, parseOrderQuery, toOrderHttpParams, toPositiveInt } from './order-query';

describe('order-query', () => {
  it('parses status, customer, paging and sort', () => {
    const q = parseOrderQuery(
      convertToParamMap({ status: 'PAID', userId: '7', page: '2', size: '50', sort: 'totalAmount,desc' }),
    );

    expect(q).toEqual({
      status: 'PAID',
      userId: 7,
      page: 2,
      size: 50,
      sort: { field: 'totalAmount', direction: 'desc' },
    });
  });

  it('ignores unknown statuses, bad ids and unsupported sorts', () => {
    const q = parseOrderQuery(convertToParamMap({ status: 'LOST', userId: '-3', size: '999', sort: 'userEmail,asc' }));

    expect(q.status).toBeNull();
    expect(q.userId).toBeNull();
    expect(q.size).toBe(DEFAULT_ORDER_PAGE_SIZE);
    expect(q.sort).toEqual(DEFAULT_ORDER_SORT);
  });

  it('builds the admin list query string', () => {
    const params = toOrderHttpParams(parseOrderQuery(convertToParamMap({ status: 'SHIPPED', page: '1' })));

    expect(params.get('status')).toBe('SHIPPED');
    expect(params.has('userId')).toBeFalse();
    expect(params.get('page')).toBe('1');
    expect(params.get('sort')).toBe('createdAt,desc');
  });

  it('toPositiveInt accepts only positive whole numbers', () => {
    expect(toPositiveInt('42')).toBe(42);
    expect(toPositiveInt(7)).toBe(7);
    expect(toPositiveInt('0')).toBeNull();
    expect(toPositiveInt('4.2')).toBeNull();
    expect(toPositiveInt(undefined)).toBeNull();
  });
});
