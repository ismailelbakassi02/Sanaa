import { http } from './http';
import type {
  AddTeamMemberPayload,
  ResetTeamPasswordPayload,
  TeamMemberResponse,
  UpdateTeamMemberStatusPayload,
} from './types';

const BASE = '/api/auth/team';

export async function fetchTeam(): Promise<TeamMemberResponse[]> {
  const response = await http.get<TeamMemberResponse[]>(BASE);
  return response.data;
}

export async function addTeamMember(payload: AddTeamMemberPayload): Promise<TeamMemberResponse> {
  const response = await http.post<TeamMemberResponse>(BASE, payload);
  return response.data;
}

export async function updateTeamMemberStatus(
  userId: number,
  payload: UpdateTeamMemberStatusPayload,
): Promise<TeamMemberResponse> {
  const response = await http.patch<TeamMemberResponse>(`${BASE}/${userId}/status`, payload);
  return response.data;
}

export async function resetTeamMemberPassword(
  userId: number,
  payload: ResetTeamPasswordPayload,
): Promise<void> {
  await http.post(`${BASE}/${userId}/reset-password`, payload);
}
