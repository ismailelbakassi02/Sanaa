import {
  Button,
  Drawer,
  Message,
  Modal,
  Radio,
  Upload,
  type UploadProps,
} from '@arco-design/web-react';
import { IconDelete, IconUpload } from '@arco-design/web-react/icon';
import { useState } from 'react';

import type { PaymentMethod } from '../api/payments';
import { useMediaQuery } from '../hooks/useMediaQuery';
import { useSubmitPayment } from '../hooks/payments';
import { formatFileSize, formatPrice } from '../i18n/format';
import { fr } from '../i18n/fr';
import { paymentErrorMessage } from '../lib/paymentErrors';
import {
  RECEIPT_ACCEPT,
  receiptFileMessage,
  validateReceiptFile,
} from '../lib/receiptFile';

type UploadItem = NonNullable<UploadProps['fileList']>[number];

interface BuyCourseDrawerProps {
  visible: boolean;
  course: {
    id: number;
    title: string;
    price: number;
    currency: string;
  };
  onClose: () => void;
}

/**
 * Payment submission. The amount and title are displayed for confirmation but
 * never sent: payment-service reads them from course-service. The server also
 * verifies the receipt, so the client-side checks are only a fast feedback.
 */
export function BuyCourseDrawer({ visible, course, onClose }: BuyCourseDrawerProps) {
  const isDesktop = useMediaQuery('(min-width: 768px)');
  const mutation = useSubmitPayment();
  const [method, setMethod] = useState<PaymentMethod>('BANK_WIRE');
  const [file, setFile] = useState<File | null>(null);
  const [fileList, setFileList] = useState<UploadItem[]>([]);
  const [fileError, setFileError] = useState<string | null>(null);
  const [submitError, setSubmitError] = useState<string | null>(null);

  const reset = () => {
    setMethod('BANK_WIRE');
    setFile(null);
    setFileList([]);
    setFileError(null);
    setSubmitError(null);
  };

  const close = () => {
    if (mutation.isPending) {
      return;
    }
    reset();
    onClose();
  };

  const acceptFile = (selected: File | undefined) => {
    if (!selected) {
      setFile(null);
      setFileList([]);
      setFileError(fr.payments.errors.emptyFile);
      return;
    }
    const problem = validateReceiptFile(selected);
    if (problem) {
      setFile(null);
      setFileList([]);
      setFileError(receiptFileMessage(problem));
      return;
    }
    setFile(selected);
    setFileList([
      {
        uid: `${selected.name}-${selected.lastModified}`,
        name: selected.name,
        status: 'done',
        originFile: selected,
      },
    ]);
    setFileError(null);
    setSubmitError(null);
  };

  const clearFile = () => {
    setFile(null);
    setFileList([]);
    setFileError(null);
    setSubmitError(null);
  };

  const submit = () => {
    if (!file) {
      setFileError(fr.payments.errors.emptyFile);
      return;
    }
    const problem = validateReceiptFile(file);
    if (problem) {
      setFileError(receiptFileMessage(problem));
      return;
    }
    setSubmitError(null);
    mutation.mutate(
      { courseId: course.id, method, receipt: file },
      {
        onSuccess: () => {
          Message.success(fr.payments.buy.success);
          reset();
          onClose();
        },
        onError: (error) => setSubmitError(paymentErrorMessage(error)),
      },
    );
  };

  const form = (
    <div className="sanaa-buy">
      <div className="sanaa-buy__course">
        <p className="sanaa-caption">{fr.payments.buy.courseLabel}</p>
        <p className="sanaa-title-md">{course.title}</p>
      </div>

      <div className="sanaa-buy__price">
        <p className="sanaa-caption">{fr.payments.buy.priceLabel}</p>
        <p className="sanaa-price">{formatPrice(course.price, course.currency)}</p>
      </div>

      <section className="sanaa-buy__section">
        <h3 className="sanaa-title-sm">{fr.payments.buy.methodLabel}</h3>
        <Radio.Group
          direction="vertical"
          className="sanaa-method-group"
          value={method}
          onChange={(value) => setMethod(value as PaymentMethod)}
        >
          <Radio value="BANK_WIRE">
            <span className="sanaa-method-option">
              <span className="sanaa-method-option__title">
                {fr.payments.methods.BANK_WIRE}
              </span>
              <span className="sanaa-caption">
                {fr.payments.methodInstructions.BANK_WIRE}
              </span>
            </span>
          </Radio>
          <Radio value="CASHPLUS">
            <span className="sanaa-method-option">
              <span className="sanaa-method-option__title">
                {fr.payments.methods.CASHPLUS}
              </span>
              <span className="sanaa-caption">
                {fr.payments.methodInstructions.CASHPLUS}
              </span>
            </span>
          </Radio>
        </Radio.Group>
      </section>

      <section className="sanaa-buy__section">
        <h3 className="sanaa-title-sm">{fr.payments.buy.receiptLabel}</h3>
        <p className="sanaa-caption sanaa-buy__formats">
          {fr.payments.buy.acceptedFormats}
        </p>
        <Upload
          accept={RECEIPT_ACCEPT}
          autoUpload={false}
          multiple={false}
          showUploadList={false}
          fileList={fileList}
          onChange={(list) => acceptFile(list[list.length - 1]?.originFile)}
          onExceedLimit={(files) => acceptFile(files[files.length - 1])}
        >
          <Button icon={<IconUpload />}>{fr.payments.buy.chooseFile}</Button>
        </Upload>
        {file ? (
          <div className="sanaa-receipt">
            <div className="sanaa-receipt__meta">
              <span className="sanaa-receipt__name">{file.name}</span>
              <span className="sanaa-caption">{formatFileSize(file.size)}</span>
            </div>
            <Button type="text" size="small" icon={<IconDelete />} onClick={clearFile}>
              {fr.payments.buy.removeFile}
            </Button>
          </div>
        ) : null}
        {fileError ? (
          <p className="sanaa-field-error" role="alert">
            {fileError}
          </p>
        ) : null}
      </section>

      {submitError ? (
        <p className="sanaa-field-error" role="alert">
          {submitError}
        </p>
      ) : null}
    </div>
  );

  const footer = (
    <div className="sanaa-buy__footer">
      <Button type="primary" loading={mutation.isPending} onClick={submit}>
        {mutation.isPending ? fr.payments.buy.submitting : fr.payments.buy.submit}
      </Button>
      <Button onClick={close} disabled={mutation.isPending}>
        {fr.payments.buy.cancel}
      </Button>
    </div>
  );

  if (isDesktop) {
    return (
      <Modal
        visible={visible}
        title={fr.payments.buy.title}
        onCancel={close}
        footer={footer}
        unmountOnExit
      >
        {form}
      </Modal>
    );
  }

  return (
    <Drawer
      visible={visible}
      title={fr.payments.buy.title}
      onCancel={close}
      footer={footer}
      placement="right"
      width="100%"
      unmountOnExit
    >
      {form}
    </Drawer>
  );
}
