import { Form, Input } from '@arco-design/web-react';
import type { ReactNode } from 'react';

import { fr } from '../i18n/fr';
import { URL_MAX_LENGTH, urlIssue } from '../lib/url';

export type LinkFieldName = 'websiteUrl' | 'instagramUrl' | 'linkedinUrl' | 'youtubeUrl';

interface LinkFieldsProps {
  disabled?: boolean;
  /** Prefix for the Form.Item names when embedded in a larger form. */
  prefix?: string;
}

const LINK_LABELS: { name: LinkFieldName; label: string; placeholder: string }[] = [
  { name: 'websiteUrl', label: fr.me.websiteLabel, placeholder: 'https://exemple.com' },
  {
    name: 'instagramUrl',
    label: fr.me.instagramLabel,
    placeholder: 'https://instagram.com/votre-studio',
  },
  {
    name: 'linkedinUrl',
    label: fr.me.linkedinLabel,
    placeholder: 'https://linkedin.com/in/vous',
  },
  {
    name: 'youtubeUrl',
    label: fr.me.youtubeLabel,
    placeholder: 'https://youtube.com/@votre-studio',
  },
];

function linkValidator(value: unknown, callback: (error?: ReactNode) => void) {
  const issue = urlIssue(typeof value === 'string' ? value : '');
  if (issue === 'tooLong') {
    callback(`Le lien doit contenir au plus ${URL_MAX_LENGTH} caractères.`);
    return;
  }
  if (issue === 'invalid') {
    callback('Utilisez une adresse HTTPS complète, sans identifiant incorporé.');
    return;
  }
  callback();
}

/** The four optional HTTPS links shared by the personal and studio profiles. */
export function LinkFields({ disabled = false, prefix }: LinkFieldsProps) {
  return (
    <>
      {LINK_LABELS.map((field) => (
        <Form.Item
          key={field.name}
          label={field.label}
          field={prefix ? `${prefix}.${field.name}` : field.name}
          rules={[{ validator: linkValidator }]}
        >
          <Input placeholder={field.placeholder} disabled={disabled} inputMode="url" />
        </Form.Item>
      ))}
      <p className="sanaa-text sanaa-muted san-helper">{fr.me.linkHint}</p>
    </>
  );
}
