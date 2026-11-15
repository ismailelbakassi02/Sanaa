import { http } from './http';
import type { ImageUploadResponse, ProfileResponse, UpdateProfilePayload } from './types';

export async function fetchMyProfile(): Promise<ProfileResponse> {
  const response = await http.get<ProfileResponse>('/api/auth/profile');
  return response.data;
}

export async function updateMyProfile(payload: UpdateProfilePayload): Promise<ProfileResponse> {
  const response = await http.put<ProfileResponse>('/api/auth/profile', payload);
  return response.data;
}

/**
 * Uploads an avatar or a publisher logo. The backend detects the real type
 * from the magic bytes and rejects anything other than PNG or JPEG over 1 MB.
 */
export async function uploadImage(
  kind: 'avatar' | 'logo',
  file: File,
): Promise<ImageUploadResponse> {
  const formData = new FormData();
  formData.append('file', file, file.name);
  const response = await http.post<ImageUploadResponse>('/api/auth/images', formData, {
    params: { kind },
  });
  return response.data;
}

export function publicImageUrl(id: string | null | undefined): string | null {
  if (!id) {
    return null;
  }
  return `/api/public/images/${encodeURIComponent(id)}`;
}
