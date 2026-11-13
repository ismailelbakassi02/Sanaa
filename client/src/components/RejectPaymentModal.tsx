import { Button, Input, Modal } from '@arco-design/web-react';
import { useEffect, useState } from 'react';

import { fr } from '../i18n/fr';

interface RejectPaymentModalProps {
  visible: boolean;
  studentName: string;
  courseTitle: string;
  submitting: boolean;
  onCancel: () => void;
  onSubmit: (reason: string) => void;
}

const MIN_REASON_LENGTH = 3;
const MAX_REASON_LENGTH = 500;

function validateReason(reason: string): string | null {
  const length = reason.trim().length;
  if (length < MIN_REASON_LENGTH) {
    return fr.payments.admin.rejectTooShort;
  }
  if (length > MAX_REASON_LENGTH) {
    return fr.payments.admin.rejectTooLong;
  }
  return null;
}

/** Rejection is irreversible, so the reason is required and bounded. */
export function RejectPaymentModal({
  visible,
  studentName,
  courseTitle,
  submitting,
  onCancel,
  onSubmit,
}: RejectPaymentModalProps) {
  const [reason, setReason] = useState('');
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!visible) {
      setReason('');
      setError(null);
    }
  }, [visible]);

  const submit = () => {
    const problem = validateReason(reason);
    if (problem) {
      setError(problem);
      return;
    }
    setError(null);
    onSubmit(reason.trim());
  };

  const footer = (
    <div className="sanaa-form-actions">
      <Button type="primary" status="danger" loading={submitting} onClick={submit}>
        {fr.payments.admin.confirmReject}
      </Button>
      <Button onClick={onCancel} disabled={submitting}>
        {fr.payments.admin.cancel}
      </Button>
    </div>
  );

  return (
    <Modal
      visible={visible}
      title={fr.payments.admin.rejectTitle}
      onCancel={onCancel}
      footer={footer}
      unmountOnExit
    >
      <div className="sanaa-reject">
        <p className="sanaa-text sanaa-text--secondary">
          {fr.payments.admin.rejectIntro(studentName, courseTitle)}
        </p>
        <Input.TextArea
          aria-label={fr.payments.admin.rejectLabel}
          placeholder={fr.payments.admin.rejectPlaceholder}
          value={reason}
          onChange={(value) => {
            setReason(value);
            if (error) {
              setError(null);
            }
          }}
          maxLength={MAX_REASON_LENGTH}
          autoFocus
        />
        <div className="sanaa-reject__meta">
          <span className="sanaa-caption">
            {fr.payments.admin.rejectCounter(reason.length)}
          </span>
        </div>
        {error ? (
          <p className="sanaa-field-error" role="alert">
            {error}
          </p>
        ) : null}
      </div>
    </Modal>
  );
}
