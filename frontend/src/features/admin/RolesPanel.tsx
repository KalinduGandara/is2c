import { Badge, Button, Drawer, Group, Stack, TextInput } from '@mantine/core';
import { IconPlus } from '@tabler/icons-react';
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

  const save = async () => {
    if (!editing) return;
    try {
      const body = { code: editing.code, name: editing.name, description: editing.description, permissions: editing.permissions ?? [], version: editing.version };
      if (editing.id) await api(`/admin/roles/${editing.id}`, { method: 'PUT', body });
      else await api('/admin/roles', { method: 'POST', body });
      showSuccess();
      setEditing(null);
      await client.invalidateQueries({ queryKey: ['roles'] });
    } catch (e) {
      showError(e);
    }
  };

  return (
    <>
      {editable && (
        <Group justify="flex-end" mb="sm">
          <Button leftSection={<IconPlus size={16} />} onClick={() => setEditing({ permissions: [] })}>
            Create role
          </Button>
        </Group>
      )}
      <DataTable
        rows={roles}
        loading={isLoading}
        rowKey={(r) => r.id}
        onRowClick={(r) => setEditing(r)}
        columns={[
          { key: 'code', header: 'Code', render: (r) => r.code },
          { key: 'name', header: 'Name', render: (r) => r.name },
          { key: 'type', header: 'Type', render: (r) => <Badge variant="light">{r.system ? 'System' : 'Custom'}</Badge> },
          { key: 'perms', header: 'Permissions', render: (r) => (r.code === 'SUPER_ADMIN' ? 'All' : r.permissions.length) },
        ]}
      />
      <Drawer opened={editing !== null} onClose={() => setEditing(null)} title={editing?.id ? `Role ${editing.code}` : 'Create role'} position="right" size="xl">
        {editing && catalog && (
          <Stack>
            {!editing.id && (
              <TextInput label="Code" withAsterisk value={editing.code ?? ''} onChange={(e) => setEditing({ ...editing, code: e.currentTarget.value.toUpperCase() })} />
            )}
            <TextInput label="Name" withAsterisk disabled={editing.system} value={editing.name ?? ''} onChange={(e) => setEditing({ ...editing, name: e.currentTarget.value })} />
            <TextInput label="Description" value={editing.description ?? ''} onChange={(e) => setEditing({ ...editing, description: e.currentTarget.value })} />
            <PermissionMatrix
              catalog={catalog}
              value={editing.permissions ?? []}
              disabled={!editable || editing.code === 'SUPER_ADMIN'}
              onChange={(permissions) => setEditing({ ...editing, permissions })}
            />
            {editable && editing.code !== 'SUPER_ADMIN' && (
              <Group justify="space-between">
                {editing.id && !editing.system ? (
                  <Button color="red" variant="light" onClick={async () => {
                    try {
                      await api(`/admin/roles/${editing.id}`, { method: 'DELETE' });
                      setEditing(null);
                      await client.invalidateQueries({ queryKey: ['roles'] });
                    } catch (e) {
                      showError(e);
                    }
                  }}>Delete</Button>
                ) : <span />}
                <Button onClick={() => void save()}>Save</Button>
              </Group>
            )}
          </Stack>
        )}
      </Drawer>
    </>
  );
}
