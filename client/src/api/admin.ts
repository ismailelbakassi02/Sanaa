import { http } from './http';
import type {
  AdminAuditEntry,
  AdminPublisherSummary,
  AdminUserSummary,
  PageResponse,
  PublisherStatus,
  UpdateModerationStatusPayload,
  UserStatus,
} from './types';

export interface AdminPageParams {
  q?: string;
  /** Zero-based, as the backend expects. */
  page: number;
  size: number;
}

export interface AuditPageParams extends AdminPageParams {
  actor?: string;
  action?: string;
}

export async function fetchAdminUsers(
  params: AdminPageParams,
): Promise<PageResponse<AdminUserSummary>> {
  const response = await http.get<PageResponse<AdminUserSummary>>('/api/admin/users', { params });
  return response.data;
}

export async function updateAdminUserStatus(
  id: number,
  status: UserStatus,
  reason: string,
): Promise<AdminUserSummary> {
  const payload: UpdateModerationStatusPayload<UserStatus> = { status, reason };
  const response = await http.patch<AdminUserSummary>(`/api/admin/users/${id}/status`, payload);
  return response.data;
}

export async function fetchAdminPublishers(
  params: AdminPageParams,
): Promise<PageResponse<AdminPublisherSummary>> {
  const response = await http.get<PageResponse<AdminPublisherSummary>>('/api/admin/publishers', {
    params,
  });
  return response.data;
}

export async function updateAdminPublisherStatus(
  id: string,
  status: PublisherStatus,
  reason: string,
): Promise<AdminPublisherSummary> {
  const payload: UpdateModerationStatusPayload<PublisherStatus> = { status, reason };
  const response = await http.patch<AdminPublisherSummary>(
    `/api/admin/publishers/${encodeURIComponent(id)}/status`,
    payload,
  );
  return response.data;
}

export async function fetchAuthAudit(
  params: AuditPageParams,
): Promise<PageResponse<AdminAuditEntry>> {
  const response = await http.get<PageResponse<AdminAuditEntry>>('/api/admin/audit/auth', {
    params,
  });
  return response.data;
}
