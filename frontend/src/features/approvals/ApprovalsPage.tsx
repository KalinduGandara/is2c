import {
  ActionIcon,
  Avatar,
  Badge,
  Box,
  Button,
  Divider,
  Group,
  Modal,
  Paper,
  Select,
  SimpleGrid,
  Stack,
  Switch,
  Tabs,
  Text,
  TextInput,
  ThemeIcon,
  Tooltip,
} from '@mantine/core';
import { DateInput } from '@mantine/dates';
import { modals } from '@mantine/modals';
import {
  IconArrowRight,
  IconChecklist,
  IconCircleCheck,
  IconInbox,
  IconPlus,
  IconSearch,
  IconSend,
  IconSitemap,
  IconTrash,
  IconUserShare,
} from '@tabler/icons-react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { api, type Page } from '../../api/client';
import { useCompany } from '../../app/CompanyContext';
import { useAuth } from '../../auth/AuthContext';
import { DataTable } from '../../components/DataTable';
import { formatDate, formatDateTime, humanize } from '../../components/format';
import { showError, showSuccess } from '../../components/notify';
import { PageHeader } from '../../components/PageHeader';
import { StatusBadge } from '../../components/StatusBadge';
import { useUrlTab } from '../../components/useUrlTab';
import { toOptions, useDepartments, useBranches } from '../org/useOrgData';
import { TxnDrawer } from './TxnDrawer';
import { levelLabel, relativeTime, TYPE_COLOR, TYPE_LABEL, typeIcon, type ApprovalLevel, type Txn } from './types';

const INBOX = '/approval/inbox';
const MINE = '/approval/my-requests';

function useTxns(path: string) {
  return useQuery({ queryKey: ['approvals', path], queryFn: () => api<Txn[]>(path) });
}

function TypeCell({ type }: { type: string }) {
  const color = TYPE_COLOR[type] ?? 'gray';
  return (
    <Group gap="xs" wrap="nowrap">
      <ThemeIcon variant="light" color={color} size={28} radius="md">
        {typeIcon(type)}
      </ThemeIcon>
      <Text size="sm" fw={500} style={{ whiteSpace: 'nowrap' }}>{TYPE_LABEL[type] ?? humanize(type)}</Text>
    </Group>
  );
}

