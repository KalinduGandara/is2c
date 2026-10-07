import { Alert, Badge, Button, Code, FileInput, Group, Modal, Paper, Select, Stack, Table, Text, TextInput, Title } from '@mantine/core';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { api, type Page } from '../../api/client';
import { useAuth } from '../../auth/AuthContext';
import { DataTable } from '../../components/DataTable';
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

/** FR-045..047 CSV import, attendance sources (API keys) and unmatched punches. */
export function IntegrationPanel({ companyId }: { companyId: string }) {
  const { can } = useAuth();
  const client = useQueryClient();
  const [file, setFile] = useState<File | null>(null);
  const [result, setResult] = useState<IngestResult | null>(null);
  const [newKey, setNewKey] = useState<string | null>(null);
  const [name, setName] = useState('');
  const [adapter, setAdapter] = useState<string | null>('GENERIC_JSON');
  const { data: sources = [] } = useQuery({ queryKey: ['attendance-sources', companyId], queryFn: () => api<Source[]>(`/companies/${companyId}/attendance-sources`) });
  const { data: unmatched } = useQuery({
    queryKey: ['unmatched', companyId],
    queryFn: () => api<Page<{ id: string; employeeIdentifier: string; eventTime: string; direction: string }>>('/attendance/unmatched-events', { query: { companyId, size: 50 } }),
  });

  const upload = async () => {
    if (!file) return;
    const form = new FormData();
    form.append('file', file);
    try {
      setResult(await api<IngestResult>(`/attendance/import?companyId=${companyId}`, { method: 'POST', body: form }));
      await client.invalidateQueries({ queryKey: ['unmatched'] });
    } catch (e) {
      showError(e);
    }
  };

  const createSource = async () => {
    try {
      const s = await api<Source>(`/companies/${companyId}/attendance-sources`, { method: 'POST', body: { name, adapterType: adapter } });
      setNewKey(s.apiKey);
      setName('');
      await client.invalidateQueries({ queryKey: ['attendance-sources'] });
    } catch (e) {
      showError(e);
    }
  };

  const sourceAction = async (s: Source, action: 'regenerate-key' | 'deactivate' | 'activate') => {
    try {
      const r = await api<Source>(`/attendance-sources/${s.id}/${action}`, { method: 'POST' });
      if (r.apiKey) setNewKey(r.apiKey);
      await client.invalidateQueries({ queryKey: ['attendance-sources'] });
    } catch (e) {
      showError(e);
    }
  };

  const rematch = async () => {
    try {
      const r = await api<{ matched: number }>(`/attendance/rematch?companyId=${companyId}`, { method: 'POST' });
      showSuccess(`${r.matched} punches matched`);
      await client.invalidateQueries({ queryKey: ['unmatched'] });
    } catch (e) {
      showError(e);
    }
  };

  return (
    <Stack>
      {can('ATTENDANCE:ADD') && (
        <Paper withBorder p="md">
          <Title order={5} mb="xs">Import punches from CSV</Title>
          <Text size="sm" c="dimmed" mb="sm">Columns: employee badge or number, timestamp (2026-03-16T08:01:00), direction (IN/OUT, optional), device (optional).</Text>
          <Group align="flex-end">
            <FileInput label="CSV file" accept=".csv,text/csv" value={file} onChange={setFile} w={280} />
            <Button onClick={() => void upload()} disabled={!file}>Import</Button>
          </Group>
          {result && (
            <Alert mt="sm" color={result.rejected ? 'yellow' : 'green'}>
              Accepted {result.accepted}, duplicates {result.duplicates}, unmatched {result.unmatched}, rejected {result.rejected}
              {result.errors.slice(0, 10).map((e) => <Text key={e.row} size="xs">Row {e.row}: {e.reason}</Text>)}
            </Alert>
          )}
        </Paper>
      )}
      <Paper withBorder p="md">
        <Group justify="space-between" mb="xs">
          <Title order={5}>Attendance sources</Title>
          {can('ATTENDANCE:CONFIGURE') && (
            <Group align="flex-end">
              <TextInput placeholder="Source name" value={name} onChange={(e) => setName(e.currentTarget.value)} />
              <Select data={['GENERIC_JSON', 'CSV']} value={adapter} onChange={setAdapter} w={150} />
              <Button onClick={() => void createSource()} disabled={!name.trim()}>Add source</Button>
            </Group>
          )}
        </Group>
        <Table withTableBorder>
          <Table.Tbody>
            {sources.map((s) => (
              <Table.Tr key={s.id}>
                <Table.Td>{s.name}</Table.Td>
                <Table.Td>{s.adapterType}</Table.Td>
                <Table.Td>{s.keyPrefix ? <Code>hrm_{s.keyPrefix}_…</Code> : '—'}</Table.Td>
                <Table.Td><Badge color={s.active ? 'green' : 'gray'}>{s.active ? 'Active' : 'Inactive'}</Badge></Table.Td>
                <Table.Td>
                  {can('ATTENDANCE:CONFIGURE') && !s.systemSource && (
                    <Group gap={4}>
                      <Button size="xs" variant="subtle" onClick={() => void sourceAction(s, 'regenerate-key')}>New key</Button>
                      <Button size="xs" variant="subtle" onClick={() => void sourceAction(s, s.active ? 'deactivate' : 'activate')}>{s.active ? 'Deactivate' : 'Activate'}</Button>
                    </Group>
                  )}
                </Table.Td>
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>
      </Paper>
      <Paper withBorder p="md">
        <Group justify="space-between" mb="xs">
          <Title order={5}>Unmatched punches</Title>
          {can('ATTENDANCE:EDIT') && <Button variant="light" onClick={() => void rematch()}>Rematch</Button>}
        </Group>
        <DataTable
          rows={unmatched?.content ?? []}
          rowKey={(r) => r.id}
          columns={[
            { key: 'id', header: 'Identifier', render: (r) => r.employeeIdentifier },
            { key: 'time', header: 'Time (UTC)', render: (r) => r.eventTime.replace('T', ' ').slice(0, 16) },
            { key: 'dir', header: 'Direction', render: (r) => r.direction },
          ]}
        />
      </Paper>
      <Modal opened={newKey !== null} onClose={() => setNewKey(null)} title="API key">
        <Stack>
          <Alert color="yellow">Copy this key now — it will not be shown again.</Alert>
          <Code block>{newKey}</Code>
          <Text size="sm">Send punches to <Code>POST /api/v1/integrations/attendance/events</Code> with header <Code>X-Api-Key</Code>.</Text>
        </Stack>
      </Modal>
    </Stack>
  );
}
