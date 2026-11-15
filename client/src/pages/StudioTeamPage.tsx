import {
  Avatar,
  Button,
  Card,
  Empty,
  Form,
  Input,
  Message,
  Modal,
  Pagination,
  Radio,
  Result,
  Skeleton,
  Table,
  Tag,
  type TableColumnProps,
} from '@arco-design/web-react';
import { IconEdit, IconPlus, IconUser } from '@arco-design/web-react/icon';
import { useState, type ReactNode } from 'react';
import { Link } from 'react-router-dom';

import { messageForError } from '../api/messages';
import { publicImageUrl } from '../api/profile';
import type { MembershipRole, TeamMemberResponse } from '../api/types';
import { PageHeader } from '../components/PageHeader';
import {
  useAddTeamMember,
  useResetTeamMemberPassword,
  useTeam,
  useUpdateTeamMemberStatus,
} from '../hooks/team';
import { formatDateTimeShort } from '../i18n/format';
import { fr } from '../i18n/fr';
import { applyServerFieldErrors } from '../lib/formErrors';
import { passwordIssue } from '../lib/password';
import { useAuthStore } from '../store/auth';

const PAGE_SIZE = 10;

interface AddFormValues {
  fullName: string;
  email: string;
  role: MembershipRole;
  temporaryPassword: string;
}

function temporaryPasswordValidator(
  value: unknown,
  callback: (error?: ReactNode) => void,
) {
  const text = typeof value === 'string' ? value : '';
  if (!text) {
    callback(fr.common.required);
    return;
  }
  if (passwordIssue(text) !== null) {
    callback(fr.validation.temporaryPassword);
    return;
  }
  callback();
}

