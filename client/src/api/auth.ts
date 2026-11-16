import axios from 'axios';

import { http } from './http';
import type {
  ChangePasswordPayload,
  GoogleLinkPayload,
  GoogleOAuthStatusResponse,
  LoginPayload,
  LoginResponse,
  MeResponse,
  RegisterPayload,
  RegisterResponse,
  SetPasswordPayload,
} from './types';

export async function login(payload: LoginPayload): Promise<LoginResponse> {
  const response = await http.post<LoginResponse>('/api/auth/login', payload);
  return response.data;
}

export async function register(payload: RegisterPayload): Promise<RegisterResponse> {
  const response = await http.post<RegisterResponse>('/api/auth/register', payload);
  return response.data;
}

/**
 * The auth service omits null fields from its JSON, so a learner arrives
 * without tenantId/tenantSlug/tenantType. Callers rely on `null` to mean "no
 * publisher"; `undefined` would make `tenantSlug !== null` true and show a
 * broken studio link. Normalise once, at the API boundary.
 */
function normalizeMe(me: MeResponse): MeResponse {
  return {
    ...me,
    tenantId: me.tenantId ?? null,
    tenantSlug: me.tenantSlug ?? null,
    tenantType: me.tenantType ?? null,
    mustChangePassword: me.mustChangePassword ?? false,
    hasPassword: me.hasPassword ?? true,
  };
}

export async function fetchMe(): Promise<MeResponse> {
  const response = await http.get<MeResponse>('/api/auth/me');
  return normalizeMe(response.data);
}

export async function logout(): Promise<void> {
  await http.post('/api/auth/logout');
}

export async function changePassword(payload: ChangePasswordPayload): Promise<LoginResponse> {
  const response = await http.post<LoginResponse>('/api/auth/password', payload);
  return response.data;
}

export async function setInitialPassword(payload: SetPasswordPayload): Promise<LoginResponse> {
  const response = await http.post<LoginResponse>('/api/auth/password/set', payload);
  return response.data;
}

export async function fetchGoogleOAuthStatus(): Promise<GoogleOAuthStatusResponse> {
  const response = await http.get<GoogleOAuthStatusResponse>('/api/auth/oauth2/google/status');
  return response.data;
}

export async function confirmGoogleLink(payload: GoogleLinkPayload): Promise<LoginResponse> {
  const response = await http.post<LoginResponse>('/api/auth/oauth2/google/link', payload);
  return response.data;
}

/**
 * Session restore uses the raw axios call: it runs before any interceptor and
 * a 401 here simply means "no session", not "refresh the session". It is also
 * called after opening a studio so the new publisher claims replace the
 * learner claims on the access token.
 */
export async function restoreSession(): Promise<string> {
  const response = await axios.post<LoginResponse>('/api/auth/refresh', null, {
    withCredentials: true,
  });
  return response.data.accessToken;
}
