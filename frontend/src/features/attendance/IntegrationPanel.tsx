import { ActionIcon, Alert, Badge, Button, Code, CopyButton, FileInput, Group, List, Modal, Paper, PasswordInput, Select, Stack, Text, TextInput, Title, Tooltip } from '@mantine/core';
import { modals } from '@mantine/modals';
import {
  IconAlertTriangle,
  IconCheck,
  IconCopy,
  IconFileTypeCsv,
  IconKey,
  IconLink,
  IconPlayerPause,
  IconPlayerPlay,
  IconPlus,
  IconUpload,
} from '@tabler/icons-react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { api, type Page } from '../../api/client';
import { useAuth } from '../../auth/AuthContext';
import { DataTable } from '../../components/DataTable';
import { StatusBadge } from '../../components/StatusBadge';
import { formatDate, humanize } from '../../components/format';
import { showError, showSuccess } from '../../components/notify';

interface Source {
  id: string;
  name: string;
  adapterType: string;
  keyPrefix: string | null;
  systemSource: boolean;
  active: boolean;
  apiKey: string | null;
}

interface IngestResult {
  accepted: number;
  duplicates: number;
  unmatched: number;
  rejected: number;
  errors: { row: number; reason: string }[];
}

const ADAPTERS = ['GENERIC_JSON', 'CSV'];

function CopyIcon({ value, label }: { value: string; label: string }) {
  return (
    <CopyButton value={value} timeout={2000}>
      {({ copied, copy }) => (
        <Tooltip label={copied ? 'Copied' : label} withArrow>
          <ActionIcon variant="subtle" color={copied ? 'teal' : 'gray'} aria-label={label} onClick={copy}>
            {copied ? <IconCheck size={16} /> : <IconCopy size={16} />}
          </ActionIcon>
        </Tooltip>
      )}
    </CopyButton>
  );
}

