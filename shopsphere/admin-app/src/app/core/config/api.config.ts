import { InjectionToken } from '@angular/core';

/**
 * Base path of the ShopSphere API. Always same-origin:
 *  - Docker: nginx proxies /api/* to api-gateway:8080
 *  - ng serve: proxy.conf.json proxies /api/* to localhost:8080
 * The JWT interceptor only ever attaches tokens to URLs under this prefix.
 */
export const API_BASE_URL = new InjectionToken<string>('API_BASE_URL', {
  providedIn: 'root',
  factory: () => '/api/v1',
});
