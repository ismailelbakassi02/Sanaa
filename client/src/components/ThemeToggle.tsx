import { Button, Tooltip } from '@arco-design/web-react';
import { IconMoonFill, IconSunFill } from '@arco-design/web-react/icon';

import { fr } from '../i18n/fr';
import { useThemeStore } from '../store/theme';

interface ThemeToggleProps {
  floating?: boolean;
}

export function ThemeToggle({ floating = false }: ThemeToggleProps) {
  const mode = useThemeStore((state) => state.mode);
  const toggle = useThemeStore((state) => state.toggle);
  const label = mode === 'dark' ? fr.theme.toLight : fr.theme.toDark;

  return (
    <span className={floating ? 'sanaa-theme-toggle sanaa-theme-toggle--floating' : undefined}>
      <Tooltip content={label}>
        <Button
          type="text"
          shape="circle"
          aria-label={label}
          icon={mode === 'dark' ? <IconSunFill /> : <IconMoonFill />}
          onClick={toggle}
        />
      </Tooltip>
    </span>
  );
}
