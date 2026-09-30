import { BreakpointObserver, Breakpoints } from '@angular/cdk/layout';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatMenuModule } from '@angular/material/menu';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { map } from 'rxjs';

import { AuthService } from '../core/auth/auth.service';

interface NavItem {
  path: string;
  icon: string;
  label: string;
}

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatButtonModule,
    MatIconModule,
    MatListModule,
    MatMenuModule,
    MatSidenavModule,
    MatToolbarModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <mat-sidenav-container class="shell">
      <mat-sidenav #drawer class="sidenav" [mode]="isHandset() ? 'over' : 'side'" [opened]="!isHandset()">
        <div class="brand">
          <mat-icon>storefront</mat-icon>
          <span>ShopSphere</span>
        </div>
        <mat-nav-list>
          @for (item of navItems; track item.path) {
            <a
              mat-list-item
              [routerLink]="item.path"
              routerLinkActive
              #rla="routerLinkActive"
              [activated]="rla.isActive"
              (click)="isHandset() && drawer.close()"
            >
              <mat-icon matListItemIcon>{{ item.icon }}</mat-icon>
              <span matListItemTitle>{{ item.label }}</span>
            </a>
          }
        </mat-nav-list>
      </mat-sidenav>

      <mat-sidenav-content>
        <mat-toolbar class="toolbar">
          @if (isHandset()) {
            <button mat-icon-button type="button" (click)="drawer.toggle()" aria-label="Toggle navigation">
              <mat-icon>menu</mat-icon>
            </button>
          }
          <span>Admin console</span>
          <span class="spacer"></span>
          @if (user(); as u) {
            <button mat-button type="button" [matMenuTriggerFor]="userMenu" data-testid="user-menu">
              <mat-icon>account_circle</mat-icon>
              {{ u.email }}
            </button>
            <mat-menu #userMenu="matMenu">
              <button mat-menu-item type="button" [disabled]="true">
                <mat-icon>verified_user</mat-icon>
                <span>{{ u.role }}</span>
              </button>
              <button mat-menu-item type="button" (click)="logout()" data-testid="logout">
                <mat-icon>logout</mat-icon>
                <span>Sign out</span>
              </button>
            </mat-menu>
          }
        </mat-toolbar>

        <main class="content">
          <router-outlet />
        </main>
      </mat-sidenav-content>
    </mat-sidenav-container>
  `,
  styles: `
    :host { display: block; height: 100vh; }
    .shell { height: 100%; }
    .sidenav { width: 240px; }
    .brand { display: flex; align-items: center; gap: 8px; padding: 20px 16px; font-size: 18px; font-weight: 500; }
    .toolbar { position: sticky; top: 0; z-index: 2; background: #ffffff; border-bottom: 1px solid #e3e5ec; }
    .content { padding: 24px; box-sizing: border-box; }
  `,
})
export class ShellComponent {
  private readonly auth = inject(AuthService);

  readonly user = this.auth.currentUser;
  readonly isHandset = toSignal(
    inject(BreakpointObserver)
      .observe(Breakpoints.Handset)
      .pipe(map((result) => result.matches)),
    { initialValue: false },
  );

  readonly navItems: NavItem[] = [
    { path: '/dashboard', icon: 'dashboard', label: 'Dashboard' },
    { path: '/products', icon: 'inventory_2', label: 'Products' },
    { path: '/categories', icon: 'category', label: 'Categories' },
  ];

  logout(): void {
    this.auth.logout();
  }
}