/** FR-045..047 CSV import, attendance sources (API keys) and unmatched punches. */
export function IntegrationPanel({ companyId }: { companyId: string }) {
  const { can } = useAuth();
  const client = useQueryClient();
  const [file, setFile] = useState<File | null>(null);
  const [result, setResult] = useState<IngestResult | null>(null);
  const [newKey, setNewKey] = useState<string | null>(null);
  const [name, setName] = useState('');
  const [adapter, setAdapter] = useState<string | null>('GENERIC_JSON');
  const [busy, setBusy] = useState<string | null>(null);
  const { data: sources = [], isLoading: sourcesLoading } = useQuery({
    queryKey: ['attendance-sources', companyId],
    queryFn: () => api<Source[]>(`/companies/${companyId}/attendance-sources`),
  });
  const { data: unmatched, isLoading: unmatchedLoading } = useQuery({
    queryKey: ['unmatched', companyId],
    queryFn: () => api<Page<{ id: string; employeeIdentifier: string; eventTime: string; direction: string }>>('/attendance/unmatched-events', { query: { companyId, size: 50 } }),
  });

  const run = async (tag: string, fn: () => Promise<void>) => {
    setBusy(tag);
    try {
      await fn();
    } catch (e) {
      showError(e);
    } finally {
      setBusy(null);
    }
  };

  const upload = () =>
    run('upload', async () => {
      if (!file) return;
      const form = new FormData();
      form.append('file', file);
      setResult(await api<IngestResult>(`/attendance/import?companyId=${companyId}`, { method: 'POST', body: form }));
      await client.invalidateQueries({ queryKey: ['unmatched'] });
    });

  const createSource = () =>
    run('create', async () => {
      const s = await api<Source>(`/companies/${companyId}/attendance-sources`, { method: 'POST', body: { name, adapterType: adapter } });
      setNewKey(s.apiKey);
      setName('');
      await client.invalidateQueries({ queryKey: ['attendance-sources'] });
    });

  const sourceAction = (s: Source, action: 'regenerate-key' | 'deactivate' | 'activate') =>
    run(`${s.id}:${action}`, async () => {
      const r = await api<Source>(`/attendance-sources/${s.id}/${action}`, { method: 'POST' });
      if (r.apiKey) setNewKey(r.apiKey);
      else showSuccess(action === 'deactivate' ? 'Source deactivated' : 'Source activated');
      await client.invalidateQueries({ queryKey: ['attendance-sources'] });
    });

  const confirmRegenerate = (s: Source) =>
    modals.openConfirmModal({
      title: 'Generate a new API key?',
      children: <Text size="sm">The current key for “{s.name}” stops working immediately. Devices using it must be updated with the new key.</Text>,
      labels: { confirm: 'Generate new key', cancel: 'Cancel' },
      confirmProps: { color: 'red' },
      onConfirm: () => void sourceAction(s, 'regenerate-key'),
    });

  const confirmDeactivate = (s: Source) =>
    modals.openConfirmModal({
      title: 'Deactivate source?',
      children: <Text size="sm">Punches sent with the “{s.name}” key will be rejected until the source is activated again.</Text>,
      labels: { confirm: 'Deactivate', cancel: 'Cancel' },
      confirmProps: { color: 'red' },
      onConfirm: () => void sourceAction(s, 'deactivate'),
    });

  const rematch = () =>
    run('rematch', async () => {
      const r = await api<{ matched: number }>(`/attendance/rematch?companyId=${companyId}`, { method: 'POST' });
      showSuccess(`${r.matched} punches matched`);
      await client.invalidateQueries({ queryKey: ['unmatched'] });
    });

  const configure = can('ATTENDANCE:CONFIGURE');
  return (
    <Stack gap="lg">
      {can('ATTENDANCE:ADD') && (
        <Paper withBorder p="lg">
          <Group gap="xs" mb={4}>
            <IconFileTypeCsv size={20} />
            <Title order={4}>Import punches from CSV</Title>
          </Group>
          <Text size="sm" c="dimmed" mb="md">
            Columns: employee badge or number, timestamp (<Code>2026-03-16T08:01:00</Code>), direction (IN/OUT, optional), device (optional).
          </Text>
          <Group align="flex-end" wrap="wrap" gap="sm">
            <FileInput label="CSV file" placeholder="Choose a .csv file" leftSection={<IconFileTypeCsv size={16} />} accept=".csv,text/csv" clearable value={file} onChange={setFile} w={{ base: '100%', sm: 320 }} />
            <Button leftSection={<IconUpload size={16} />} onClick={() => void upload()} disabled={!file} loading={busy === 'upload'}>Import</Button>
          </Group>
          {result && (
            <Alert mt="md" color={result.rejected ? 'yellow' : 'green'} variant="light" title={result.rejected ? 'Imported with some rejected rows' : 'Import complete'} withCloseButton onClose={() => setResult(null)}>
              <Group gap="xs" wrap="wrap" mb={result.errors.length ? 'xs' : 0}>
                <Badge color="green" variant="light">Accepted {result.accepted}</Badge>
                <Badge color="gray" variant="light">Duplicates {result.duplicates}</Badge>
                <Badge color="yellow" variant="light">Unmatched {result.unmatched}</Badge>
                <Badge color="red" variant="light">Rejected {result.rejected}</Badge>
              </Group>
              {result.errors.length > 0 && (
                <List size="xs" spacing={2}>
                  {result.errors.slice(0, 10).map((e) => <List.Item key={e.row}>Row {e.row}: {e.reason}</List.Item>)}
                </List>
              )}
            </Alert>
          )}
        </Paper>
      )}

      <Paper withBorder p="lg">
        <Group gap="xs" mb={4}>
          <IconLink size={20} />
          <Title order={4}>Attendance sources</Title>
        </Group>
        <Text size="sm" c="dimmed" mb="md">Devices and systems that push punches through the integration API, each with its own key.</Text>
        {configure && (
          <Group align="flex-end" wrap="wrap" gap="sm" mb="md">
            <TextInput label="Source name" placeholder="e.g. Main gate terminal" value={name} onChange={(e) => setName(e.currentTarget.value)} w={{ base: '100%', sm: 260 }} />
            <Select label="Format" data={ADAPTERS.map((v) => ({ value: v, label: v === 'GENERIC_JSON' ? 'Generic JSON' : 'CSV' }))} value={adapter} allowDeselect={false} onChange={setAdapter} w={{ base: '100%', sm: 160 }} />
            <Button leftSection={<IconPlus size={16} />} onClick={() => void createSource()} disabled={!name.trim()} loading={busy === 'create'}>Add source</Button>
          </Group>
        )}
        <DataTable
          bare
          rows={sources}
          loading={sourcesLoading}
          rowKey={(s) => s.id}
          minWidth={560}
          emptyTitle="No attendance sources"
          emptyDescription={configure ? 'Add a source to get an API key for a device or system.' : undefined}
          columns={[
            {
              key: 'name', header: 'Name',
              render: (s) => (
                <Group gap={6} wrap="nowrap">
                  <Text size="sm" fw={500}>{s.name}</Text>
                  {s.systemSource && <Badge size="xs" variant="outline" color="gray">System</Badge>}
                </Group>
              ),
            },
            { key: 'type', header: 'Format', render: (s) => (s.adapterType === 'GENERIC_JSON' ? 'Generic JSON' : humanize(s.adapterType)) },
            {
              key: 'key', header: 'API key',
              render: (s) => (s.keyPrefix ? (
                <Group gap={6} wrap="nowrap">
                  <IconKey size={14} />
                  <Code>hrm_{s.keyPrefix}_••••••••</Code>
                </Group>
              ) : <Text size="sm" c="dimmed">—</Text>),
            },
            { key: 'status', header: 'Status', render: (s) => <StatusBadge status={s.active ? 'ACTIVE' : 'INACTIVE'} /> },
            {
              key: 'actions', header: '', align: 'right',
              render: (s) =>
                configure && !s.systemSource ? (
                  <Group gap={4} justify="flex-end" wrap="nowrap">
                    <Button size="xs" variant="subtle" leftSection={<IconKey size={14} />} loading={busy === `${s.id}:regenerate-key`} onClick={() => confirmRegenerate(s)}>New key</Button>
                    {s.active ? (
                      <Button size="xs" variant="subtle" color="red" leftSection={<IconPlayerPause size={14} />} loading={busy === `${s.id}:deactivate`} onClick={() => confirmDeactivate(s)}>Deactivate</Button>
                    ) : (
                      <Button size="xs" variant="subtle" color="teal" leftSection={<IconPlayerPlay size={14} />} loading={busy === `${s.id}:activate`} onClick={() => void sourceAction(s, 'activate')}>Activate</Button>
                    )}
                  </Group>
                ) : null,
            },
          ]}
        />
      </Paper>

      <Paper withBorder p="lg">
        <Group justify="space-between" mb="md" wrap="wrap" gap="sm">
          <div>
            <Title order={4}>Unmatched punches</Title>
            <Text size="sm" c="dimmed">Punches whose identifier did not match an employee badge or number.</Text>
          </div>
          {can('ATTENDANCE:EDIT') && (
            <Button variant="light" onClick={() => void rematch()} loading={busy === 'rematch'} disabled={(unmatched?.content.length ?? 0) === 0}>Rematch</Button>
          )}
        </Group>
        <DataTable
          bare
          rows={unmatched?.content ?? []}
          loading={unmatchedLoading}
          rowKey={(r) => r.id}
          minWidth={420}
          emptyTitle="All punches matched"
          emptyDescription="Every imported punch belongs to a known employee."
          columns={[
            { key: 'id', header: 'Identifier', render: (r) => <Code>{r.employeeIdentifier}</Code> },
            { key: 'time', header: 'Time (UTC)', render: (r) => <Text size="sm" className="tabular">{formatDate(r.eventTime)} {r.eventTime.slice(11, 16)}</Text> },
            { key: 'dir', header: 'Direction', render: (r) => <Badge variant="light" color={r.direction === 'IN' ? 'teal' : r.direction === 'OUT' ? 'brand' : 'gray'}>{humanize(r.direction)}</Badge> },
          ]}
        />
      </Paper>

      <Modal opened={newKey !== null} onClose={() => setNewKey(null)} title={<Group gap="xs"><IconKey size={18} /><Text fw={600}>New API key</Text></Group>} size="lg">
        <Stack>
          <Alert color="yellow" variant="light" icon={<IconAlertTriangle size={18} />} title="Shown only once">
            Copy this key now and store it securely — it will not be shown again.
          </Alert>
          <Group gap="xs" align="flex-end" wrap="nowrap">
            <PasswordInput label="API key" readOnly value={newKey ?? ''} style={{ flex: 1 }} styles={{ input: { fontFamily: 'var(--mantine-font-family-monospace)' } }} />
            {newKey && (
              <CopyButton value={newKey} timeout={2000}>
                {({ copied, copy }) => (
                  <Button variant={copied ? 'light' : 'filled'} color={copied ? 'teal' : undefined} leftSection={copied ? <IconCheck size={16} /> : <IconCopy size={16} />} onClick={copy}>
                    {copied ? 'Copied' : 'Copy'}
                  </Button>
                )}
              </CopyButton>
            )}
          </Group>
          <Paper withBorder p="sm">
            <Text size="sm" fw={500} mb={6}>How to use it</Text>
            <Stack gap={6}>
              <Group gap={6} wrap="wrap">
                <Text size="sm">Send punches to</Text>
                <Code>POST /api/v1/integrations/attendance/events</Code>
                <CopyIcon value="/api/v1/integrations/attendance/events" label="Copy endpoint" />
              </Group>
              <Text size="sm">with the header <Code>X-Api-Key</Code> set to this key.</Text>
            </Stack>
          </Paper>
          <Group justify="flex-end">
            <Button variant="default" onClick={() => setNewKey(null)}>I have saved the key</Button>
          </Group>
        </Stack>
      </Modal>
    </Stack>
  );
}
