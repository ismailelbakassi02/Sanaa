import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import {
  addTeamMember,
  fetchTeam,
  resetTeamMemberPassword,
  updateTeamMemberStatus,
} from '../api/team';
import type {
  AddTeamMemberPayload,
  MembershipStatus,
  ResetTeamPasswordPayload,
  TeamMemberResponse,
} from '../api/types';

export const teamKeys = {
  list: ['team', 'list'] as const,
};

export function useTeam(enabled = true) {
  return useQuery<TeamMemberResponse[]>({
    queryKey: teamKeys.list,
    queryFn: fetchTeam,
    enabled,
    retry: false,
  });
}

export function useAddTeamMember() {
  const queryClient = useQueryClient();
  return useMutation<TeamMemberResponse, unknown, AddTeamMemberPayload>({
    mutationFn: (payload) => addTeamMember(payload),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: teamKeys.list });
    },
  });
}

export function useUpdateTeamMemberStatus() {
  const queryClient = useQueryClient();
  return useMutation<
    TeamMemberResponse,
    unknown,
    { userId: number; status: MembershipStatus }
  >({
    mutationFn: ({ userId, status }) => updateTeamMemberStatus(userId, { status }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: teamKeys.list });
    },
  });
}

export function useResetTeamMemberPassword() {
  return useMutation<void, unknown, { userId: number; payload: ResetTeamPasswordPayload }>({
    mutationFn: ({ userId, payload }) => resetTeamMemberPassword(userId, payload),
  });
}
