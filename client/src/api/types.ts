export type Role = 'STUDENT' | 'INSTRUCTOR' | 'TENANT_ADMIN' | 'SUPER_ADMIN';
export type PublisherType = 'INDIVIDUAL' | 'ORGANIZATION';
export type MembershipRole = 'TENANT_ADMIN' | 'INSTRUCTOR';
export type UserStatus = 'ACTIVE' | 'DISABLED';
export type MembershipStatus = 'ACTIVE' | 'DISABLED';
export type PublisherStatus = 'ACTIVE' | 'SUSPENDED';

export interface LoginPayload {
  email: string;
  password: string;
}

export interface LoginResponse {
  accessToken: string;
  expiresIn: number;
}

export interface RegisterPayload {
  email: string;
  password: string;
  fullName: string;
}

export interface RegisterResponse {
  id: number;
  email: string;
  fullName: string;
  role: Role;
}

/**
 * Mirrors auth-service MeResponse. The tenant fields are null for learners and
 * for the platform administrator, who have no publisher. Claims are only a
 * display hint: the server remains the authority for every permission.
 */
export interface MeResponse {
  id: number;
  email: string;
  fullName: string;
  role: Role;
  tenantId: string | null;
  tenantSlug: string | null;
  tenantType: PublisherType | null;
  mustChangePassword: boolean;
}

export interface ChangePasswordPayload {
  currentPassword: string;
  newPassword: string;
}

export interface ProfileResponse {
  userId: number;
  email: string;
  fullName: string;
  headline: string | null;
  bio: string | null;
  websiteUrl: string | null;
  instagramUrl: string | null;
  linkedinUrl: string | null;
  youtubeUrl: string | null;
  avatarImageId: string | null;
}

export interface UpdateProfilePayload {
  fullName?: string;
  headline?: string;
  bio?: string;
  websiteUrl?: string;
  instagramUrl?: string;
  linkedinUrl?: string;
  youtubeUrl?: string;
}

export interface ImageUploadResponse {
  id: string;
  kind: 'avatar' | 'logo';
  contentType: string;
  sizeBytes: number;
}

export interface SlugAvailabilityResponse {
  slug: string;
  available: boolean;
}

export interface CreatePublisherPayload {
  publisherType: PublisherType;
  publisherName: string;
  slug: string;
  primaryColor: string;
  contactEmail?: string;
  ownerFullName?: string;
  ownerEmail?: string;
  password?: string;
}

export interface CreatedPublisherResponse {
  slug: string;
  name: string;
  type: PublisherType;
}

export interface PublisherProfileResponse {
  slug: string;
  name: string;
  type: PublisherType;
  status: PublisherStatus;
  headline: string | null;
  description: string | null;
  websiteUrl: string | null;
  instagramUrl: string | null;
  linkedinUrl: string | null;
  youtubeUrl: string | null;
  logoImageId: string | null;
  primaryColor: string | null;
  contactEmail: string | null;
}

export interface UpdatePublisherPayload {
  name: string;
  headline?: string;
  description?: string;
  websiteUrl?: string;
  instagramUrl?: string;
  linkedinUrl?: string;
  youtubeUrl?: string;
  primaryColor: string;
  contactEmail?: string;
}

export interface PublicTeamMember {
  userId: number;
  fullName: string;
  headline: string | null;
  avatarImageId: string | null;
}

export interface PublicPublisherResponse {
  slug: string;
  name: string;
  type: PublisherType;
  headline: string | null;
  description: string | null;
  websiteUrl: string | null;
  instagramUrl: string | null;
  linkedinUrl: string | null;
  youtubeUrl: string | null;
  logoImageId: string | null;
  primaryColor: string | null;
  team: PublicTeamMember[];
}

export interface PublicInstructorResponse {
  userId: number;
  fullName: string;
  headline: string | null;
  bio: string | null;
  websiteUrl: string | null;
  instagramUrl: string | null;
  linkedinUrl: string | null;
  youtubeUrl: string | null;
  avatarImageId: string | null;
  publisherSlug: string;
  publisherName: string;
}

export interface TeamMemberResponse {
  userId: number;
  email: string;
  fullName: string;
  role: MembershipRole;
  status: MembershipStatus;
  headline: string | null;
  avatarImageId: string | null;
  mustChangePassword: boolean;
  createdAt: string;
}

export interface AddTeamMemberPayload {
  fullName: string;
  email: string;
  role: MembershipRole;
  temporaryPassword: string;
}

export interface ResetTeamPasswordPayload {
  temporaryPassword: string;
}

export interface UpdateTeamMemberStatusPayload {
  status: MembershipStatus;
}

export interface AdminUserSummary {
  id: number;
  email: string;
  fullName: string;
  platformRole: string;
  status: UserStatus;
  mustChangePassword: boolean;
  tenantId: string | null;
  tenantSlug: string | null;
  publisherRole: MembershipRole | null;
  createdAt: string;
}

export interface AdminPublisherSummary {
  id: string;
  slug: string;
  name: string;
  type: PublisherType;
  status: PublisherStatus;
  primaryColor: string | null;
  contactEmail: string | null;
  headline: string | null;
  activeMembers: number;
  createdAt: string;
}

export interface AdminAuditEntry {
  id: number;
  actorEmail: string;
  action: string;
  targetType: string;
  targetId: string | null;
  tenantId: string | null;
  reason: string | null;
  createdAt: string;
}

export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface UpdateModerationStatusPayload<TStatus extends UserStatus | PublisherStatus = UserStatus | PublisherStatus> {
  status: TStatus;
  reason: string;
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
