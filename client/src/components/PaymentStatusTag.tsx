import { Tag } from '@arco-design/web-react';

import type { PaymentStatus } from '../api/payments';
import { fr } from '../i18n/fr';

/**
 * Status is paired with text, never carried by color alone. Gold means the
 * receipt is waiting for an administrator, green an approved payment, red a
 * rejected one.
 */
const TAG_COLORS: Record<PaymentStatus, string> = {
  PENDING_REVIEW: 'gold',
  APPROVED: 'green',
  REJECTED: 'red',
};

export function PaymentStatusTag({ status }: { status: PaymentStatus }) {
  return <Tag color={TAG_COLORS[status]}>{fr.payments.statuses[status]}</Tag>;
}
