import { Code, Group, Select, TextInput } from '@mantine/core';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { api, type Page } from '../../api/client';
import { DataTable } from '../../components/DataTable';

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

/** FR-008 audit search (export is added with the reports module). */
export function AuditLogPanel() {
  const [entityType, setEntityType] = useState<string | null>(null);
  const [action, setAction] = useState('');
  const [page, setPage] = useState(0);
  const { data, isLoading } = useQuery({
    queryKey: ['audit', entityType, action, page],
    queryFn: () => api<Page<AuditRow>>('/audit', { query: { entityType, action: action.toUpperCase(), page, size: 25 } }),
  });
  return (
    <>
      <Group mb="sm">
        <Select placeholder="Entity type" clearable data={ENTITY_TYPES} value={entityType} onChange={(v) => { setEntityType(v); setPage(0); }} />
        <TextInput placeholder="Action (e.g. UPDATE)" value={action} onChange={(e) => { setAction(e.currentTarget.value); setPage(0); }} />
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
