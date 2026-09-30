import { toPageResult } from './page';

describe('toPageResult', () => {
  it('reads the Spring Boot default (DIRECT) page shape', () => {
    const page = toPageResult({ content: [1, 2], number: 3, size: 2, totalElements: 20, totalPages: 10 });

    expect(page).toEqual({ content: [1, 2], page: 3, size: 2, totalElements: 20, totalPages: 10 });
  });

  it('reads the VIA_DTO page shape', () => {
    const page = toPageResult({ content: ['a'], page: { number: 1, size: 1, totalElements: 5, totalPages: 5 } });

    expect(page).toEqual({ content: ['a'], page: 1, size: 1, totalElements: 5, totalPages: 5 });
  });

  it('tolerates an empty body', () => {
    expect(toPageResult(null)).toEqual({ content: [], page: 0, size: 0, totalElements: 0, totalPages: 0 });
  });
});
