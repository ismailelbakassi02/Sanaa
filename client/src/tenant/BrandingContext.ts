import { createContext, useContext } from 'react';

import type { BrandingResponse } from '../api/types';

export interface BrandingState {
  slug: string;
  branding: BrandingResponse;
}

export const BrandingContext = createContext<BrandingState | null>(null);

export function useBranding(): BrandingState {
  const value = useContext(BrandingContext);
  if (!value) {
    throw new Error('useBranding must be used inside BrandingGate');
  }
  return value;
}
