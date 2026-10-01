import { Routes } from '@angular/router';

import { adminGuard, authGuard, guestGuard } from './core/auth/auth.guards';
import { ShellComponent } from './layout/shell.component';

export const routes: Routes = [
  {
    path: 'login',
    title: 'Sign in · ShopSphere Admin',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/login/login.component').then((m) => m.LoginComponent),
  },
  {
    path: '',
    component: ShellComponent,
    canActivate: [authGuard, adminGuard],
    canActivateChild: [authGuard, adminGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      {
        path: 'dashboard',
        title: 'Dashboard · ShopSphere Admin',
        loadComponent: () =>
          import('./features/dashboard/dashboard.component').then((m) => m.DashboardComponent),
      },
      // US44 — catalog management
      {
        path: 'products',
        title: 'Products · ShopSphere Admin',
        loadComponent: () =>
          import('./features/catalog/products/product-list.component').then((m) => m.ProductListComponent),
      },
      {
        path: 'products/new',
        title: 'New product · ShopSphere Admin',
        loadComponent: () =>
          import('./features/catalog/products/product-form.component').then((m) => m.ProductFormComponent),
      },
      {
        path: 'products/:id/edit',
        title: 'Edit product · ShopSphere Admin',
        loadComponent: () =>
          import('./features/catalog/products/product-form.component').then((m) => m.ProductFormComponent),
      },
      // US45 — order management
      {
        path: 'orders',
        title: 'Orders · ShopSphere Admin',
        loadComponent: () => import('./features/orders/order-list.component').then((m) => m.OrderListComponent),
      },
      {
        path: 'orders/:id',
        title: 'Order · ShopSphere Admin',
        loadComponent: () => import('./features/orders/order-detail.component').then((m) => m.OrderDetailComponent),
      },
      // US46 — operations dashboards
      {
        path: 'inventory/low-stock',
        title: 'Low stock · ShopSphere Admin',
        loadComponent: () => import('./features/inventory/low-stock.component').then((m) => m.LowStockComponent),
      },
      {
        path: 'payments',
        title: 'Payments · ShopSphere Admin',
        loadComponent: () => import('./features/payments/payments.component').then((m) => m.PaymentsComponent),
      },
      {
        path: 'users',
        title: 'Users · ShopSphere Admin',
        loadComponent: () => import('./features/users/user-list.component').then((m) => m.UserListComponent),
      },
      {
        path: 'notifications',
        title: 'Notifications · ShopSphere Admin',
        loadComponent: () =>
          import('./features/notifications/notification-log.component').then((m) => m.NotificationLogComponent),
      },
      {
        path: 'categories',
        title: 'Categories · ShopSphere Admin',
        loadComponent: () =>
          import('./features/catalog/categories/category-list.component').then((m) => m.CategoryListComponent),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
