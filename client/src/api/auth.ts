import axios from 'axios';

import { http } from './http';
import type {
  BrandingResponse,
  LoginPayload,
  LoginResponse,
  MeResponse,
  RegisterPayload,
  RegisterResponse,
} from './types';

export async function login(payload: LoginPayload): Promise<LoginResponse> {
  const response = await http.post<LoginResponse>('/api/auth/login', payload);
  return response.data;
}

export async function register(payload: RegisterPayload): Promise<RegisterResponse> {
  const response = await http.post<RegisterResponse>('/api/auth/register', payload);
  return response.data;
}

export async function fetchMe(): Promise<MeResponse> {
  const response = await http.get<MeResponse>('/api/auth/me');
  return response.data;
}

export async function logout(): Promise<void> {
  await http.post('/api/auth/logout');
}

export async function fetchBranding(slug: string): Promise<BrandingResponse> {
  const response = await http.get<BrandingResponse>(
    `/api/auth/tenants/${encodeURIComponent(slug)}/branding`,
  );
  return response.data;
}

/**
 * Session restore uses the raw axios call: it runs before any interceptor and
 * a 401 here simply means "no session", not "refresh the session".
 */
export async function restoreSession(): Promise<string> {
  const response = await axios.post<LoginResponse>('/api/auth/refresh', null, {
    withCredentials: true,
  });
  return response.data.accessToken;
}
