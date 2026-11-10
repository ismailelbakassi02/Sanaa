import { Form, Input, InputNumber } from '@arco-design/web-react';
import type { ReactNode } from 'react';

import { fr } from '../i18n/fr';
import { hasAtMostTwoDecimals } from '../lib/price';

export interface CourseFormValues {
  title: string;
  description?: string;
  price: number;
}

const MAX_PRICE = 1_000_000;

/**
 * The rules mirror the backend constraints: title 3-120, description up to
 * 4000, price between 0 and 1 000 000 with at most two decimals.
 */
const titleRules = [
  { required: true, message: fr.common.required },
  { minLength: 3, maxLength: 120, message: fr.editor.validation.titleLength },
];

const descriptionRules = [{ maxLength: 4000, message: fr.editor.validation.descriptionMax }];

const priceRules = [
  { required: true, message: fr.editor.validation.priceRequired },
  {
    validator: (value: number | undefined, callback: (error?: ReactNode) => void) => {
      if (value === undefined || value === null || Number.isNaN(value)) {
        callback(fr.editor.validation.priceRequired);
        return;
      }
      if (value < 0 || value > MAX_PRICE) {
        callback(fr.editor.validation.priceRange);
        return;
      }
      if (!hasAtMostTwoDecimals(value)) {
        callback(fr.editor.validation.priceDecimals);
        return;
      }
      callback();
    },
  },
];

interface CourseFieldsProps {
  disabled?: boolean;
}

/** Shared title, description and price fields of the create and edit forms. */
export function CourseFields({ disabled = false }: CourseFieldsProps) {
  return (
    <>
      <Form.Item label={fr.editor.titleLabel} field="title" rules={titleRules}>
        <Input placeholder={fr.editor.titlePlaceholder} disabled={disabled} maxLength={120} />
      </Form.Item>
      <Form.Item label={fr.editor.descriptionLabel} field="description" rules={descriptionRules}>
        <Input.TextArea
          placeholder={fr.editor.descriptionPlaceholder}
          disabled={disabled}
          autoSize={{ minRows: 4, maxRows: 10 }}
        />
      </Form.Item>
      <Form.Item label={fr.editor.priceLabel} field="price" rules={priceRules}>
        <InputNumber
          min={0}
          max={MAX_PRICE}
          step={10}
          precision={2}
          disabled={disabled}
          className="sanaa-price-input"
        />
      </Form.Item>
      <Form.Item label={fr.editor.currencyLabel}>
        <Input value={fr.editor.currencyValue} disabled className="sanaa-currency-input" />
      </Form.Item>
    </>
  );
}
