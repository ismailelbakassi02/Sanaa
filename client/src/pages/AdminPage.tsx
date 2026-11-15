import {
  Alert,
  Button,
  Card,
  Form,
  Input,
  Message,
  Modal,
  Pagination,
  Table,
  Tabs,
  Tag,
  type TableColumnProps,
} from '@arco-design/web-react';
import { IconSearch } from '@arco-design/web-react/icon';
import { useState } from 'react';

import type {
  AdminAuditEntry,
  AdminPublisherSummary,
  AdminUserSummary,
  PublisherStatus,
  UserStatus,
} from '../api/types';
import { PageHeader } from '../components/PageHeader';
import {
  useAdminPublishers,
  useAdminUsers,
  useAuthAudit,
  useUpdateAdminPublisherStatus,
  useUpdateAdminUserStatus,
} from '../hooks/admin';
import { useDebouncedValue } from '../hooks/useDebouncedValue';
import { formatDateTimeShort } from '../i18n/format';
import { fr } from '../i18n/fr';
import { applyServerFieldErrors } from '../lib/formErrors';
import { messageForError } from '../api/messages';

const PAGE_SIZE = 10;
const REASON_MIN = 5;
const REASON_MAX = 300;

type AdminTab = 'users' | 'publishers' | 'audit';

type ModerationTarget =
  | { kind: 'user'; id: number; label: string; nextStatus: UserStatus }
  | { kind: 'publisher'; id: string; label: string; nextStatus: PublisherStatus };

