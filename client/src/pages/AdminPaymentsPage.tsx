import {
  Button,
  Empty,
  Message,
  Popconfirm,
  Radio,
  Result,
  Skeleton,
  Table,
  Tooltip,
  Typography,
  type TableColumnProps,
} from '@arco-design/web-react';
import { IconEye } from '@arco-design/web-react/icon';
import { useState } from 'react';

import { toApiError } from '../api/errors';
import { messageForError } from '../api/messages';
import type { Payment, PaymentStatus } from '../api/payments';
import { PaymentStatusTag } from '../components/PaymentStatusTag';
import { RejectPaymentModal } from '../components/RejectPaymentModal';
import {
  PAYMENTS_PAGE_SIZE,
  useAdminPayments,
  useApprovePayment,
  usePaymentReceipt,
  useRejectPayment,
} from '../hooks/payments';
import { formatDateTimeShort, formatPrice } from '../i18n/format';
import { fr } from '../i18n/fr';
import { adminPaymentErrorMessage } from '../lib/paymentErrors';

type StatusFilter = PaymentStatus | 'ALL';

const FILTERS: { value: StatusFilter; label: string }[] = [
  { value: 'PENDING_REVIEW', label: fr.payments.statuses.PENDING_REVIEW },
  { value: 'APPROVED', label: fr.payments.statuses.APPROVED },
  { value: 'REJECTED', label: fr.payments.statuses.REJECTED },
  { value: 'ALL', label: fr.payments.admin.filterAll },
];

export function AdminPaymentsPage() {
  const [filter, setFilter] = useState<StatusFilter>('PENDING_REVIEW');
  const [page, setPage] = useState(1);
  const [rejectTarget, setRejectTarget] = useState<Payment | null>(null);
  const query = useAdminPayments(filter, page);
  const approve = useApprovePayment();
  const reject = useRejectPayment();
  const receipt = usePaymentReceipt();

  const openReceipt = async (paymentId: number) => {
    try {
      await receipt.open(paymentId);
    } catch (error) {
      Message.error(messageForError(error));
    }
  };

  const handleApprove = (payment: Payment) => {
    approve.mutate(payment.id, {
      onSuccess: () => Message.success(fr.payments.admin.approved),
      onError: (error) => Message.error(adminPaymentErrorMessage(error)),
    });
  };

  const handleReject = (reason: string) => {
    if (!rejectTarget) {
      return;
    }
    reject.mutate(
      { id: rejectTarget.id, reason },
      {
        onSuccess: () => {
          Message.success(fr.payments.admin.rejected);
          setRejectTarget(null);
        },
        onError: (error) => {
          Message.error(adminPaymentErrorMessage(error));
          if (toApiError(error).status === 409) {
            setRejectTarget(null);
          }
        },
      },
    );
  };

  const columns: TableColumnProps<Payment>[] = [
    {
      title: fr.payments.admin.columnStudent,
      width: 180,
      render: (_, payment) => (
        <div className="sanaa-payment-student">
          <span className="sanaa-payment-student__name">{payment.userName}</span>
          <span className="sanaa-caption">{payment.userEmail}</span>
        </div>
      ),
    },
    {
      title: fr.payments.admin.columnCourse,
      dataIndex: 'courseTitle',
      width: 200,
    },
    {
      title: fr.payments.admin.columnAmount,
      dataIndex: 'amount',
      width: 110,
      align: 'right',
      render: (_, payment) => formatPrice(payment.amount, payment.currency),
    },
    {
      title: fr.payments.admin.columnMethod,
      dataIndex: 'method',
      width: 160,
      render: (_, payment) => fr.payments.methods[payment.method],
    },
    {
      title: fr.payments.admin.columnDate,
      dataIndex: 'createdAt',
      width: 150,
      render: (_, payment) => formatDateTimeShort(payment.createdAt),
    },
    {
      title: fr.payments.admin.columnStatus,
      dataIndex: 'status',
      width: 110,
      render: (_, payment) => <PaymentStatusTag status={payment.status} />,
    },
    {
      title: fr.payments.admin.columnActions,
      width: 240,
      align: 'right',
      render: (_, payment) => (
        <div className="sanaa-payment-actions">
          <Tooltip content={fr.payments.admin.viewReceipt}>
            <Button
              type="text"
              size="small"
              aria-label={fr.payments.admin.viewReceipt}
              icon={<IconEye />}
              loading={receipt.openingId === payment.id}
              onClick={() => void openReceipt(payment.id)}
            />
          </Tooltip>
          {payment.status === 'PENDING_REVIEW' ? (
            <>
              <Popconfirm
                title={fr.payments.admin.approveConfirm}
                okText={fr.payments.admin.approve}
                cancelText={fr.payments.admin.cancel}
                onOk={() => handleApprove(payment)}
              >
                <Button
                  type="text"
                  size="small"
                  status="success"
                  loading={approve.isPending && approve.variables === payment.id}
                >
                  {fr.payments.admin.approve}
                </Button>
              </Popconfirm>
              <Button
                type="text"
                size="small"
                status="danger"
                onClick={() => setRejectTarget(payment)}
              >
                {fr.payments.admin.reject}
              </Button>
            </>
          ) : null}
        </div>
      ),
    },
  ];

  return (
    <div className="sanaa-page">
      <header className="sanaa-page__head sanaa-page__head--row">
        <div>
          <Typography.Title heading={3} className="sanaa-title-xl">
            {fr.payments.admin.title}
          </Typography.Title>
          <p className="sanaa-text sanaa-muted">{fr.payments.admin.subtitle}</p>
        </div>
        <div className="sanaa-payments-filter">
          <span className="sanaa-caption">{fr.payments.admin.filterLabel}</span>
          <Radio.Group
            type="button"
            value={filter}
            onChange={(value) => {
              setFilter(value as StatusFilter);
              setPage(1);
            }}
          >
            {FILTERS.map((option) => (
              <Radio key={option.value} value={option.value}>
                {option.label}
              </Radio>
            ))}
          </Radio.Group>
        </div>
      </header>

      {query.isPending ? (
        <div className="sanaa-table-skeleton" data-testid="admin-payments-skeleton">
          <Skeleton animation text={{ rows: 6 }} />
        </div>
      ) : query.isError ? (
        <Result
          status="warning"
          title={fr.payments.admin.loadErrorTitle}
          subTitle={fr.payments.admin.loadErrorBody}
          extra={
            <Button type="primary" onClick={() => void query.refetch()}>
              {fr.common.retry}
            </Button>
          }
        />
      ) : !query.data || query.data.items.length === 0 ? (
        <div className="sanaa-empty">
          <Empty description={fr.payments.admin.empty} />
        </div>
      ) : (
        <Table<Payment>
          rowKey="id"
          className="sanaa-payments-table"
          columns={columns}
          data={query.data.items}
          scroll={{ x: 1150 }}
          pagination={{
            current: page,
            pageSize: PAYMENTS_PAGE_SIZE,
            total: query.data.totalElements,
            showTotal: true,
            onChange: (nextPage) => setPage(nextPage),
          }}
        />
      )}

      {query.data && query.data.items.length > 0 ? (
        <p className="sanaa-table-hint">{fr.payments.scrollHint}</p>
      ) : null}

      <RejectPaymentModal
        visible={rejectTarget !== null}
        studentName={rejectTarget?.userName ?? ''}
        courseTitle={rejectTarget?.courseTitle ?? ''}
        submitting={reject.isPending}
        onCancel={() => {
          if (!reject.isPending) {
            setRejectTarget(null);
          }
        }}
        onSubmit={handleReject}
      />
    </div>
  );
}
