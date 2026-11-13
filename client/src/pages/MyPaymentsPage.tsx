import {
  Button,
  Empty,
  Message,
  Result,
  Skeleton,
  Table,
  Tooltip,
  Typography,
  type TableColumnProps,
} from '@arco-design/web-react';
import { IconEye } from '@arco-design/web-react/icon';
import { useState } from 'react';
import { Link } from 'react-router-dom';

import { messageForError } from '../api/messages';
import type { Payment } from '../api/payments';
import { PaymentStatusTag } from '../components/PaymentStatusTag';
import { PAYMENTS_PAGE_SIZE, useMyPayments, usePaymentReceipt } from '../hooks/payments';
import { formatDateTimeShort, formatPrice } from '../i18n/format';
import { fr } from '../i18n/fr';
import { useTenantSearch } from '../tenant/navigation';

export function MyPaymentsPage() {
  const tenantSearch = useTenantSearch();
  const [page, setPage] = useState(1);
  const query = useMyPayments(page);
  const receipt = usePaymentReceipt();

  const openReceipt = async (paymentId: number) => {
    try {
      await receipt.open(paymentId);
    } catch (error) {
      Message.error(messageForError(error));
    }
  };

  const columns: TableColumnProps<Payment>[] = [
    {
      title: fr.payments.myPayments.columnDate,
      dataIndex: 'createdAt',
      width: 150,
      render: (_, payment) => formatDateTimeShort(payment.createdAt),
    },
    {
      title: fr.payments.myPayments.columnCourse,
      dataIndex: 'courseTitle',
      width: 280,
      render: (_, payment) => (
        <Link
          to={`/courses/${payment.courseId}${tenantSearch}`}
          className="sanaa-link"
        >
          {payment.courseTitle}
        </Link>
      ),
    },
    {
      title: fr.payments.myPayments.columnAmount,
      dataIndex: 'amount',
      width: 120,
      align: 'right',
      render: (_, payment) => formatPrice(payment.amount, payment.currency),
    },
    {
      title: fr.payments.myPayments.columnMethod,
      dataIndex: 'method',
      width: 160,
      render: (_, payment) => fr.payments.methods[payment.method],
    },
    {
      title: fr.payments.myPayments.columnStatus,
      dataIndex: 'status',
      width: 190,
      render: (_, payment) => (
        <div className="sanaa-payment-status">
          <PaymentStatusTag status={payment.status} />
          {payment.status === 'REJECTED' && payment.rejectionReason ? (
            <Tooltip content={payment.rejectionReason}>
              <Button type="text" size="mini" className="sanaa-payment-reason">
                {fr.payments.myPayments.reason}
              </Button>
            </Tooltip>
          ) : null}
        </div>
      ),
    },
    {
      title: fr.payments.myPayments.columnActions,
      width: 250,
      align: 'right',
      render: (_, payment) => (
        <div className="sanaa-payment-actions">
          <Button
            type="text"
            size="small"
            icon={<IconEye />}
            loading={receipt.openingId === payment.id}
            onClick={() => void openReceipt(payment.id)}
          >
            {fr.payments.myPayments.viewReceipt}
          </Button>
          {payment.status === 'REJECTED' ? (
            <Link
              to={`/courses/${payment.courseId}${tenantSearch}`}
              className="sanaa-link"
            >
              {fr.payments.myPayments.retry}
            </Link>
          ) : null}
        </div>
      ),
    },
  ];

  const data = query.data;
  const showDelayNote =
    data?.items.some(
      (payment) => payment.status === 'PENDING_REVIEW' || payment.status === 'APPROVED',
    ) ?? false;

  return (
    <div className="sanaa-page">
      <header className="sanaa-page__head">
        <Typography.Title heading={3} className="sanaa-title-xl">
          {fr.payments.myPayments.title}
        </Typography.Title>
        <p className="sanaa-text sanaa-muted">{fr.payments.myPayments.subtitle}</p>
      </header>

      {query.isPending ? (
        <div className="sanaa-table-skeleton" data-testid="my-payments-skeleton">
          <Skeleton animation text={{ rows: 6 }} />
        </div>
      ) : query.isError ? (
        <Result
          status="warning"
          title={fr.payments.myPayments.loadErrorTitle}
          subTitle={fr.payments.myPayments.loadErrorBody}
          extra={
            <Button type="primary" onClick={() => void query.refetch()}>
              {fr.common.retry}
            </Button>
          }
        />
      ) : !data || data.items.length === 0 ? (
        <div className="sanaa-empty">
          <Empty description={fr.payments.myPayments.empty} />
          <Link to={`/courses${tenantSearch}`} className="sanaa-empty__action">
            <Button type="primary">{fr.payments.myPayments.browse}</Button>
          </Link>
        </div>
      ) : (
        <>
          <Table<Payment>
            rowKey="id"
            className="sanaa-payments-table"
            columns={columns}
            data={data.items}
            scroll={{ x: 1150 }}
            pagination={{
              current: page,
              pageSize: PAYMENTS_PAGE_SIZE,
              total: data.totalElements,
              showTotal: true,
              onChange: (nextPage) => setPage(nextPage),
            }}
          />
          <p className="sanaa-table-hint">{fr.payments.scrollHint}</p>
          {showDelayNote ? (
            <p className="sanaa-caption sanaa-payments-note">
              {fr.payments.enrollmentDelay}
            </p>
          ) : null}
        </>
      )}
    </div>
  );
}
