import enUS from '@arco-design/web-react/es/locale/en-US';
import frFR from '@arco-design/web-react/es/locale/fr-FR';
import type { Locale } from '@arco-design/web-react/es/locale/interface';

/**
 * Arco 2.66.18 requires ColorPicker keys in the Locale type, but its fr-FR
 * file predates them. The application does not use ColorPicker; the English
 * fallback only completes the type without changing any visible form text.
 */
export const arcoLocale: Locale = {
  ...frFR,
  ColorPicker: enUS.ColorPicker,
};
