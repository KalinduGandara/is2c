import { ActionIcon, Badge, Button, Group, Modal, Paper, Select, Stack, Switch, Tabs, Text } from '@mantine/core';
import { DateInput } from '@mantine/dates';
import { IconPlus, IconTrash } from '@tabler/icons-react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { api, type Page } from '../../api/client';
import { useCompany } from '../../app/CompanyContext';
import { useAuth } from '../../auth/AuthContext';
import { DataTable } from '../../components/DataTable';
import { showError, showSuccess } from '../../components/notify';
import { PageHeader } from '../../components/PageHeader';
import { toOptions, useDepartments, useBranches } from '../org/useOrgData';
import { TxnDrawer } from './TxnDrawer';
import { TYPE_LABEL, type Txn } from './types';

function TxnList({ path }: { path: string }) {
  const [open, setOpen] = useState<string | null>(null);
  const { data = [], isLoading } = useQuery({ queryKey: ['approvals', path], queryFn: () => api<Txn[]>(path) });
  return (
    <>
      <DataTable
        rows={data}
        loading={isLoading}
        rowKey={(r) => r.id}
        onRowClick={(r) => setOpen(r.id)}
        columns={[
          { key: 'type', header: 'Type', render: (r) => <Badge variant="light">{TYPE_LABEL[r.type] ?? r.type}</Badge> },
          { key: 'summary', header: 'Summary', render: (r) => r.summary },
          { key: 'by', header: 'Requested by', render: (r) => r.requesterName },
          { key: 'at', header: 'Submitted', render: (r) => new Date(r.submittedAt).toLocaleString() },
          { key: 'status', header: 'Status', render: (r) => <Badge>{r.status}</Badge> },
        ]}
      />
      {open && <TxnDrawer id={open} onClose={() => setOpen(null)} />}
    </>
  );
}

interface Delegation {
  id: string;
  delegatorName: string;
  delegateName: string;
  delegatorUserId: string;
  fromDate: string;
  toDate: string;
}

function DelegationsPanel() {
  const { can, user } = useAuth();
  const client = useQueryClient();
  const { data = [] } = useQuery({ queryKey: ['delegations'], queryFn: () => api<Delegation[]>('/approval/delegations') });
  const { data: users } = useQuery({
    queryKey: ['user-options'],
    queryFn: () => api<Page<{ id: string; displayName: string; username: string }>>('/admin/users', { query: { size: 200, active: true } }),
    enabled: can('ADMIN:VIEW'),
  });
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState<{ delegateUserId: string | null; fromDate: string | null; toDate: string | null }>({ delegateUserId: null, fromDate: null, toDate: null });
  const save = async () => {
    try {
      await api('/approval/delegations', { method: 'POST', body: form });
      showSuccess('Delegation created');
      setOpen(false);
      await client.invalidateQueries({ queryKey: ['delegations'] });
    } catch (e) {
      showError(e);
    }
  };
  return (
    <>
      <Group justify="flex-end" mb="sm"><Button leftSection={<IconPlus size={16} />} onClick={() => setOpen(true)}>Delegate my approvals</Button></Group>
      <DataTable
        rows={data}
        rowKey={(r) => r.id}
        columns={[
          { key: 'from', header: 'Delegator', render: (r) => r.delegatorName },
          { key: 'to', header: 'Delegate', render: (r) => r.delegateName },
          { key: 'period', header: 'Period', render: (r) => `${r.fromDate} → ${r.toDate}` },
          {
            key: 'x', header: '',
            render: (r) => r.delegatorUserId === user?.id && (
              <ActionIcon color="red" variant="subtle" aria-label="Revoke" onClick={async () => {
                await api(`/approval/delegations/${r.id}`, { method: 'DELETE' }).catch(showError);
                await client.invalidateQueries({ queryKey: ['delegations'] });
              }}><IconTrash size={16} /></ActionIcon>
            ),
          },
        ]}
      />
      <Modal opened={open} onClose={() => setOpen(false)} title="Delegate approvals">
        <Stack>
          {users ? (
            <Select label="Delegate" searchable data={users.content.filter((u) => u.id !== user?.id).map((u) => ({ value: u.id, label: `${u.displayName} (${u.username})` }))}
              value={form.delegateUserId} onChange={(v) => setForm({ ...form, delegateUserId: v })} />
          ) : (
            <Text size="sm" c="dimmed">Ask an administrator to set up a delegation to a specific user.</Text>
          )}
          <Group grow>
            <DateInput label="From" valueFormat="YYYY-MM-DD" value={form.fromDate} onChange={(v) => setForm({ ...form, fromDate: v ? String(v) : null })} />
            <DateInput label="To" valueFormat="YYYY-MM-DD" value={form.toDate} onChange={(v) => setForm({ ...form, toDate: v ? String(v) : null })} />
          </Group>
          <Group justify="flex-end"><Button disabled={!form.delegateUserId || !form.fromDate || !form.toDate} onClick={() => void save()}>Save</Button></Group>
        </Stack>
      </Modal>
    </>
  );
}

