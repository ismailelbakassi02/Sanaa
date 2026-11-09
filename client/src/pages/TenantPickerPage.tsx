import { Button, Card, Form, Input } from '@arco-design/web-react';
import { useState } from 'react';

import { ThemeToggle } from '../components/ThemeToggle';
import { fr } from '../i18n/fr';
import { normalizeTenantSlug } from '../tenant/detectTenant';

interface TenantPickerPageProps {
  onSelect: (slug: string) => void;
}

interface TenantPickerValues {
  slug?: string;
}

/**
 * Shown when neither the hostname, ?tenant= nor VITE_DEFAULT_TENANT gives a
 * usable organisation. No API call is made from this screen.
 */
export function TenantPickerPage({ onSelect }: TenantPickerPageProps) {
  const [form] = Form.useForm<TenantPickerValues>();
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = () => {
    const slug = normalizeTenantSlug(form.getFieldValue('slug'));
    if (!slug) {
      setError(fr.tenantPicker.invalidSlug);
      return;
    }
    setError(null);
    onSelect(slug);
  };

  return (
    <div className="sanaa-center-page">
      <ThemeToggle floating />
      <div className="sanaa-picker">
        <Card bordered className="sanaa-picker__card" bodyStyle={{ padding: 32 }}>
          <h1 className="sanaa-title-xl">{fr.tenantPicker.title}</h1>
          <p className="sanaa-text sanaa-muted sanaa-picker__subtitle">
            {fr.tenantPicker.subtitle}
          </p>

          <Form form={form} layout="vertical" size="large" onSubmit={handleSubmit}>
            <Form.Item label={fr.tenantPicker.slugLabel} field="slug">
              <Input
                placeholder={fr.tenantPicker.slugPlaceholder}
                autoComplete="off"
                autoFocus
                onChange={() => {
                  if (error) {
                    setError(null);
                  }
                }}
              />
            </Form.Item>
            {error ? (
              <p className="sanaa-field-error" role="alert">
                {error}
              </p>
            ) : null}
            <Button type="primary" htmlType="submit" long className="sanaa-picker__submit">
              {fr.tenantPicker.submit}
            </Button>
          </Form>
        </Card>
      </div>
    </div>
  );
}
