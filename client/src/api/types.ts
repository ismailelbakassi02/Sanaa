export type Role = 'STUDENT' | 'INSTRUCTOR' | 'TENANT_ADMIN' | 'SUPER_ADMIN';

export interface LoginPayload {
  tenantSlug: string;
  email: string;
  password: string;
}

export interface LoginResponse {
  accessToken: string;
  expiresIn: number;
}

export interface RegisterPayload {
  tenantSlug: string;
  email: string;
  password: string;
  fullName: string;
}

export interface RegisterResponse {
  id: number;
  email: string;
  fullName: string;
  tenantSlug: string;
  role: Role;
}

export interface MeResponse {
  id: number;
  email: string;
  fullName: string;
  role: Role;
  tenantId: string;
  tenantSlug: string;
}

export interface BrandingResponse {
  name: string;
  primaryColor: string;
  logoUrl?: string | null;
  registrationEnabled: boolean;
}

/** RFC 7807 body returned by the backend. Title and detail are English. */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  properties?: Record<string, unknown>;
}
