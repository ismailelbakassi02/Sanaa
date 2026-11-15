import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { fetchMyProfile, updateMyProfile, uploadImage } from '../api/profile';
import type { ImageUploadResponse, ProfileResponse, UpdateProfilePayload } from '../api/types';

export const profileKeys = {
  me: ['profile', 'me'] as const,
};

export function useMyProfile() {
  return useQuery<ProfileResponse>({
    queryKey: profileKeys.me,
    queryFn: fetchMyProfile,
    retry: false,
  });
}

export function useUpdateMyProfile() {
  const queryClient = useQueryClient();
  return useMutation<ProfileResponse, unknown, UpdateProfilePayload>({
    mutationFn: (payload) => updateMyProfile(payload),
    onSuccess: (profile) => {
      queryClient.setQueryData(profileKeys.me, profile);
    },
  });
}

export function useUploadImage() {
  return useMutation<ImageUploadResponse, unknown, { kind: 'avatar' | 'logo'; file: File }>({
    mutationFn: ({ kind, file }) => uploadImage(kind, file),
  });
}