interface Workflow {
  id: string;
  branchId: string | null;
  departmentId: string | null;
  txnType: string;
  enabled: boolean;
  levels: { approverType: string; roleCode: string | null; userId: string | null }[];
  version: number;
}

function WorkflowsPanel({ companyId }: { companyId: string }) {
  const { can } = useAuth();
  const client = useQueryClient();
  const editable = can('APPROVAL:CONFIGURE');
  const { data = [] } = useQuery({ queryKey: ['workflows', companyId], queryFn: () => api<Workflow[]>('/approval/workflows', { query: { companyId } }) });
  const { data: roles = [] } = useQuery({ queryKey: ['roles'], queryFn: () => api<{ code: string; name: string }[]>('/admin/roles'), enabled: can('ADMIN:VIEW') });
  const { data: departments } = useDepartments(companyId);
  const { data: branches } = useBranches(companyId);
  const [editing, setEditing] = useState<Partial<Workflow> | null>(null);

  const save = async () => {
    if (!editing) return;
    const body = { ...editing, companyId };
    try {
      if (editing.id) await api(`/approval/workflows/${editing.id}`, { method: 'PUT', body });
      else await api('/approval/workflows', { method: 'POST', body });
      showSuccess();
      setEditing(null);
      await client.invalidateQueries({ queryKey: ['workflows'] });
    } catch (e) {
      showError(e);
    }
  };

  const scopeLabel = (w: Workflow) =>
    w.departmentId ? `Department ${departments?.find((d) => d.id === w.departmentId)?.code ?? ''}`
      : w.branchId ? `Branch ${branches?.find((b) => b.id === w.branchId)?.code ?? ''}` : 'Company';
  const levelLabel = (l: Workflow['levels'][number]) => (l.approverType === 'ROLE' ? l.roleCode : l.approverType === 'USER' ? 'Specific user' : 'Reporting manager');

  return (
    <>
      {editable && <Group justify="flex-end" mb="sm"><Button leftSection={<IconPlus size={16} />} onClick={() => setEditing({ txnType: 'LEAVE_REQUEST', enabled: true, levels: [{ approverType: 'REPORTING_MANAGER', roleCode: null, userId: null }] })}>Add workflow</Button></Group>}
      <DataTable
        rows={data}
        rowKey={(r) => r.id}
        onRowClick={editable ? (r) => setEditing(r) : undefined}
        columns={[
          { key: 'type', header: 'Type', render: (r) => TYPE_LABEL[r.txnType] ?? r.txnType },
          { key: 'scope', header: 'Applies to', render: scopeLabel },
          { key: 'levels', header: 'Levels', render: (r) => r.levels.map(levelLabel).join(' → ') },
          { key: 'enabled', header: 'Enabled', render: (r) => (r.enabled ? 'Yes' : 'No') },
        ]}
      />
      <Modal opened={editing !== null} onClose={() => setEditing(null)} title="Workflow" size="lg">
        {editing && (
          <Stack>
            <Select label="Transaction type" data={Object.entries(TYPE_LABEL).map(([value, label]) => ({ value, label }))} value={editing.txnType ?? null} disabled={!!editing.id}
              onChange={(v) => setEditing({ ...editing, txnType: v ?? undefined })} />
            <Group grow>
              <Select label="Branch (optional)" clearable data={toOptions(branches)} value={editing.branchId ?? null} onChange={(v) => setEditing({ ...editing, branchId: v, departmentId: v ? null : editing.departmentId })} />
              <Select label="Department (optional)" clearable data={toOptions(departments)} value={editing.departmentId ?? null} onChange={(v) => setEditing({ ...editing, departmentId: v, branchId: v ? null : editing.branchId })} />
            </Group>
            <Switch label="Enabled" checked={editing.enabled ?? true} onChange={(e) => setEditing({ ...editing, enabled: e.currentTarget.checked })} />
            {(editing.levels ?? []).map((l, i) => (
              <Paper withBorder p="xs" key={i}>
                <Group align="flex-end">
                  <Text size="sm" w={60}>Level {i + 1}</Text>
                  <Select label="Approver" data={[{ value: 'REPORTING_MANAGER', label: 'Reporting manager' }, { value: 'ROLE', label: 'Role' }]} value={l.approverType}
                    onChange={(v) => setEditing({ ...editing, levels: editing.levels!.map((x, j) => (j === i ? { ...x, approverType: v ?? 'ROLE' } : x)) })} />
                  {l.approverType === 'ROLE' && (
                    <Select label="Role" data={roles.map((r) => ({ value: r.code, label: r.name }))} value={l.roleCode}
                      onChange={(v) => setEditing({ ...editing, levels: editing.levels!.map((x, j) => (j === i ? { ...x, roleCode: v } : x)) })} />
                  )}
                  <ActionIcon color="red" variant="subtle" aria-label="Remove level" disabled={editing.levels!.length === 1}
                    onClick={() => setEditing({ ...editing, levels: editing.levels!.filter((_, j) => j !== i) })}><IconTrash size={16} /></ActionIcon>
                </Group>
              </Paper>
            ))}
            <Button variant="light" disabled={(editing.levels?.length ?? 0) >= 5}
              onClick={() => setEditing({ ...editing, levels: [...(editing.levels ?? []), { approverType: 'ROLE', roleCode: 'HR_MANAGER', userId: null }] })}>Add level</Button>
            <Group justify="space-between">
              {editing.id ? <Button color="red" variant="subtle" onClick={async () => {
                await api(`/approval/workflows/${editing.id}`, { method: 'DELETE' }).catch(showError);
                setEditing(null);
                await client.invalidateQueries({ queryKey: ['workflows'] });
              }}>Delete</Button> : <span />}
              <Button onClick={() => void save()}>Save</Button>
            </Group>
          </Stack>
        )}
      </Modal>
    </>
  );
}

export function ApprovalsPage() {
  const { t } = useTranslation();
  const { companyId } = useCompany();
  const { can } = useAuth();
  return (
    <>
      <PageHeader title={t('nav.approvals')} />
      <Tabs defaultValue="inbox" keepMounted={false}>
        <Tabs.List mb="md">
          <Tabs.Tab value="inbox">Inbox</Tabs.Tab>
          <Tabs.Tab value="mine">My requests</Tabs.Tab>
          <Tabs.Tab value="delegations">Delegations</Tabs.Tab>
          {companyId && can('APPROVAL:VIEW', 'APPROVAL:CONFIGURE') && <Tabs.Tab value="workflows">Workflows</Tabs.Tab>}
        </Tabs.List>
        <Tabs.Panel value="inbox"><TxnList path="/approval/inbox" /></Tabs.Panel>
        <Tabs.Panel value="mine"><TxnList path="/approval/my-requests" /></Tabs.Panel>
        <Tabs.Panel value="delegations"><DelegationsPanel /></Tabs.Panel>
        {companyId && <Tabs.Panel value="workflows"><WorkflowsPanel companyId={companyId} /></Tabs.Panel>}
      </Tabs>
    </>
  );
}
