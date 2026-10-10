import { ActionIcon, Avatar, Badge, Button, Drawer, Group, Menu, PasswordInput, Stack, Text, TextInput, Tooltip } from '@mantine/core';
import { modals } from '@mantine/modals';
import {
  IconDots,
  IconKey,
  IconLock,
  IconPencil,
  IconPlus,
  IconSearch,
  IconUserCheck,
  IconUserOff,
  IconUsers,
} from '@tabler/icons-react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { api, type Page } from '../../api/client';
import { useCompany } from '../../app/CompanyContext';
import { useAuth } from '../../auth/AuthContext';
import type { ScopeType } from '../../auth/types';
import { DataTable } from '../../components/DataTable';
import { EntityForm, type FormValues } from '../../components/EntityForm';
import { formatDateTime } from '../../components/format';
import { showError, showSuccess } from '../../components/notify';
import { StatusBadge } from '../../components/StatusBadge';
import { toOptions, useBranches, useDepartments } from '../org/useOrgData';
import type { Role, User } from './types';

const SCOPE_LABELS: Record<ScopeType, string> = {
  ALL: 'All data',
  COMPANY: 'Selected companies',
  BRANCH: 'Selected branches',
  DEPARTMENT: 'Selected departments',
  OWN: 'Own records only',
};
const SCOPES = (Object.keys(SCOPE_LABELS) as ScopeType[]).map((v) => ({ value: v, label: SCOPE_LABELS[v] }));

interface EmployeeOption {
  id: string;
  employeeNumber: string;
  firstName: string;
  lastName: string;
}

function scopeCount(user: User): number | null {
  switch (user.scopeType) {
    case 'COMPANY': return user.scopeCompanyIds.length;
    case 'BRANCH': return user.scopeBranchIds.length;
    case 'DEPARTMENT': return user.scopeDepartmentIds.length;
    default: return null;
  }
}

function ResetPasswordForm({ user, onDone }: { user: User; onDone: () => void }) {
  const [value, setValue] = useState('');
  const [busy, setBusy] = useState(false);
  const submit = async () => {
    setBusy(true);
    try {
      await api(`/admin/users/${user.id}/reset-password`, { method: 'POST', body: { temporaryPassword: value } });
      showSuccess('Password reset; the user must change it at next login.');
      onDone();
    } catch (e) {
      showError(e);
    } finally {
      setBusy(false);
    }
  };
  return (
    <form onSubmit={(e) => { e.preventDefault(); void submit(); }}>
      <Stack>
        <Text size="sm" c="dimmed">
          Set a temporary password for <b>{user.displayName}</b>. They will be asked to choose a new one at next login.
        </Text>
        <PasswordInput label="Temporary password" withAsterisk data-autofocus value={value} onChange={(e) => setValue(e.currentTarget.value)} />
        <Group justify="flex-end">
          <Button variant="default" onClick={onDone}>Cancel</Button>
          <Button type="submit" color="red" loading={busy} disabled={!value}>Reset</Button>
        </Group>
      </Stack>
    </form>
  );
}

