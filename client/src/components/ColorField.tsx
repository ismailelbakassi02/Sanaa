import { ColorPicker } from '@arco-design/web-react';

import { BRAND_COLOR } from '../lib/brand';
import { isHexColor } from '../lib/color';

interface ColorFieldProps {
  value?: string;
  onChange?: (value: string) => void;
  disabled?: boolean;
}

/**
 * Publisher color. The value is stored as #RRGGBB; Arco's color picker is used
 * instead of a bare input so the choice stays accessible and validated.
 */
export function ColorField({ value = BRAND_COLOR, onChange, disabled = false }: ColorFieldProps) {
  return (
    <div className="sanaa-color-field">
      <ColorPicker
        value={isHexColor(value) ? value : BRAND_COLOR}
        format="hex"
        showText
        disabled={disabled}
        onChange={(color) => {
          if (typeof color === 'string' && isHexColor(color) && onChange) {
            onChange(color.toUpperCase());
          }
        }}
      />
    </div>
  );
}
