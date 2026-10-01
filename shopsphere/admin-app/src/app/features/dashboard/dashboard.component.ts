import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';

import { AuthService } from '../../core/auth/auth.service';

interface Tile {
  icon: string;
  title: string;
  description: string;
  /** Route for modules that exist; undefined = coming soon. */
  link?: string;
}

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [RouterLink, MatButtonModule, MatCardModule, MatIconModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1 class="heading">Welcome back, {{ auth.displayName() }}</h1>
    <p class="sub">Signed in as {{ auth.currentUser()?.email }} · {{ auth.currentUser()?.role }}</p>

    <div class="grid">
      @for (tile of tiles; track tile.title) {
        <mat-card appearance="outlined">
          <mat-card-header>
            <mat-icon mat-card-avatar>{{ tile.icon }}</mat-icon>
            <mat-card-title>{{ tile.title }}</mat-card-title>
            <mat-card-subtitle>{{ tile.link ? 'Available' : 'Coming soon' }}</mat-card-subtitle>
          </mat-card-header>
          <mat-card-content>
            <p>{{ tile.description }}</p>
          </mat-card-content>
          @if (tile.link) {
            <mat-card-actions align="end">
              <a mat-button [routerLink]="tile.link">Open</a>
            </mat-card-actions>
          }
        </mat-card>
      }
    </div>
  `,
  styles: `
    .heading { margin: 0 0 4px; font-size: 24px; font-weight: 500; }
    .sub { margin: 0 0 24px; color: #5f6368; }
    .grid { display: grid; gap: 16px; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); }
  `,
})
export class DashboardComponent {
  protected readonly auth = inject(AuthService);

  readonly tiles: Tile[] = [
    { icon: 'receipt_long', title: 'Orders', description: 'Filter orders, ship, deliver or cancel them.', link: '/orders' },
    { icon: 'inventory_2', title: 'Products', description: 'Search, create, edit and delete catalog products.', link: '/products' },
    { icon: 'category', title: 'Categories', description: 'Organise the catalog into categories.', link: '/categories' },
    { icon: 'warning_amber', title: 'Low stock', description: 'Products running out, with one-click restock.', link: '/inventory/low-stock' },
    { icon: 'payments', title: 'Payments', description: 'Captured money, failures and reconciliation issues.', link: '/payments' },
    { icon: 'group', title: 'Users', description: 'Customer and admin accounts.', link: '/users' },
    { icon: 'mail', title: 'Notifications', description: 'Every email sent, failed or retried.', link: '/notifications' },
  ];
}