export function UsersPanel() {
  const { can } = useAuth();
  const { companies, companyId } = useCompany();
  const client = useQueryClient();
  const [q, setQ] = useState('');
  const [page, setPage] = useState(0);
  const [editing, setEditing] = useState<{ user: User | null } | null>(null);
  const { data, isLoading } = useQuery({
    queryKey: ['users', q, page],
    queryFn: () => api<Page<User>>('/admin/users', { query: { q, page, size: 20 } }),
  });
  const { data: roles = [] } = useQuery({ queryKey: ['roles'], queryFn: () => api<Role[]>('/admin/roles') });
  const { data: branches } = useBranches(companyId);
  const { data: departments } = useDepartments(companyId);
  const { data: employees } = useQuery({
    queryKey: ['employee-options', companyId],
    queryFn: () => api<Page<EmployeeOption>>('/employees', { query: { companyId, size: 200, sort: 'employeeNumber,asc' } }),
    enabled: !!companyId && can('EMPLOYEE:VIEW'),
  });
  const roleName = new Map(roles.map((r) => [r.code, r.name]));
  const canEdit = can('ADMIN:EDIT');

  const refresh = () => client.invalidateQueries({ queryKey: ['users'] });

  const fields = (user: User | null) => [
    { name: 'username', label: 'Username', required: true },
    { name: 'displayName', label: 'Display name', required: true },
    { name: 'email', label: 'Email' },
    ...(user ? [] : [{ name: 'password', label: 'Initial password', required: true, description: 'User must change it at first login.' }]),
    { name: 'roles', label: 'Roles', type: 'multiselect' as const, required: true, options: roles.map((r) => ({ value: r.code, label: r.name })) },
    { name: 'scopeType', label: 'Data scope', type: 'select' as const, required: true, options: SCOPES, description: 'Which employees this user can see and act on.' },
    { name: 'scopeCompanyIds', label: 'Companies', type: 'multiselect' as const, options: companies.map((c) => ({ value: c.id, label: c.code })), visible: (v: FormValues) => v.scopeType === 'COMPANY' },
    { name: 'scopeBranchIds', label: 'Branches', type: 'multiselect' as const, options: toOptions(branches), visible: (v: FormValues) => v.scopeType === 'BRANCH' },
    { name: 'scopeDepartmentIds', label: 'Departments (includes sub-departments)', type: 'multiselect' as const, options: toOptions(departments), visible: (v: FormValues) => v.scopeType === 'DEPARTMENT' },
    {
      name: 'employeeId', label: 'Linked employee (self-service)', type: 'select' as const,
      options: (employees?.content ?? []).map((e) => ({ value: e.id, label: `${e.employeeNumber} — ${e.firstName} ${e.lastName}` })),
    },
    { name: 'active', label: 'Active', type: 'checkbox' as const },
  ];

  const resetPassword = (user: User) => {
    const id = modals.open({
      title: `Reset password for ${user.username}?`,
      children: <ResetPasswordForm user={user} onDone={() => modals.close(id)} />,
    });
  };

  const setActive = async (user: User, active: boolean) => {
    try {
      await api(`/admin/users/${user.id}/${active ? 'activate' : 'deactivate'}`, { method: 'POST' });
      showSuccess(active ? 'User activated' : 'User deactivated');
      await refresh();
    } catch (e) {
      showError(e);
    }
  };

  const confirmDeactivate = (user: User) =>
    modals.openConfirmModal({
      title: `Deactivate ${user.displayName}?`,
      children: <Text size="sm">They will no longer be able to sign in. You can activate the account again at any time.</Text>,
      labels: { confirm: 'Deactivate', cancel: 'Cancel' },
      confirmProps: { color: 'red' },
      onConfirm: () => void setActive(user, false),
    });

  return (
    <>
      <Group justify="space-between" mb="sm" gap="sm" wrap="wrap">
        <TextInput
          leftSection={<IconSearch size={14} />}
          placeholder="Search users"
          aria-label="Search users"
          value={q}
          onChange={(e) => { setQ(e.currentTarget.value); setPage(0); }}
          w={{ base: '100%', xs: 280 }}
        />
        {can('ADMIN:ADD') && (
          <Button leftSection={<IconPlus size={16} />} onClick={() => setEditing({ user: null })}>
            Create user
          </Button>
        )}
      </Group>
      <DataTable
        rows={data?.content ?? []}
        loading={isLoading}
        rowKey={(r) => r.id}
        page={page}
        totalPages={data?.totalPages}
        totalElements={data?.totalElements}
        onPageChange={setPage}
        onRowClick={canEdit ? (r) => setEditing({ user: r }) : undefined}
        rowLabel={(r) => `Edit ${r.displayName}`}
        minWidth={760}
        emptyIcon={<IconUsers size={20} stroke={1.5} />}
        emptyTitle={q ? 'No matching users' : 'No users yet'}
        emptyDescription={q ? 'Try a different name or username.' : 'Create a user to give someone access to the system.'}
        columns={[
          {
            key: 'name', header: 'User',
            render: (r) => (
              <Group gap="sm" wrap="nowrap">
                <Avatar name={r.displayName} color="initials" size="sm" radius="xl" />
                <div style={{ minWidth: 0 }}>
                  <Text size="sm" fw={500}>{r.displayName}</Text>
                  <Text size="xs" c="dimmed">{r.username}{r.email ? ` · ${r.email}` : ''}</Text>
                </div>
              </Group>
            ),
          },
          {
            key: 'roles', header: 'Roles',
            render: (r) => (
              <Group gap={4}>
                {r.roles.map((code) => (
                  <Badge key={code} variant="light" color={code === 'SUPER_ADMIN' ? 'grape' : 'brand'} size="sm">
                    {roleName.get(code) ?? code}
                  </Badge>
                ))}
              </Group>
            ),
          },
          {
            key: 'scope', header: 'Scope',
            render: (r) => {
              const n = scopeCount(r);
              return (
                <Text size="sm">
                  {SCOPE_LABELS[r.scopeType] ?? r.scopeType}
                  {n !== null && <Text span c="dimmed" size="sm"> ({n})</Text>}
                </Text>
              );
            },
          },
          {
            key: 'status', header: 'Status',
            render: (r) => (
              <Group gap={4} wrap="nowrap">
                <StatusBadge status={r.active ? 'ACTIVE' : 'INACTIVE'} />
                {r.locked && <Badge color="red" variant="light" leftSection={<IconLock size={12} />}>Locked</Badge>}
              </Group>
            ),
          },
          { key: 'login', header: 'Last sign-in', render: (r) => <Text size="sm" c="dimmed">{r.lastLoginAt ? formatDateTime(r.lastLoginAt) : 'Never'}</Text> },
          {
            key: 'actions', header: '', width: 56, align: 'right',
            render: (r) =>
              canEdit && (
                <div onClick={(e) => e.stopPropagation()}>
                  <Menu position="bottom-end" withinPortal>
                    <Menu.Target>
                      <Tooltip label="User actions">
                        <ActionIcon variant="subtle" color="gray" aria-label="User actions"><IconDots size={16} /></ActionIcon>
                      </Tooltip>
                    </Menu.Target>
                    <Menu.Dropdown>
                      <Menu.Item leftSection={<IconPencil size={14} />} onClick={() => setEditing({ user: r })}>Edit</Menu.Item>
                      <Menu.Item leftSection={<IconKey size={14} />} onClick={() => resetPassword(r)}>Reset password</Menu.Item>
                      <Menu.Divider />
                      {r.active ? (
                        <Menu.Item color="red" leftSection={<IconUserOff size={14} />} onClick={() => confirmDeactivate(r)}>Deactivate</Menu.Item>
                      ) : (
                        <Menu.Item leftSection={<IconUserCheck size={14} />} onClick={() => void setActive(r, true)}>Activate</Menu.Item>
                      )}
                    </Menu.Dropdown>
                  </Menu>
                </div>
              ),
          },
        ]}
      />
      <Drawer opened={editing !== null} onClose={() => setEditing(null)} title={<Text fw={600}>{editing?.user ? 'Edit user' : 'Create user'}</Text>} position="right" size="md">
        {editing && (
          <Stack>
            {editing.user && (
              <Group gap="sm" wrap="nowrap">
                <Avatar name={editing.user.displayName} color="initials" radius="xl" />
                <div>
                  <Text fw={600}>{editing.user.displayName}</Text>
                  <Text size="xs" c="dimmed">
                    Last sign-in: {editing.user.lastLoginAt ? formatDateTime(editing.user.lastLoginAt) : 'Never'}
                  </Text>
                </div>
              </Group>
            )}
            <EntityForm
              fields={fields(editing.user)}
              initialValues={
                editing.user
                  ? { ...editing.user }
                  : { username: '', displayName: '', email: '', password: '', roles: [], scopeType: 'OWN', scopeCompanyIds: [], scopeBranchIds: [], scopeDepartmentIds: [], employeeId: null, active: true }
              }
              onCancel={() => setEditing(null)}
              onSubmit={async (values) => {
                if (editing.user) await api(`/admin/users/${editing.user.id}`, { method: 'PUT', body: { ...values, version: editing.user.version } });
                else await api('/admin/users', { method: 'POST', body: values });
                showSuccess();
                setEditing(null);
                await refresh();
              }}
            />
          </Stack>
        )}
      </Drawer>
    </>
  );
}
