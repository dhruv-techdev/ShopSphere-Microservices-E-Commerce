export type UserRole = 'CUSTOMER' | 'ADMIN';
export type UserStatusFilter = 'ACTIVE' | 'DISABLED' | 'UNVERIFIED';

/** Mirrors user-service AdminUserResponse. */
export interface AdminUser {
  id: number;
  firstName: string;
  lastName: string;
  email: string;
  role: UserRole;
  enabled: boolean;
  emailVerified: boolean;
  emailVerifiedAt: string | null;
  createdAt: string | null;
  updatedAt: string | null;
}

export interface UserQuery {
  q: string | null;
  role: UserRole | null;
  status: UserStatusFilter | null;
  page: number;
  size: number;
}
