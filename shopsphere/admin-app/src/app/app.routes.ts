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
