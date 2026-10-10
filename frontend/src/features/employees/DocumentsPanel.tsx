import { ActionIcon, Badge, Button, FileInput, Grid, Group, Paper, Select, Stack, Text, TextInput, ThemeIcon, Title, Tooltip } from '@mantine/core';
import { DateInput } from '@mantine/dates';
import { modals } from '@mantine/modals';
import { IconDownload, IconFile, IconFileText, IconFileTypePdf, IconFiles, IconPhoto, IconTrash, IconUpload } from '@tabler/icons-react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { api, download } from '../../api/client';
import { useAuth } from '../../auth/AuthContext';
import { DataTable } from '../../components/DataTable';
import { formatDate, formatDateTime, humanize } from '../../components/format';
import { showError, showSuccess } from '../../components/notify';
import type { DocumentInfo } from './types';

const EXPIRING_DAYS = 30;

function formatBytes(bytes: number | null | undefined): string {
  if (bytes === null || bytes === undefined) return '';
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function FileTypeIcon({ name, contentType }: { name: string; contentType?: string }) {
  const lower = name.toLowerCase();
  if (contentType === 'application/pdf' || lower.endsWith('.pdf')) return <ThemeIcon variant="light" color="red" size={32} radius="md"><IconFileTypePdf size={18} /></ThemeIcon>;
  if (contentType?.startsWith('image/') || /\.(png|jpe?g)$/.test(lower)) return <ThemeIcon variant="light" color="teal" size={32} radius="md"><IconPhoto size={18} /></ThemeIcon>;
  if (/\.docx?$/.test(lower)) return <ThemeIcon variant="light" color="blue" size={32} radius="md"><IconFileText size={18} /></ThemeIcon>;
  return <ThemeIcon variant="light" color="gray" size={32} radius="md"><IconFile size={18} /></ThemeIcon>;
}

/** Days from today (UTC calendar) until an ISO date; negative when past. */
function daysUntil(iso: string): number {
  const today = new Date();
  const start = Date.UTC(today.getFullYear(), today.getMonth(), today.getDate());
  return Math.round((new Date(`${iso.slice(0, 10)}T00:00:00Z`).getTime() - start) / 86_400_000);
}

/** Expiry date with an "Expired" / "Expires soon" badge. */
export function ExpiryCell({ date }: { date: string | null }) {
  if (!date) return <Text span c="dimmed" size="sm">No expiry</Text>;
  const days = daysUntil(date);
  return (
    <Group gap={6} wrap="nowrap">
      <Text size="sm" style={{ whiteSpace: 'nowrap' }}>{formatDate(date)}</Text>
      {days < 0 ? (
        <Badge color="red" size="sm">Expired</Badge>
      ) : days <= EXPIRING_DAYS ? (
        <Badge color="orange" size="sm">{days === 0 ? 'Expires today' : `Expires in ${days} d`}</Badge>
      ) : null}
    </Group>
  );
}

export function DocumentsPanel({ employeeId }: { employeeId: string }) {
  const { can } = useAuth();
  const client = useQueryClient();
  const key = ['employee-documents', employeeId];
  const { data = [], isLoading } = useQuery({ queryKey: key, queryFn: () => api<DocumentInfo[]>(`/employees/${employeeId}/documents`) });
  const { data: types = [] } = useQuery({ queryKey: ['document-types'], queryFn: () => api<string[]>('/document-types') });
  const [docType, setDocType] = useState<string | null>(null);
  const [title, setTitle] = useState('');
  const [expiry, setExpiry] = useState<string | null>(null);
  const [file, setFile] = useState<File | null>(null);
  const [busy, setBusy] = useState(false);
  const tooLarge = !!file && file.size > 10 * 1024 * 1024;

  const upload = async () => {
    if (!file || !docType || !title) return;
    const form = new FormData();
    form.append('file', file);
    form.append('docType', docType);
    form.append('title', title);
    if (expiry) form.append('expiryDate', expiry);
    setBusy(true);
    try {
      await api(`/employees/${employeeId}/documents`, { method: 'POST', body: form });
      showSuccess('Document uploaded');
      setFile(null);
      setTitle('');
      setExpiry(null);
      await client.invalidateQueries({ queryKey: key });
    } catch (e) {
      showError(e);
    } finally {
      setBusy(false);
    }
  };

  const remove = (doc: DocumentInfo) =>
    modals.openConfirmModal({
      title: 'Delete document?',
      children: <Text size="sm">“{doc.title}” ({doc.fileName}) will be permanently removed from this employee’s record.</Text>,
      labels: { confirm: 'Delete', cancel: 'Cancel' },
      confirmProps: { color: 'red' },
      onConfirm: async () => {
        try {
          await api(`/employees/${employeeId}/documents/${doc.id}`, { method: 'DELETE' });
          showSuccess('Document deleted');
          await client.invalidateQueries({ queryKey: key });
        } catch (e) {
          showError(e);
        }
      },
    });

  return (
    <Stack>
      {can('EMPLOYEE:EDIT') && (
        <Paper withBorder p="lg">
          <Group gap="sm" mb="md" wrap="nowrap" align="flex-start">
            <ThemeIcon variant="light" size={32} radius="md"><IconUpload size={18} /></ThemeIcon>
            <div>
              <Title order={4}>Upload a document</Title>
              <Text size="sm" c="dimmed">PDF, JPG, PNG or DOCX, up to 10 MB.</Text>
            </div>
          </Group>
          <Grid align="flex-start">
            <Grid.Col span={{ base: 12, sm: 6, md: 3 }}>
              <Select label="Type" withAsterisk placeholder="Select type" data={types.map((t) => ({ value: t, label: humanize(t) }))} value={docType} onChange={setDocType} />
            </Grid.Col>
            <Grid.Col span={{ base: 12, sm: 6, md: 3 }}>
              <TextInput label="Title" withAsterisk placeholder="e.g. Appointment letter" value={title} onChange={(e) => setTitle(e.currentTarget.value)} />
            </Grid.Col>
            <Grid.Col span={{ base: 12, sm: 6, md: 2 }}>
              <DateInput label="Expiry" placeholder="Optional" valueFormat="YYYY-MM-DD" clearable value={expiry} onChange={(v) => setExpiry(v ? String(v) : null)} />
            </Grid.Col>
            <Grid.Col span={{ base: 12, sm: 6, md: 4 }}>
              <FileInput label="File" withAsterisk placeholder="Choose a file" clearable leftSection={<IconFile size={16} />}
                value={file} onChange={setFile} accept=".pdf,.jpg,.jpeg,.png,.docx"
                description={file ? formatBytes(file.size) : undefined}
                error={tooLarge ? 'File is larger than 10 MB' : undefined} />
            </Grid.Col>
          </Grid>
          <Group justify="flex-end" mt="md">
            <Button leftSection={<IconUpload size={16} />} onClick={() => void upload()} loading={busy} disabled={!file || !docType || !title.trim() || tooLarge}>Upload</Button>
          </Group>
        </Paper>
      )}
      <DataTable
        rows={data}
        loading={isLoading}
        rowKey={(r) => r.id}
        emptyIcon={<IconFiles size={20} />}
        emptyTitle="No documents yet"
        emptyDescription={can('EMPLOYEE:EDIT') ? 'Upload contracts, IDs and certificates above.' : 'Documents added to this employee will appear here.'}
        minWidth={680}
        columns={[
          {
            key: 'title', header: 'Document',
            render: (r) => (
              <Group gap="sm" wrap="nowrap">
                <FileTypeIcon name={r.fileName} contentType={r.contentType} />
                <div style={{ minWidth: 0 }}>
                  <Text size="sm" fw={600} truncate>{r.title}</Text>
                  <Text size="xs" c="dimmed" truncate>{r.fileName}{r.sizeBytes ? ` · ${formatBytes(r.sizeBytes)}` : ''}</Text>
                </div>
              </Group>
            ),
          },
          { key: 'type', header: 'Type', render: (r) => <Badge variant="light" color="gray">{humanize(r.docType)}</Badge> },
          { key: 'expiry', header: 'Expiry', render: (r) => <ExpiryCell date={r.expiryDate} /> },
          { key: 'uploaded', header: 'Uploaded', render: (r) => <Text size="xs" c="dimmed" style={{ whiteSpace: 'nowrap' }}>{formatDateTime(r.uploadedAt)}</Text> },
          {
            key: 'actions', header: '', align: 'right',
            render: (r) => (
              <Group gap={4} wrap="nowrap" justify="flex-end">
                <Tooltip label="Download">
                  <ActionIcon variant="subtle" aria-label={`Download ${r.title}`} onClick={() => void download(`/employees/${employeeId}/documents/${r.id}/content`).catch(showError)}>
                    <IconDownload size={16} />
                  </ActionIcon>
                </Tooltip>
                {can('EMPLOYEE:DELETE') && (
                  <Tooltip label="Delete">
                    <ActionIcon variant="subtle" color="red" aria-label={`Delete ${r.title}`} onClick={() => remove(r)}>
                      <IconTrash size={16} />
                    </ActionIcon>
                  </Tooltip>
                )}
              </Group>
            ),
          },
        ]}
      />
    </Stack>
  );
}