function TxnList({ path }: { path: string }) {
  const [open, setOpen] = useState<string | null>(null);
  const [search, setSearch] = useState('');
  const [type, setType] = useState<string | null>(null);
  const { data = [], isLoading } = useTxns(path);
  const inbox = path === INBOX;
  const q = search.trim().toLowerCase();
  const rows = data.filter(
    (r) => (!type || r.type === type) && (!q || `${r.summary} ${r.requesterName ?? ''}`.toLowerCase().includes(q)),
  );
  const types = [...new Set(data.map((r) => r.type))];
  const filtered = !!q || !!type;
  return (
    <Stack gap="md">
      {data.length > 0 && (
        <Group gap="sm" wrap="wrap">
          <TextInput
            aria-label="Search requests"
            placeholder="Search requests"
            leftSection={<IconSearch size={16} />}
            value={search}
            onChange={(e) => setSearch(e.currentTarget.value)}
            w={{ base: '100%', sm: 240 }}
          />
          {types.length > 1 && (
            <Select
              aria-label="Request type"
              placeholder="All types"
              clearable
              data={types.map((v) => ({ value: v, label: TYPE_LABEL[v] ?? humanize(v) }))}
              value={type}
              onChange={setType}
              w={{ base: '100%', sm: 200 }}
            />
          )}
        </Group>
      )}
      <DataTable
        rows={rows}
        loading={isLoading}
        rowKey={(r) => r.id}
        onRowClick={(r) => setOpen(r.id)}
        rowLabel={(r) => `${TYPE_LABEL[r.type] ?? humanize(r.type)}: ${r.summary}`}
        minWidth={760}
        emptyIcon={inbox && !filtered ? <IconCircleCheck size={20} stroke={1.5} /> : undefined}
        emptyTitle={filtered ? 'No matching requests' : inbox ? 'You’re all caught up' : 'No requests yet'}
        emptyDescription={
          filtered
            ? 'Try a different search or type.'
            : inbox
              ? 'Nothing is waiting for your decision right now.'
              : 'Requests you submit — leave, attendance corrections and more — will appear here.'
        }
        columns={[
          { key: 'type', header: 'Type', render: (r) => <TypeCell type={r.type} /> },
          { key: 'summary', header: 'Summary', render: (r) => <Text size="sm" lineClamp={2}>{r.summary}</Text> },
          {
            key: 'by',
            header: 'Requested by',
            render: (r) => (
              <Group gap="sm" wrap="nowrap">
                <Avatar name={r.requesterName ?? 'System'} color="initials" size="sm" radius="xl" />
                <Text size="sm" style={{ whiteSpace: 'nowrap' }}>{r.requesterName ?? 'System'}</Text>
              </Group>
            ),
          },
          {
            key: 'at',
            header: 'Submitted',
            render: (r) => (
              <Tooltip label={formatDateTime(r.submittedAt)} withArrow>
                <Text size="sm" c="dimmed" w="fit-content" style={{ whiteSpace: 'nowrap' }}>{relativeTime(r.submittedAt)}</Text>
              </Tooltip>
            ),
          },
          {
            key: 'status',
            header: 'Status',
            width: 130,
            render: (r) => (
              <Group gap={6} wrap="nowrap">
                <StatusBadge status={r.status} />
                {r.status === 'PENDING' && r.currentLevel > 1 && <Text size="xs" c="dimmed">L{r.currentLevel}</Text>}
              </Group>
            ),
          },
        ]}
      />
      {open && <TxnDrawer id={open} onClose={() => setOpen(null)} />}
    </Stack>
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

function delegationState(d: Delegation, today: string) {
  if (d.toDate < today) return { label: 'Ended', color: 'gray' };
  if (d.fromDate > today) return { label: 'Scheduled', color: 'blue' };
  return { label: 'Active', color: 'teal' };
}

function DelegationsPanel() {
  const { can, user } = useAuth();
  const client = useQueryClient();
  const { data = [], isLoading } = useQuery({ queryKey: ['delegations'], queryFn: () => api<Delegation[]>('/approval/delegations') });
  const { data: users } = useQuery({
    queryKey: ['user-options'],
    queryFn: () => api<Page<{ id: string; displayName: string; username: string }>>('/admin/users', { query: { size: 200, active: true } }),
    enabled: can('ADMIN:VIEW'),
  });
  const [open, setOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form, setForm] = useState<{ delegateUserId: string | null; fromDate: string | null; toDate: string | null }>({ delegateUserId: null, fromDate: null, toDate: null });
  const today = new Date().toISOString().slice(0, 10);
  const save = async () => {
    setSaving(true);
    try {
      await api('/approval/delegations', { method: 'POST', body: form });
      showSuccess('Delegation created');
      setOpen(false);
      setForm({ delegateUserId: null, fromDate: null, toDate: null });
      await client.invalidateQueries({ queryKey: ['delegations'] });
    } catch (e) {
      showError(e);
    } finally {
      setSaving(false);
    }
  };
  const revoke = (d: Delegation) =>
    modals.openConfirmModal({
      title: 'Revoke delegation?',
      children: (
        <Text size="sm">
          {d.delegateName} will no longer be able to approve on your behalf ({formatDate(d.fromDate)} – {formatDate(d.toDate)}).
        </Text>
      ),
      labels: { confirm: 'Revoke', cancel: 'Keep' },
      confirmProps: { color: 'red' },
      onConfirm: async () => {
        try {
          await api(`/approval/delegations/${d.id}`, { method: 'DELETE' });
          showSuccess('Delegation revoked');
        } catch (e) {
          showError(e);
        }
        await client.invalidateQueries({ queryKey: ['delegations'] });
      },
    });
  const invalidRange = !!form.fromDate && !!form.toDate && form.toDate < form.fromDate;
  return (
    <Stack gap="md">
      <Group justify="space-between" wrap="wrap" gap="sm">
        <Text size="sm" c="dimmed" maw={560}>
          Let a colleague approve requests on your behalf while you are away. Their decisions are recorded as made by a delegate.
        </Text>
        <Button leftSection={<IconPlus size={16} />} onClick={() => setOpen(true)}>Delegate my approvals</Button>
      </Group>
      <DataTable
        rows={data}
        loading={isLoading}
        rowKey={(r) => r.id}
        emptyIcon={<IconUserShare size={20} stroke={1.5} />}
        emptyTitle="No delegations"
        emptyDescription="Delegations you create or receive will appear here."
        columns={[
          {
            key: 'pair',
            header: 'Delegation',
            render: (r) => (
              <Group gap="xs" wrap="nowrap">
                <Avatar name={r.delegatorName} color="initials" size="sm" radius="xl" />
                <Text size="sm" style={{ whiteSpace: 'nowrap' }}>{r.delegatorName}</Text>
                <IconArrowRight size={14} color="var(--mantine-color-dimmed)" style={{ flexShrink: 0 }} />
                <Avatar name={r.delegateName} color="initials" size="sm" radius="xl" />
                <Text size="sm" style={{ whiteSpace: 'nowrap' }}>{r.delegateName}</Text>
              </Group>
            ),
          },
          { key: 'period', header: 'Period', render: (r) => <Text size="sm" style={{ whiteSpace: 'nowrap' }}>{formatDate(r.fromDate)} – {formatDate(r.toDate)}</Text> },
          {
            key: 'state',
            header: 'Status',
            render: (r) => {
              const s = delegationState(r, today);
              return <StatusBadge status={s.label.toUpperCase()} label={s.label} colors={{ [s.label.toUpperCase()]: s.color }} />;
            },
          },
          {
            key: 'x',
            header: '',
            align: 'right',
            render: (r) =>
              r.delegatorUserId === user?.id && (
                <Tooltip label="Revoke delegation">
                  <ActionIcon color="red" variant="subtle" aria-label="Revoke" onClick={() => revoke(r)}>
                    <IconTrash size={16} />
                  </ActionIcon>
                </Tooltip>
              ),
          },
        ]}
      />
      <Modal opened={open} onClose={() => setOpen(false)} title="Delegate approvals">
        <Stack>
          {users ? (
            <Select
              label="Delegate"
              placeholder="Choose a user"
              searchable
              withAsterisk
              nothingFoundMessage="No matching users"
              data={users.content.filter((u) => u.id !== user?.id).map((u) => ({ value: u.id, label: `${u.displayName} (${u.username})` }))}
              value={form.delegateUserId}
              onChange={(v) => setForm({ ...form, delegateUserId: v })}
            />
          ) : (
            <Paper withBorder p="sm">
              <Text size="sm" c="dimmed">Ask an administrator to set up a delegation to a specific user.</Text>
            </Paper>
          )}
          <SimpleGrid cols={{ base: 1, xs: 2 }}>
            <DateInput label="From" withAsterisk valueFormat="D MMM YYYY" placeholder="Start date" value={form.fromDate} onChange={(v) => setForm({ ...form, fromDate: v ? String(v) : null })} />
            <DateInput
              label="To"
              withAsterisk
              valueFormat="D MMM YYYY"
              placeholder="End date"
              minDate={form.fromDate ?? undefined}
              value={form.toDate}
              onChange={(v) => setForm({ ...form, toDate: v ? String(v) : null })}
              error={invalidRange ? 'Must be on or after the start date' : undefined}
            />
          </SimpleGrid>
          <Group justify="flex-end">
            <Button variant="default" onClick={() => setOpen(false)}>Cancel</Button>
            <Button loading={saving} disabled={!form.delegateUserId || !form.fromDate || !form.toDate || invalidRange} onClick={() => void save()}>
              Save
            </Button>
          </Group>
        </Stack>
      </Modal>
    </Stack>
  );
}

interface Workflow {
  id: string;
  branchId: string | null;
  departmentId: string | null;
  txnType: string;
  enabled: boolean;
  levels: ApprovalLevel[];
  version: number;
}

const APPROVER_TYPES = [
  { value: 'REPORTING_MANAGER', label: 'Reporting manager' },
  { value: 'ROLE', label: 'Role' },
];

function WorkflowsPanel({ companyId }: { companyId: string }) {
  const { can } = useAuth();
  const client = useQueryClient();
  const editable = can('APPROVAL:CONFIGURE');
  const { data = [], isLoading } = useQuery({ queryKey: ['workflows', companyId], queryFn: () => api<Workflow[]>('/approval/workflows', { query: { companyId } }) });
  const { data: roles = [] } = useQuery({ queryKey: ['roles'], queryFn: () => api<{ code: string; name: string }[]>('/admin/roles'), enabled: can('ADMIN:VIEW') });
  const { data: departments } = useDepartments(companyId);
  const { data: branches } = useBranches(companyId);
  const [editing, setEditing] = useState<Partial<Workflow> | null>(null);
  const [saving, setSaving] = useState(false);
  const roleName = (code: string) => roles.find((r) => r.code === code)?.name;

  const save = async () => {
    if (!editing) return;
    const body = { ...editing, companyId };
    setSaving(true);
    try {
      if (editing.id) await api(`/approval/workflows/${editing.id}`, { method: 'PUT', body });
      else await api('/approval/workflows', { method: 'POST', body });
      showSuccess();
      setEditing(null);
      await client.invalidateQueries({ queryKey: ['workflows'] });
    } catch (e) {
      showError(e);
    } finally {
      setSaving(false);
    }
  };

  const remove = (w: Partial<Workflow>) =>
    modals.openConfirmModal({
      title: 'Delete workflow?',
      children: <Text size="sm">The {TYPE_LABEL[w.txnType ?? ''] ?? ''} workflow for this scope will be permanently removed.</Text>,
      labels: { confirm: 'Delete', cancel: 'Keep' },
      confirmProps: { color: 'red' },
      onConfirm: async () => {
        try {
          await api(`/approval/workflows/${w.id}`, { method: 'DELETE' });
          showSuccess('Workflow deleted');
          setEditing(null);
        } catch (e) {
          showError(e);
        }
        await client.invalidateQueries({ queryKey: ['workflows'] });
      },
    });

  const scope = (w: Workflow) =>
    w.departmentId
      ? { kind: 'Department', name: departments?.find((d) => d.id === w.departmentId)?.code ?? '' }
      : w.branchId
        ? { kind: 'Branch', name: branches?.find((b) => b.id === w.branchId)?.code ?? '' }
        : { kind: 'Company', name: 'All employees' };

  const setLevel = (i: number, patch: Partial<ApprovalLevel>) =>
    editing && setEditing({ ...editing, levels: editing.levels!.map((x, j) => (j === i ? { ...x, ...patch } : x)) });
  const levels = editing?.levels ?? [];
  const incomplete = !editing?.txnType || levels.some((l) => l.approverType === 'ROLE' && !l.roleCode);

  return (
    <Stack gap="md">
      <Group justify="space-between" wrap="wrap" gap="sm">
        <Text size="sm" c="dimmed" maw={560}>
          Who approves each kind of request. The most specific workflow (department, then branch, then company) applies.
        </Text>
        {editable && (
          <Button
            leftSection={<IconPlus size={16} />}
            onClick={() => setEditing({ txnType: 'LEAVE_REQUEST', enabled: true, levels: [{ approverType: 'REPORTING_MANAGER', roleCode: null, userId: null }] })}
          >
            Add workflow
          </Button>
        )}
      </Group>
      <DataTable
        rows={data}
        loading={isLoading}
        rowKey={(r) => r.id}
        onRowClick={editable ? (r) => setEditing(r) : undefined}
        rowLabel={(r) => `${TYPE_LABEL[r.txnType] ?? humanize(r.txnType)} workflow — ${scope(r).kind} ${scope(r).name}`}
        minWidth={720}
        emptyIcon={<IconSitemap size={20} stroke={1.5} />}
        emptyTitle="No workflows"
        emptyDescription="Without a workflow, requests are routed to the reporting manager by default."
        columns={[
          { key: 'type', header: 'Type', render: (r) => <TypeCell type={r.txnType} /> },
          {
            key: 'scope',
            header: 'Applies to',
            render: (r) => {
              const s = scope(r);
              return (
                <div>
                  <Text size="sm">{s.kind}</Text>
                  <Text size="xs" c="dimmed">{s.name}</Text>
                </div>
              );
            },
          },
          {
            key: 'levels',
            header: 'Approval chain',
            render: (r) => (
              <Group gap={4} wrap="wrap">
                {r.levels.map((l, i) => (
                  <Group key={i} gap={4} wrap="nowrap">
                    {i > 0 && <IconArrowRight size={12} color="var(--mantine-color-dimmed)" />}
                    <Badge variant="light" color="gray" leftSection={<Text span size="xs" fw={700}>{i + 1}</Text>}>
                      {levelLabel(l, roleName)}
                    </Badge>
                  </Group>
                ))}
              </Group>
            ),
          },
          { key: 'enabled', header: 'Status', render: (r) => <StatusBadge status={r.enabled ? 'ENABLED' : 'DISABLED'} colors={{ ENABLED: 'teal', DISABLED: 'gray' }} /> },
        ]}
      />
      <Modal opened={editing !== null} onClose={() => setEditing(null)} title={editing?.id ? 'Edit workflow' : 'New workflow'} size="lg">
        {editing && (
          <Stack gap="md">
            <Select
              label="Transaction type"
              data={Object.entries(TYPE_LABEL).map(([value, label]) => ({ value, label }))}
              value={editing.txnType ?? null}
              disabled={!!editing.id}
              description={editing.id ? 'The type of an existing workflow cannot be changed.' : undefined}
              onChange={(v) => setEditing({ ...editing, txnType: v ?? undefined })}
            />
            <SimpleGrid cols={{ base: 1, sm: 2 }}>
              <Select
                label="Branch (optional)"
                placeholder="Any branch"
                clearable
                data={toOptions(branches)}
                value={editing.branchId ?? null}
                onChange={(v) => setEditing({ ...editing, branchId: v, departmentId: v ? null : editing.departmentId })}
              />
              <Select
                label="Department (optional)"
                placeholder="Any department"
                clearable
                data={toOptions(departments)}
                value={editing.departmentId ?? null}
                onChange={(v) => setEditing({ ...editing, departmentId: v, branchId: v ? null : editing.branchId })}
              />
            </SimpleGrid>
            <Text size="xs" c="dimmed" mt={-8}>Leave both empty to apply company-wide. Choose either a branch or a department, not both.</Text>
            <Switch label="Enabled" checked={editing.enabled ?? true} onChange={(e) => setEditing({ ...editing, enabled: e.currentTarget.checked })} />

            <Divider label="Approval levels" labelPosition="left" />
            <Stack gap={0}>
              {levels.map((l, i) => (
                <Group key={i} align="stretch" gap="sm" wrap="nowrap">
                  <Stack align="center" gap={0}>
                    <ThemeIcon radius="xl" size={28} variant="light">
                      <Text size="sm" fw={700}>{i + 1}</Text>
                    </ThemeIcon>
                    {i < levels.length - 1 && <Box style={{ flex: 1, width: 2, background: 'var(--app-border)', minHeight: 12 }} />}
                  </Stack>
                  <Paper withBorder p="sm" mb={i < levels.length - 1 ? 'sm' : 0} style={{ flex: 1, minWidth: 0 }}>
                    <Group align="flex-end" gap="sm" wrap="wrap">
                      <Select
                        label={`Level ${i + 1} approver`}
                        data={APPROVER_TYPES}
                        value={l.approverType}
                        onChange={(v) => setLevel(i, { approverType: v ?? 'ROLE' })}
                        w={{ base: '100%', sm: 200 }}
                        allowDeselect={false}
                      />
                      {l.approverType === 'ROLE' && (
                        <Select
                          label="Role"
                          placeholder="Choose a role"
                          data={roles.map((r) => ({ value: r.code, label: r.name }))}
                          value={l.roleCode}
                          onChange={(v) => setLevel(i, { roleCode: v })}
                          error={!l.roleCode ? 'Required' : undefined}
                          style={{ flex: 1, minWidth: 160 }}
                        />
                      )}
                      <Tooltip label={levels.length === 1 ? 'At least one level is required' : 'Remove level'}>
                        <ActionIcon
                          color="red"
                          variant="subtle"
                          size={36}
                          aria-label="Remove level"
                          disabled={levels.length === 1}
                          onClick={() => setEditing({ ...editing, levels: levels.filter((_, j) => j !== i) })}
                          ml="auto"
                        >
                          <IconTrash size={16} />
                        </ActionIcon>
                      </Tooltip>
                    </Group>
                  </Paper>
                </Group>
              ))}
            </Stack>
            <Button
              variant="light"
              leftSection={<IconPlus size={16} />}
              disabled={levels.length >= 5}
              onClick={() => setEditing({ ...editing, levels: [...levels, { approverType: 'ROLE', roleCode: 'HR_MANAGER', userId: null }] })}
              w="fit-content"
            >
              Add level
            </Button>
            {levels.length >= 5 && <Text size="xs" c="dimmed">A workflow can have up to 5 levels.</Text>}

            <Divider />
            <Group justify="space-between" wrap="wrap">
              {editing.id ? (
                <Button color="red" variant="subtle" leftSection={<IconTrash size={16} />} onClick={() => remove(editing)}>
                  Delete
                </Button>
              ) : (
                <span />
              )}
              <Group gap="xs">
                <Button variant="default" onClick={() => setEditing(null)}>Cancel</Button>
                <Button loading={saving} disabled={incomplete} onClick={() => void save()}>Save</Button>
              </Group>
            </Group>
          </Stack>
        )}
      </Modal>
    </Stack>
  );
}

const TABS = ['inbox', 'mine', 'delegations', 'workflows'];

export function ApprovalsPage() {
  const { t } = useTranslation();
  const { companyId } = useCompany();
  const { can } = useAuth();
  const [tab, setTab] = useUrlTab('inbox', TABS);
  const { data: inbox } = useTxns(INBOX);
  const inboxCount = inbox?.length ?? 0;
  const showWorkflows = !!companyId && can('APPROVAL:VIEW', 'APPROVAL:CONFIGURE');
  return (
    <>
      <PageHeader title={t('nav.approvals')} icon={<IconChecklist size={22} />} description={t('navHint.approvals')} />
      <Tabs value={tab === 'workflows' && !showWorkflows ? 'inbox' : tab} onChange={setTab} keepMounted={false}>
        <Tabs.List className="app-tabs-list" mb="lg">
          <Tabs.Tab
            value="inbox"
            leftSection={<IconInbox size={16} />}
            rightSection={inboxCount > 0 ? <Badge size="sm" variant="filled" circle={inboxCount < 10}>{inboxCount}</Badge> : undefined}
          >
            Inbox
          </Tabs.Tab>
          <Tabs.Tab value="mine" leftSection={<IconSend size={16} />}>My requests</Tabs.Tab>
          <Tabs.Tab value="delegations" leftSection={<IconUserShare size={16} />}>Delegations</Tabs.Tab>
          {showWorkflows && <Tabs.Tab value="workflows" leftSection={<IconSitemap size={16} />}>Workflows</Tabs.Tab>}
        </Tabs.List>
        <Tabs.Panel value="inbox"><TxnList path={INBOX} /></Tabs.Panel>
        <Tabs.Panel value="mine"><TxnList path={MINE} /></Tabs.Panel>
        <Tabs.Panel value="delegations"><DelegationsPanel /></Tabs.Panel>
        {showWorkflows && <Tabs.Panel value="workflows"><WorkflowsPanel companyId={companyId} /></Tabs.Panel>}
      </Tabs>
    </>
  );
}
