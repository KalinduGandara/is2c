import { Alert, Badge, Button, Drawer, Group, SimpleGrid, Stack, Text, TextInput } from '@mantine/core';
import { modals } from '@mantine/modals';
import { IconInfoCircle, IconLock, IconPlus, IconShieldLock, IconTrash } from '@tabler/icons-react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { api } from '../../api/client';
import { useAuth } from '../../auth/AuthContext';
import { DataTable } from '../../components/DataTable';
import { showError, showSuccess } from '../../components/notify';
import { PermissionMatrix } from './PermissionMatrix';
import type { PermissionCatalog, Role } from './types';

export function RolesPanel() {
  const { can } = useAuth();
  const client = useQueryClient();
  const editable = can('ADMIN:CONFIGURE');
  const { data: roles = [], isLoading } = useQuery({ queryKey: ['roles'], queryFn: () => api<Role[]>('/admin/roles') });
  const { data: catalog } = useQuery({ queryKey: ['permissions'], queryFn: () => api<PermissionCatalog>('/admin/permissions') });
  const [editing, setEditing] = useState<Partial<Role> | null>(null);
  const [saving, setSaving] = useState(false);

  const isSuperAdmin = editing?.code === 'SUPER_ADMIN';
  const valid = !!editing?.code?.trim() && !!editing?.name?.trim();

  const save = async () => {
    if (!editing) return;
    setSaving(true);
    try {
      const body = { code: editing.code, name: editing.name, description: editing.description, permissions: editing.permissions ?? [], version: editing.version };
      if (editing.id) await api(`/admin/roles/${editing.id}`, { method: 'PUT', body });
      else await api('/admin/roles', { method: 'POST', body });
      showSuccess();
      setEditing(null);
      await client.invalidateQueries({ queryKey: ['roles'] });
    } catch (e) {
      showError(e);
    } finally {
      setSaving(false);
    }
  };

  const confirmDelete = (role: Partial<Role>) =>
    modals.openConfirmModal({
      title: `Delete role ${role.name ?? role.code}?`,
      children: <Text size="sm">Users who hold this role will lose its permissions. This cannot be undone.</Text>,
      labels: { confirm: 'Delete role', cancel: 'Cancel' },
      confirmProps: { color: 'red' },
      onConfirm: async () => {
        try {
          await api(`/admin/roles/${role.id}`, { method: 'DELETE' });
          showSuccess('Role deleted');
          setEditing(null);
          await client.invalidateQueries({ queryKey: ['roles'] });
        } catch (e) {
          showError(e);
        }
      },
    });

  return (
    <>
      <Group justify="space-between" mb="sm" gap="sm" wrap="wrap">
        <Text size="sm" c="dimmed">Roles bundle permissions; assign them to users on the Users tab.</Text>
        {editable && (
          <Button leftSection={<IconPlus size={16} />} onClick={() => setEditing({ permissions: [] })}>
            Create role
          </Button>
        )}
      </Group>
      <DataTable
        rows={roles}
        loading={isLoading}
        rowKey={(r) => r.id}
        onRowClick={(r) => setEditing(r)}
        rowLabel={(r) => `Open role ${r.name}`}
        emptyTitle="No roles yet"
        emptyIcon={<IconShieldLock size={20} stroke={1.5} />}
        columns={[
          {
            key: 'name', header: 'Role',
            render: (r) => (
              <div>
                <Text size="sm" fw={600}>{r.name}</Text>
                <Text size="xs" c="dimmed" ff="monospace">{r.code}</Text>
              </div>
            ),
          },
          { key: 'description', header: 'Description', render: (r) => <Text size="sm" c="dimmed" lineClamp={2} maw={360}>{r.description || '—'}</Text> },
          {
            key: 'type', header: 'Type',
            render: (r) =>
              r.system ? (
                <Badge variant="light" color="gray" leftSection={<IconLock size={12} />}>System</Badge>
              ) : (
                <Badge variant="light" color="brand">Custom</Badge>
              ),
          },
          {
            key: 'perms', header: 'Permissions', align: 'right',
            render: (r) => (r.code === 'SUPER_ADMIN' ? <Badge variant="light" color="grape">All</Badge> : <Text size="sm" fw={600} className="tabular">{r.permissions.length}</Text>),
          },
        ]}
      />
      <Drawer
        opened={editing !== null}
        onClose={() => setEditing(null)}
        title={<Text fw={600}>{editing?.id ? `Role ${editing.code}` : 'Create role'}</Text>}
        position="right"
        size="min(960px, 100vw)"
      >
        {editing && catalog && (
          <Stack>
            {isSuperAdmin && (
              <Alert variant="light" color="grape" icon={<IconInfoCircle size={18} />}>
                The super administrator role always has every permission and cannot be changed.
              </Alert>
            )}
            {editing.system && !isSuperAdmin && (
              <Alert variant="light" color="gray" icon={<IconLock size={18} />}>
                Built-in role: its name is fixed and it cannot be deleted.
              </Alert>
            )}
            <SimpleGrid cols={{ base: 1, sm: 2 }}>
              {!editing.id && (
                <TextInput
                  label="Code" withAsterisk placeholder="PAYROLL_CLERK" description="Upper-case identifier; cannot be changed later."
                  value={editing.code ?? ''} onChange={(e) => setEditing({ ...editing, code: e.currentTarget.value.toUpperCase() })}
                />
              )}
              <TextInput label="Name" withAsterisk disabled={editing.system || !editable} value={editing.name ?? ''} onChange={(e) => setEditing({ ...editing, name: e.currentTarget.value })} />
            </SimpleGrid>
            <TextInput label="Description" disabled={!editable} value={editing.description ?? ''} onChange={(e) => setEditing({ ...editing, description: e.currentTarget.value })} />
            <PermissionMatrix
              catalog={catalog}
              value={editing.permissions ?? []}
              disabled={!editable || isSuperAdmin}
              onChange={(permissions) => setEditing({ ...editing, permissions })}
            />
            {editable && !isSuperAdmin && (
              <Group justify="space-between" mt="xs">
                {editing.id && !editing.system ? (
                  <Button color="red" variant="subtle" leftSection={<IconTrash size={16} />} onClick={() => confirmDelete(editing)}>
                    Delete
                  </Button>
                ) : <span />}
                <Group gap="xs">
                  <Button variant="default" onClick={() => setEditing(null)}>Cancel</Button>
                  <Button onClick={() => void save()} loading={saving} disabled={!valid}>Save</Button>
                </Group>
              </Group>
            )}
          </Stack>
        )}
      </Drawer>
    </>
  );
}