export function StudioTeamPage() {
  const user = useAuthStore((state) => state.user);
  const organization = user?.tenantType === 'ORGANIZATION';
  const query = useTeam(organization);
  const addMutation = useAddTeamMember();
  const statusMutation = useUpdateTeamMemberStatus();
  const resetMutation = useResetTeamMemberPassword();
  const [page, setPage] = useState(1);
  const [addOpen, setAddOpen] = useState(false);
  const [resetTarget, setResetTarget] = useState<TeamMemberResponse | null>(null);
  const [addForm] = Form.useForm<AddFormValues>();
  const [resetForm] = Form.useForm<{ temporaryPassword: string }>();

  if (!organization) {
    return (
      <div className="sanaa-page sanaa-page--narrow">
        {/* The card below explains the limitation; the organization subtitle
            ("add instructors and administrators to your organization") would
            contradict it. */}
        <PageHeader title={fr.studio.team.title} />
        <Card bordered className="sanaa-card" bodyStyle={{ padding: 32 }}>
          <div className="sanaa-conflict">
            <IconUser className="sanaa-conflict__icon" aria-hidden="true" />
            <h2 className="sanaa-title-lg">{fr.studio.team.organizationOnlyTitle}</h2>
            <p className="sanaa-text sanaa-muted">{fr.studio.team.organizationOnlyBody}</p>
            <Link to="/studio/profile">
              <Button type="primary">{fr.me.studio}</Button>
            </Link>
          </div>
        </Card>
      </div>
    );
  }

  if (!user) {
    return null;
  }

  const members = query.data ?? [];
  const visible = members.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);
  const activeAdmins = members.filter(
    (member) => member.role === 'TENANT_ADMIN' && member.status === 'ACTIVE',
  ).length;

  const changeStatus = async (member: TeamMemberResponse) => {
    const status = member.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE';
    try {
      await statusMutation.mutateAsync({ userId: member.userId, status });
      Message.success(status === 'DISABLED' ? fr.studio.team.disabled : fr.studio.team.enabled);
    } catch (error) {
      Message.error(messageForError(error, 'team'));
    }
  };

  const handleAdd = async (values: AddFormValues) => {
    try {
      await addMutation.mutateAsync({
        fullName: values.fullName.trim(),
        email: values.email.trim(),
        role: values.role,
        temporaryPassword: values.temporaryPassword,
      });
      Message.success(fr.studio.team.added);
      setAddOpen(false);
      addForm.resetFields();
    } catch (error) {
      if (!applyServerFieldErrors(addForm, error)) {
        Message.error(messageForError(error, 'team'));
      }
    }
  };

  const handleReset = async (values: { temporaryPassword: string }) => {
    if (!resetTarget) {
      return;
    }
    try {
      await resetMutation.mutateAsync({
        userId: resetTarget.userId,
        payload: { temporaryPassword: values.temporaryPassword },
      });
      Message.success(fr.studio.team.resetDone);
      setResetTarget(null);
      resetForm.resetFields();
    } catch (error) {
      if (!applyServerFieldErrors(resetForm, error)) {
        Message.error(messageForError(error, 'team'));
      }
    }
  };

  const columns: TableColumnProps<TeamMemberResponse>[] = [
    {
      title: fr.studio.team.columnMember,
      render: (_, member) => (
        <div className="sanaa-member">
          <Avatar size={32}>
            {member.avatarImageId ? (
              <img src={publicImageUrl(member.avatarImageId) ?? ''} alt="" />
            ) : (
              member.fullName.charAt(0).toUpperCase()
            )}
          </Avatar>
          <div>
            <span className="sanaa-member__name">
              {member.fullName}
              {member.userId === user.id ? (
                <Tag size="small" className="sanaa-member__badge">
                  {fr.studio.team.selfBadge}
                </Tag>
              ) : null}
            </span>
            <span className="sanaa-caption">{member.email}</span>
          </div>
        </div>
      ),
    },
    {
      title: fr.studio.team.columnRole,
      dataIndex: 'role',
      width: 170,
      render: (_, member) => <Tag>{fr.membershipRoles[member.role]}</Tag>,
    },
    {
      title: fr.studio.team.columnStatus,
      width: 220,
      render: (_, member) => (
        <span className="sanaa-member__status">
          <Tag color={member.status === 'ACTIVE' ? 'green' : 'gray'}>
            {fr.accountStatuses[member.status]}
          </Tag>
          {member.mustChangePassword ? (
            <Tag size="small">{fr.studio.team.temporaryBadge}</Tag>
          ) : null}
        </span>
      ),
    },
    {
      title: fr.studio.team.columnCreated,
      dataIndex: 'createdAt',
      width: 170,
      render: (_, member) => (
        <span className="sanaa-nowrap">{formatDateTimeShort(member.createdAt)}</span>
      ),
    },
    {
      title: fr.studio.team.columnActions,
      width: 260,
      render: (_, member) => {
        const isSelf = member.userId === user.id;
        const lastAdmin =
          member.role === 'TENANT_ADMIN' && member.status === 'ACTIVE' && activeAdmins <= 1;
        return (
          <div className="sanaa-row-actions">
            <Button
              size="mini"
              disabled={isSelf || lastAdmin}
              loading={statusMutation.isPending}
              onClick={() => void changeStatus(member)}
            >
              {member.status === 'ACTIVE' ? fr.studio.team.disable : fr.studio.team.enable}
            </Button>
            <Button
              size="mini"
              type="text"
              icon={<IconEdit />}
              onClick={() => {
                resetForm.resetFields();
                setResetTarget(member);
              }}
            >
              {fr.studio.team.reset}
            </Button>
          </div>
        );
      },
    },
  ];

  return (
    <div className="sanaa-page">
      <PageHeader
        title={fr.studio.team.title}
        subtitle={fr.studio.team.subtitle}
        actions={
          <Button type="primary" icon={<IconPlus />} onClick={() => setAddOpen(true)}>
            {fr.studio.team.add}
          </Button>
        }
      />

      {query.isPending ? (
        <Card bordered className="sanaa-card" bodyStyle={{ padding: 24 }}>
          <Skeleton animation text={{ rows: 6 }} />
        </Card>
      ) : query.isError ? (
        <Result
          status="warning"
          title={fr.studio.team.loadError}
          extra={
            <Button type="primary" onClick={() => void query.refetch()}>
              {fr.common.retry}
            </Button>
          }
        />
      ) : members.length === 0 ? (
        <Empty description={fr.studio.team.empty} />
      ) : (
        <Card bordered className="sanaa-card sanaa-table-card" bodyStyle={{ padding: 0 }}>
          <Table
            rowKey="userId"
            columns={columns}
            data={visible}
            pagination={false}
            scroll={{ x: 960 }}
            border={false}
          />
          {members.length > PAGE_SIZE ? (
            <div className="sanaa-pagination">
              <Pagination
                current={page}
                pageSize={PAGE_SIZE}
                total={members.length}
                onChange={setPage}
                showTotal
              />
            </div>
          ) : null}
        </Card>
      )}

      <Modal
        visible={addOpen}
        title={fr.studio.team.addTitle}
        footer={null}
        onCancel={() => setAddOpen(false)}
        unmountOnExit
      >
        <Form form={addForm} layout="vertical" onSubmit={handleAdd}>
          <Form.Item
            label={fr.studio.team.fullNameLabel}
            field="fullName"
            rules={[
              { required: true, message: fr.common.required },
              { minLength: 2, message: fr.validation.name },
              { maxLength: 120, message: fr.validation.name },
            ]}
          >
            <Input placeholder={fr.studio.team.fullNamePlaceholder} />
          </Form.Item>
          <Form.Item
            label={fr.studio.team.emailLabel}
            field="email"
            rules={[
              { required: true, message: fr.common.required },
              { type: 'email', message: fr.common.emailInvalid },
            ]}
          >
            <Input placeholder={fr.studio.team.emailPlaceholder} autoComplete="off" />
          </Form.Item>
          <Form.Item
            label={fr.studio.team.roleLabel}
            field="role"
            initialValue="INSTRUCTOR"
            rules={[{ required: true, message: fr.common.required }]}
          >
            <Radio.Group>
              <Radio value="INSTRUCTOR">{fr.membershipRoles.INSTRUCTOR}</Radio>
              <Radio value="TENANT_ADMIN">{fr.membershipRoles.TENANT_ADMIN}</Radio>
            </Radio.Group>
          </Form.Item>
          <Form.Item
            label={fr.studio.team.passwordLabel}
            field="temporaryPassword"
            extra={fr.studio.team.passwordHint}
            rules={[
              { required: true, message: fr.common.required },
              { validator: temporaryPasswordValidator },
            ]}
          >
            <Input.Password autoComplete="new-password" />
          </Form.Item>
          <div className="sanaa-form-actions">
            <Button htmlType="button" onClick={() => setAddOpen(false)}>
              {fr.common.cancel}
            </Button>
            <Button type="primary" htmlType="submit" loading={addMutation.isPending}>
              {fr.studio.team.addSubmit}
            </Button>
          </div>
        </Form>
      </Modal>

      <Modal
        visible={resetTarget !== null}
        title={fr.studio.team.resetTitle}
        footer={null}
        onCancel={() => setResetTarget(null)}
        unmountOnExit
      >
        <p className="sanaa-text sanaa-muted">
          {resetTarget ? fr.studio.team.resetBody(resetTarget.fullName) : ''}
        </p>
        <Form form={resetForm} layout="vertical" onSubmit={handleReset}>
          <Form.Item
            label={fr.studio.team.passwordLabel}
            field="temporaryPassword"
            rules={[
              { required: true, message: fr.common.required },
              { validator: temporaryPasswordValidator },
            ]}
          >
            <Input.Password autoComplete="new-password" />
          </Form.Item>
          <div className="sanaa-form-actions">
            <Button htmlType="button" onClick={() => setResetTarget(null)}>
              {fr.common.cancel}
            </Button>
            <Button type="primary" htmlType="submit" loading={resetMutation.isPending}>
              {fr.studio.team.resetSubmit}
            </Button>
          </div>
        </Form>
      </Modal>
    </div>
  );
}
