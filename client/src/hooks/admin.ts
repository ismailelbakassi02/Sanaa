import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import {
  fetchAdminPublishers,
  fetchAdminUsers,
  fetchAuthAudit,
  updateAdminPublisherStatus,
  updateAdminUserStatus,
  type AdminPageParams,
  type AuditPageParams,
} from '../api/admin';
import type {
  AdminAuditEntry,
  AdminPublisherSummary,
  AdminUserSummary,
  PageResponse,
  PublisherStatus,
  UserStatus,
} from '../api/types';

export const adminKeys = {
  users: (params: AdminPageParams) => ['admin', 'users', params] as const,
  publishers: (params: AdminPageParams) => ['admin', 'publishers', params] as const,
  audit: (params: AuditPageParams) => ['admin', 'audit', params] as const,
};

export function useAdminUsers(params: AdminPageParams, enabled = true) {
  return useQuery<PageResponse<AdminUserSummary>>({
    queryKey: adminKeys.users(params),
    queryFn: () => fetchAdminUsers(params),
    enabled,
    retry: false,
    placeholderData: (previous) => previous,
  });
}

export function useAdminPublishers(params: AdminPageParams, enabled = true) {
  return useQuery<PageResponse<AdminPublisherSummary>>({
    queryKey: adminKeys.publishers(params),
    queryFn: () => fetchAdminPublishers(params),
    enabled,
    retry: false,
    placeholderData: (previous) => previous,
  });
}

export function useAuthAudit(params: AuditPageParams, enabled = true) {
  return useQuery<PageResponse<AdminAuditEntry>>({
    queryKey: adminKeys.audit(params),
    queryFn: () => fetchAuthAudit(params),
    enabled,
    retry: false,
    placeholderData: (previous) => previous,
  });
}

export function useUpdateAdminUserStatus() {
  const queryClient = useQueryClient();
  return useMutation<
    AdminUserSummary,
    unknown,
    { id: number; status: UserStatus; reason: string }
  >({
    mutationFn: ({ id, status, reason }) => updateAdminUserStatus(id, status, reason),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['admin', 'users'] });
      void queryClient.invalidateQueries({ queryKey: ['admin', 'audit'] });
    },
  });
}

export function useUpdateAdminPublisherStatus() {
  const queryClient = useQueryClient();
  return useMutation<
    AdminPublisherSummary,
    unknown,
    { id: string; status: PublisherStatus; reason: string }
  >({
    mutationFn: ({ id, status, reason }) => updateAdminPublisherStatus(id, status, reason),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['admin', 'publishers'] });
      void queryClient.invalidateQueries({ queryKey: ['admin', 'audit'] });
    },
  });
}
