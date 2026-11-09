import { Spin } from '@arco-design/web-react';
import type { ReactNode } from 'react';

interface LoadingScreenProps {
  message: string;
}

export function LoadingScreen({ message }: LoadingScreenProps) {
  return (
    <div className="sanaa-loading" role="status" aria-live="polite">
      <Spin size={32} />
      <p className="sanaa-loading__label">{message}</p>
    </div>
  );
}

export function FullHeightCenter({ children }: { children: ReactNode }) {
  return <div className="sanaa-center-page">{children}</div>;
}
