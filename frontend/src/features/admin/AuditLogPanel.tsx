import { Button, Code, Group, Select, TextInput } from '@mantine/core';
import { IconDownload } from '@tabler/icons-react';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { api, download, type Page } from '../../api/client';
import { useAuth } from '../../auth/AuthContext';
import { DataTable } from '../../components/DataTable';
import { showError } from '../../components/notify';

interface AuditRow {
  id: string;
  occurredAt: string;
  username: string | null;
  action: string;
  entityType: string;
  entityId: string | null;
  reason: string | null;
  before: string | null;
  after: string | null;
}

const ENTITY_TYPES = ['EMPLOYEE', 'USER', 'ROLE', 'COMPANY', 'COMPANY_SETTINGS', 'BRANCH', 'DEPARTMENT', 'SECURITY',
  'ATTENDANCE_ADJUSTMENT', 'LEAVE_REQUEST', 'PAYROLL_PERIOD', 'PAYROLL_COMPONENT', 'SALARY_STRUCTURE', 'ROSTER'];

/** FR-008 audit search and export. Dates are UTC days. */
export function AuditLogPanel() {
  const [entityType, setEntityType] = useState<string | null>(null);
  const [action, setAction] = useState('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [page, setPage] = useState(0);
  const { can } = useAuth();
  const day = (d: string, plus = 0) => {
    if (!/^\d{4}-\d{2}-\d{2}$/.test(d)) return null;
    const t = new Date(`${d}T00:00:00Z`);
    t.setUTCDate(t.getUTCDate() + plus);
    return t.toISOString();
  };
  const filters = { entityType, action: action.trim().toUpperCase(), from: day(from), to: day(to, 1) };
  const { data, isLoading } = useQuery({
    queryKey: ['audit', filters, page],
    queryFn: () => api<Page<AuditRow>>('/audit', { query: { ...filters, page, size: 25 } }),
  });
  return (
    <>
      <Group mb="sm">
        <Select placeholder="Entity type" clearable data={ENTITY_TYPES} value={entityType} onChange={(v) => { setEntityType(v); setPage(0); }} />
        <TextInput placeholder="Action (e.g. UPDATE)" value={action} onChange={(e) => { setAction(e.currentTarget.value); setPage(0); }} />
        <TextInput placeholder="From (YYYY-MM-DD)" value={from} onChange={(e) => { setFrom(e.currentTarget.value); setPage(0); }} w={170} />
        <TextInput placeholder="To (YYYY-MM-DD)" value={to} onChange={(e) => { setTo(e.currentTarget.value); setPage(0); }} w={170} />
        {can('AUDIT:EXPORT') && ['csv', 'xlsx', 'pdf'].map((f) => (
          <Button key={f} variant="light" size="xs" leftSection={<IconDownload size={14} />}
            onClick={() => void download('/audit/export', { ...filters, format: f }).catch(showError)}>
            Export {f.toUpperCase()}
          </Button>
        ))}
      </Group>
      <DataTable
        rows={data?.content ?? []}
        loading={isLoading}
        rowKey={(r) => r.id}
        page={page}
        totalPages={data?.totalPages}
        onPageChange={setPage}
        columns={[
          { key: 'at', header: 'When', render: (r) => new Date(r.occurredAt).toLocaleString() },
          { key: 'user', header: 'User', render: (r) => r.username },
          { key: 'action', header: 'Action', render: (r) => r.action },
          { key: 'entity', header: 'Entity', render: (r) => `${r.entityType} ${r.entityId ?? ''}` },
          { key: 'reason', header: 'Reason', render: (r) => r.reason },
          { key: 'change', header: 'After', render: (r) => r.after && <Code block style={{ maxWidth: 360, whiteSpace: 'pre-wrap' }}>{r.after}</Code> },
        ]}
      />
    </>
  );
}
