import { ActionIcon, Badge, Button, Drawer, Group, Menu, PasswordInput, Stack, TextInput } from '@mantine/core';
import { modals } from '@mantine/modals';
import { IconDots, IconPlus } from '@tabler/icons-react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { api, type Page } from '../../api/client';
import { useCompany } from '../../app/CompanyContext';
import { useAuth } from '../../auth/AuthContext';
import { DataTable } from '../../components/DataTable';
import { EntityForm, type FormValues } from '../../components/EntityForm';
import { showError, showSuccess } from '../../components/notify';
import { toOptions, useBranches, useDepartments } from '../org/useOrgData';
import type { Role, User } from './types';

const SCOPES = ['ALL', 'COMPANY', 'BRANCH', 'DEPARTMENT', 'OWN'].map((v) => ({ value: v, label: v }));

interface EmployeeOption {
  id: string;
  employeeNumber: string;
  firstName: string;
  lastName: string;
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

  const refresh = () => client.invalidateQueries({ queryKey: ['users'] });

  const fields = (user: User | null) => [
    { name: 'username', label: 'Username', required: true },
    { name: 'displayName', label: 'Display name', required: true },
    { name: 'email', label: 'Email' },
    ...(user ? [] : [{ name: 'password', label: 'Initial password', required: true, description: 'User must change it at first login.' }]),
    { name: 'roles', label: 'Roles', type: 'multiselect' as const, required: true, options: roles.map((r) => ({ value: r.code, label: r.name })) },
    { name: 'scopeType', label: 'Data scope', type: 'select' as const, required: true, options: SCOPES },
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
    let value = '';
    modals.openConfirmModal({
      title: `Reset password for ${user.username}`,
      children: <PasswordInput label="Temporary password" onChange={(e) => (value = e.currentTarget.value)} />,
      labels: { confirm: 'Reset', cancel: 'Cancel' },
      onConfirm: async () => {
        try {
          await api(`/admin/users/${user.id}/reset-password`, { method: 'POST', body: { temporaryPassword: value } });
          showSuccess('Password reset; the user must change it at next login.');
        } catch (e) {
          showError(e);
        }
      },
    });
  };

  const setActive = async (user: User, active: boolean) => {
    try {
      await api(`/admin/users/${user.id}/${active ? 'activate' : 'deactivate'}`, { method: 'POST' });
      showSuccess();
      await refresh();
    } catch (e) {
      showError(e);
    }
  };

  return (
    <>
      <Group justify="space-between" mb="sm">
        <TextInput placeholder="Search users" value={q} onChange={(e) => { setQ(e.currentTarget.value); setPage(0); }} w={260} />
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
        onPageChange={setPage}
        columns={[
          { key: 'username', header: 'Username', render: (r) => r.username },
          { key: 'name', header: 'Name', render: (r) => r.displayName },
          { key: 'roles', header: 'Roles', render: (r) => r.roles.join(', ') },
          { key: 'scope', header: 'Scope', render: (r) => r.scopeType },
          {
            key: 'status', header: 'Status',
            render: (r) => (
              <Group gap={4}>
                <Badge color={r.active ? 'green' : 'gray'}>{r.active ? 'Active' : 'Inactive'}</Badge>
                {r.locked && <Badge color="red">Locked</Badge>}
              </Group>
            ),
          },
          {
            key: 'actions', header: '',
            render: (r) =>
              can('ADMIN:EDIT') && (
                <Menu position="bottom-end">
                  <Menu.Target>
                    <ActionIcon variant="subtle" aria-label="User actions"><IconDots size={16} /></ActionIcon>
                  </Menu.Target>
                  <Menu.Dropdown>
                    <Menu.Item onClick={() => setEditing({ user: r })}>Edit</Menu.Item>
                    <Menu.Item onClick={() => resetPassword(r)}>Reset password</Menu.Item>
                    <Menu.Item color={r.active ? 'red' : undefined} onClick={() => void setActive(r, !r.active)}>
                      {r.active ? 'Deactivate' : 'Activate'}
                    </Menu.Item>
                  </Menu.Dropdown>
                </Menu>
              ),
          },
        ]}
      />
      <Drawer opened={editing !== null} onClose={() => setEditing(null)} title={editing?.user ? 'Edit user' : 'Create user'} position="right" size="md">
        {editing && (
          <Stack>
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