export function AdminPage() {
  const [tab, setTab] = useState<AdminTab>('users');
  const [userSearch, setUserSearch] = useState('');
  const [userPage, setUserPage] = useState(1);
  const [publisherSearch, setPublisherSearch] = useState('');
  const [publisherPage, setPublisherPage] = useState(1);
  const [actor, setActor] = useState('');
  const [action, setAction] = useState('');
  const [auditPage, setAuditPage] = useState(1);
  const [target, setTarget] = useState<ModerationTarget | null>(null);
  const [reasonForm] = Form.useForm<{ reason: string }>();

  const debouncedUserSearch = useDebouncedValue(userSearch);
  const debouncedPublisherSearch = useDebouncedValue(publisherSearch);
  const debouncedActor = useDebouncedValue(actor);
  const debouncedAction = useDebouncedValue(action);

  const usersQuery = useAdminUsers(
    {
      q: debouncedUserSearch.trim() || undefined,
      page: userPage - 1,
      size: PAGE_SIZE,
    },
    tab === 'users',
  );
  const publishersQuery = useAdminPublishers(
    {
      q: debouncedPublisherSearch.trim() || undefined,
      page: publisherPage - 1,
      size: PAGE_SIZE,
    },
    tab === 'publishers',
  );
  const auditQuery = useAuthAudit(
    {
      actor: debouncedActor.trim() || undefined,
      action: debouncedAction.trim() || undefined,
      page: auditPage - 1,
      size: PAGE_SIZE,
    },
    tab === 'audit',
  );

  const userStatus = useUpdateAdminUserStatus();
  const publisherStatus = useUpdateAdminPublisherStatus();

  const openUser = (user: AdminUserSummary) => {
    reasonForm.resetFields();
    setTarget({
      kind: 'user',
      id: user.id,
      label: user.fullName,
      nextStatus: user.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE',
    });
  };

  const openPublisher = (publisher: AdminPublisherSummary) => {
    reasonForm.resetFields();
    setTarget({
      kind: 'publisher',
      id: publisher.id,
      label: publisher.name,
      nextStatus: publisher.status === 'ACTIVE' ? 'SUSPENDED' : 'ACTIVE',
    });
  };

  const confirmModeration = async (values: { reason: string }) => {
    if (!target) {
      return;
    }
    try {
      if (target.kind === 'user') {
        await userStatus.mutateAsync({
          id: target.id,
          status: target.nextStatus,
          reason: values.reason.trim(),
        });
      } else {
        await publisherStatus.mutateAsync({
          id: target.id,
          status: target.nextStatus,
          reason: values.reason.trim(),
        });
      }
      Message.success(fr.admin.updated);
      setTarget(null);
    } catch (error) {
      if (!applyServerFieldErrors(reasonForm, error)) {
        Message.error(messageForError(error));
      }
    }
  };

  const userColumns: TableColumnProps<AdminUserSummary>[] = [
    {
      title: fr.admin.users.columnName,
      render: (_, user) => (
        <div className="sanaa-member">
          <div>
            <span className="sanaa-member__name">{user.fullName}</span>
            <span className="sanaa-caption">{user.email}</span>
          </div>
        </div>
      ),
    },
    {
      title: fr.admin.users.columnRole,
      render: (_, user) => (
        <span className="sanaa-member__status">
          <Tag>
            {fr.platformRoles[user.platformRole === 'SUPER_ADMIN' ? 'SUPER_ADMIN' : 'USER']}
          </Tag>
          {user.publisherRole ? (
            <Tag size="small">{fr.membershipRoles[user.publisherRole]}</Tag>
          ) : null}
        </span>
      ),
    },
    {
      title: fr.admin.users.columnPublisher,
      dataIndex: 'tenantSlug',
      width: 160,
      render: (_, user) => user.tenantSlug ?? fr.common.none,
    },
    {
      title: fr.admin.users.columnStatus,
      width: 120,
      render: (_, user) => (
        <Tag color={user.status === 'ACTIVE' ? 'green' : 'red'}>
          {fr.accountStatuses[user.status]}
        </Tag>
      ),
    },
    {
      title: fr.admin.users.columnCreated,
      dataIndex: 'createdAt',
      width: 170,
      render: (_, user) => formatDateTimeShort(user.createdAt),
    },
    {
      title: fr.admin.users.columnActions,
      width: 150,
      render: (_, user) => (
        <Button
          size="mini"
          status={user.status === 'ACTIVE' ? 'danger' : 'success'}
          onClick={() => openUser(user)}
        >
          {user.status === 'ACTIVE' ? fr.admin.users.disable : fr.admin.users.enable}
        </Button>
      ),
    },
  ];

  const publisherColumns: TableColumnProps<AdminPublisherSummary>[] = [
    {
      title: fr.admin.publishers.columnName,
      render: (_, publisher) => (
        <div>
          <span className="sanaa-member__name">{publisher.name}</span>
          <span className="sanaa-caption">/{publisher.slug}</span>
        </div>
      ),
    },
    {
      title: fr.admin.publishers.columnType,
      dataIndex: 'type',
      width: 170,
      render: (_, publisher) => <Tag>{fr.publisherTypes[publisher.type]}</Tag>,
    },
    {
      title: fr.admin.publishers.columnStatus,
      width: 120,
      render: (_, publisher) => (
        <Tag color={publisher.status === 'ACTIVE' ? 'green' : 'red'}>
          {fr.accountStatuses[publisher.status]}
        </Tag>
      ),
    },
    {
      title: fr.admin.publishers.columnContact,
      dataIndex: 'contactEmail',
      width: 200,
      render: (_, publisher) => publisher.contactEmail ?? fr.common.none,
    },
    {
      title: fr.admin.publishers.columnMembers,
      dataIndex: 'activeMembers',
      width: 130,
      align: 'right',
    },
    {
      title: fr.admin.publishers.columnCreated,
      dataIndex: 'createdAt',
      width: 170,
      render: (_, publisher) => formatDateTimeShort(publisher.createdAt),
    },
    {
      title: fr.admin.publishers.columnActions,
      width: 150,
      render: (_, publisher) => (
        <Button
          size="mini"
          status={publisher.status === 'ACTIVE' ? 'danger' : 'success'}
          onClick={() => openPublisher(publisher)}
        >
          {publisher.status === 'ACTIVE'
            ? fr.admin.publishers.suspend
            : fr.admin.publishers.reactivate}
        </Button>
      ),
    },
  ];

  const auditColumns: TableColumnProps<AdminAuditEntry>[] = [
    {
      title: fr.admin.audit.columnDate,
      dataIndex: 'createdAt',
      width: 170,
      render: (_, entry) => formatDateTimeShort(entry.createdAt),
    },
    { title: fr.admin.audit.columnActor, dataIndex: 'actorEmail', width: 220 },
    { title: fr.admin.audit.columnAction, dataIndex: 'action', width: 200 },
    {
      title: fr.admin.audit.columnTarget,
      width: 220,
      render: (_, entry) =>
        entry.targetId ? `${entry.targetType} ${entry.targetId}` : entry.targetType,
    },
    {
      title: fr.admin.audit.columnReason,
      render: (_, entry) => entry.reason ?? fr.common.none,
    },
  ];

  const renderTable = <T,>(
    query: {
      data?: { items: T[]; totalElements: number; totalPages: number };
      isError: boolean;
      isFetching: boolean;
      refetch: () => unknown;
    },
    columns: TableColumnProps<T>[],
    rowKey: keyof T & string,
    page: number,
    setPage: (page: number) => void,
  ) => {
    if (query.isError) {
      return (
        <Alert
          type="error"
          showIcon
          content={fr.admin.loadError}
          action={
            <Button size="small" onClick={() => void query.refetch()}>
              {fr.common.retry}
            </Button>
          }
        />
      );
    }
    return (
      <Card bordered className="sanaa-card sanaa-table-card" bodyStyle={{ padding: 0 }}>
        <Table
          rowKey={rowKey}
          columns={columns}
          data={query.data?.items ?? []}
          loading={query.isFetching}
          pagination={false}
          scroll={{ x: 1100 }}
          border={false}
        />
        {(query.data?.totalPages ?? 0) > 1 ? (
          <div className="sanaa-pagination">
            <Pagination
              current={page}
              pageSize={PAGE_SIZE}
              total={query.data?.totalElements ?? 0}
              onChange={setPage}
              showTotal
            />
          </div>
        ) : null}
      </Card>
    );
  };

  return (
    <div className="sanaa-page">
      <PageHeader title={fr.admin.title} subtitle={fr.admin.subtitle} />

      <Tabs activeTab={tab} onChange={(key) => setTab(key as AdminTab)}>
        <Tabs.TabPane key="users" title={fr.admin.tabs.users}>
          <Input
            allowClear
            prefix={<IconSearch />}
            placeholder={fr.admin.searchPlaceholder}
            value={userSearch}
            onChange={(value) => {
              setUserSearch(value);
              setUserPage(1);
            }}
            className="sanaa-table-search"
          />
          {renderTable(usersQuery, userColumns, 'id', userPage, setUserPage)}
        </Tabs.TabPane>

        <Tabs.TabPane key="publishers" title={fr.admin.tabs.publishers}>
          <Input
            allowClear
            prefix={<IconSearch />}
            placeholder={fr.admin.searchPublisherPlaceholder}
            value={publisherSearch}
            onChange={(value) => {
              setPublisherSearch(value);
              setPublisherPage(1);
            }}
            className="sanaa-table-search"
          />
          {renderTable(publishersQuery, publisherColumns, 'id', publisherPage, setPublisherPage)}
        </Tabs.TabPane>

        <Tabs.TabPane key="audit" title={fr.admin.tabs.audit}>
          <div className="sanaa-table-filters">
            <Input
              allowClear
              prefix={<IconSearch />}
              placeholder={fr.admin.filterActor}
              value={actor}
              onChange={(value) => {
                setActor(value);
                setAuditPage(1);
              }}
            />
            <Input
              allowClear
              prefix={<IconSearch />}
              placeholder={fr.admin.filterAction}
              value={action}
              onChange={(value) => {
                setAction(value);
                setAuditPage(1);
              }}
            />
          </div>
          {renderTable(auditQuery, auditColumns, 'id', auditPage, setAuditPage)}
        </Tabs.TabPane>
      </Tabs>

      <Modal
        visible={target !== null}
        title={fr.admin.reasonTitle(target?.label ?? '')}
        footer={null}
        onCancel={() => setTarget(null)}
        unmountOnExit
      >
        <Form form={reasonForm} layout="vertical" onSubmit={confirmModeration}>
          <Form.Item
            label={fr.admin.reasonLabel}
            field="reason"
            extra={fr.admin.reasonHelp}
            rules={[
              { required: true, message: fr.common.required },
              { minLength: REASON_MIN, message: fr.admin.reasonTooShort },
              { maxLength: REASON_MAX, message: fr.admin.reasonTooLong },
            ]}
          >
            <Input.TextArea
              placeholder={fr.admin.reasonPlaceholder}
              maxLength={REASON_MAX}
              showWordLimit
              autoSize={{ minRows: 3, maxRows: 6 }}
            />
          </Form.Item>
          <div className="sanaa-form-actions">
            <Button htmlType="button" onClick={() => setTarget(null)}>
              {fr.admin.cancel}
            </Button>
            <Button
              type="primary"
              htmlType="submit"
              loading={userStatus.isPending || publisherStatus.isPending}
            >
              {fr.admin.confirm}
            </Button>
          </div>
        </Form>
      </Modal>
    </div>
  );
}
