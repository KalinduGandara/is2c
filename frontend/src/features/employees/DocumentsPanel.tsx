import { ActionIcon, Button, FileInput, Group, Paper, Select, Stack, TextInput } from '@mantine/core';
import { DateInput } from '@mantine/dates';
import { IconDownload, IconTrash } from '@tabler/icons-react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { api, download } from '../../api/client';
import { useAuth } from '../../auth/AuthContext';
import { DataTable } from '../../components/DataTable';
import { showError, showSuccess } from '../../components/notify';
import type { DocumentInfo } from './types';

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
      await client.invalidateQueries({ queryKey: key });
    } catch (e) {
      showError(e);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Stack>
      {can('EMPLOYEE:EDIT') && (
        <Paper withBorder p="sm">
          <Group align="flex-end" wrap="wrap">
            <Select label="Type" data={types} value={docType} onChange={setDocType} w={180} />
            <TextInput label="Title" value={title} onChange={(e) => setTitle(e.currentTarget.value)} w={200} />
            <DateInput label="Expiry" valueFormat="YYYY-MM-DD" clearable value={expiry} onChange={(v) => setExpiry(v ? String(v) : null)} w={150} />
            <FileInput label="File (PDF, JPG, PNG, DOCX ≤ 10 MB)" value={file} onChange={setFile} accept=".pdf,.jpg,.jpeg,.png,.docx" w={260} />
            <Button onClick={() => void upload()} loading={busy} disabled={!file || !docType || !title}>Upload</Button>
          </Group>
        </Paper>
      )}
      <DataTable
        rows={data}
        loading={isLoading}
        rowKey={(r) => r.id}
        columns={[
          { key: 'type', header: 'Type', render: (r) => r.docType },
          { key: 'title', header: 'Title', render: (r) => r.title },
          { key: 'file', header: 'File', render: (r) => r.fileName },
          { key: 'expiry', header: 'Expiry', render: (r) => r.expiryDate },
          {
            key: 'actions', header: '',
            render: (r) => (
              <Group gap={4} wrap="nowrap">
                <ActionIcon variant="subtle" aria-label="Download" onClick={() => void download(`/employees/${employeeId}/documents/${r.id}/content`).catch(showError)}>
                  <IconDownload size={16} />
                </ActionIcon>
                {can('EMPLOYEE:DELETE') && (
                  <ActionIcon variant="subtle" color="red" aria-label="Delete document" onClick={async () => {
                    try {
                      await api(`/employees/${employeeId}/documents/${r.id}`, { method: 'DELETE' });
                      await client.invalidateQueries({ queryKey: key });
                    } catch (e) {
                      showError(e);
                    }
                  }}>
                    <IconTrash size={16} />
                  </ActionIcon>
                )}
              </Group>
            ),
          },
        ]}
      />
    </Stack>
  );
}
