import { Badge, Button, Checkbox, Group, Select } from '@mantine/core';
import { DateInput } from '@mantine/dates';
import { keepPreviousData, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { api, type Page } from '../../api/client';
import { useAuth } from '../../auth/AuthContext';
import { DataTable } from '../../components/DataTable';
import { showError, showSuccess } from '../../components/notify';
import { toOptions, useDepartments } from '../org/useOrgData';
import { DayDetailDrawer } from './DayDetailDrawer';
import { STATUS_COLOR, daysAgo, hours, time, today, totalOt, type Daily } from './types';

const STATUSES = ['PRESENT', 'ABSENT', 'HALF_DAY', 'ON_LEAVE', 'HALF_DAY_LEAVE', 'WEEKLY_OFF', 'HOLIDAY', 'INCOMPLETE', 'UNSCHEDULED'];
const EXCEPTIONS = ['INCOMPLETE', 'ABSENT', 'LATE', 'EARLY', 'UNSCHEDULED', 'OT_PENDING'];

/** FR-054/055 daily attendance and exceptions with OT approval. */
export function DailyPanel({ companyId, exceptionsOnly }: { companyId: string; exceptionsOnly?: boolean }) {
  const { can } = useAuth();
  const client = useQueryClient();
  const [from, setFrom] = useState(daysAgo(7));
  const [to, setTo] = useState(today());
  const [departmentId, setDepartmentId] = useState<string | null>(null);
  const [status, setStatus] = useState<string | null>(null);
  const [type, setType] = useState<string | null>(null);
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState<string[]>([]);
  const [open, setOpen] = useState<{ employeeId: string; date: string } | null>(null);
  const { data: departments } = useDepartments(companyId);
  const { data, isLoading } = useQuery({
    queryKey: ['attendance-daily', companyId, exceptionsOnly, from, to, departmentId, status, type, page],
    queryFn: () =>
      api<Page<Daily>>(exceptionsOnly ? '/attendance/exceptions' : '/attendance/daily', {
        query: { companyId, from, to, departmentId, status, type, page, size: 50 },
      }),
    placeholderData: keepPreviousData,
  });

  const process = async () => {
    try {
      const r = await api<{ processed: number; skippedLocked: number }>('/attendance/process', { method: 'POST', body: { companyId, from, to } });
      showSuccess(`Processed ${r.processed} days${r.skippedLocked ? ` (${r.skippedLocked} locked)` : ''}`);
      await client.invalidateQueries({ queryKey: ['attendance-daily'] });
    } catch (e) {
      showError(e);
    }
  };

  const decideOt = async (approve: boolean) => {
    try {
      await api(`/attendance/ot/${approve ? 'approve' : 'reject'}`, { method: 'POST', body: { dailyIds: selected } });
      showSuccess(approve ? 'Overtime approved' : 'Overtime rejected');
      setSelected([]);
      await client.invalidateQueries({ queryKey: ['attendance-daily'] });
    } catch (e) {
      showError(e);
    }
  };

  const rows = data?.content ?? [];
  return (
    <>
      <Group mb="sm" align="flex-end" wrap="wrap">
        <DateInput label="From" valueFormat="YYYY-MM-DD" value={from} onChange={(v) => v && setFrom(String(v))} w={140} />
        <DateInput label="To" valueFormat="YYYY-MM-DD" value={to} onChange={(v) => v && setTo(String(v))} w={140} />
        <Select label="Department" clearable data={toOptions(departments)} value={departmentId} onChange={(v) => { setDepartmentId(v); setPage(0); }} w={200} />
        {exceptionsOnly ? (
          <Select label="Exception" clearable data={EXCEPTIONS} value={type} onChange={(v) => { setType(v); setPage(0); }} w={160} />
        ) : (
          <Select label="Status" clearable data={STATUSES} value={status} onChange={(v) => { setStatus(v); setPage(0); }} w={160} />
        )}
        {can('ATTENDANCE:EDIT') && !exceptionsOnly && <Button variant="light" onClick={() => void process()}>Process range</Button>}
        {exceptionsOnly && can('ATTENDANCE:APPROVE') && (
          <>
            <Button disabled={selected.length === 0} onClick={() => void decideOt(true)}>Approve OT ({selected.length})</Button>
            <Button color="red" variant="light" disabled={selected.length === 0} onClick={() => void decideOt(false)}>Reject OT</Button>
          </>
        )}
      </Group>
      <DataTable
        rows={rows}
        loading={isLoading}
        rowKey={(r) => r.id}
        page={page}
        totalPages={data?.totalPages}
        onPageChange={setPage}
        columns={[
          ...(exceptionsOnly
            ? [{
                key: 'sel', header: '', width: 36,
                render: (r: Daily) =>
                  r.otRequiresApproval && !r.otApproved && !r.otRejected ? (
                    <Checkbox aria-label={`Select ${r.employeeNumber} ${r.date}`} checked={selected.includes(r.id)}
                      onChange={(e) => setSelected((s) => (e.currentTarget.checked ? [...s, r.id] : s.filter((x) => x !== r.id)))} />
                  ) : null,
              }]
            : []),
          { key: 'date', header: 'Date', render: (r) => r.date },
          { key: 'emp', header: 'Employee', render: (r) => `${r.employeeNumber ?? ''} ${r.employeeName ?? ''}` },
          { key: 'status', header: 'Status', render: (r) => <Badge color={STATUS_COLOR[r.status]}>{r.status}</Badge> },
          { key: 'inout', header: 'In – Out', render: (r) => `${time(r.firstIn)} – ${time(r.lastOut)}` },
          { key: 'worked', header: 'Worked h', render: (r) => hours(r.workedMinutes) },
          { key: 'late', header: 'Late', render: (r) => r.lateMinutes || '' },
          { key: 'early', header: 'Early', render: (r) => r.earlyMinutes || '' },
          { key: 'ot', header: 'OT h', render: (r) => hours(totalOt(r)) },
          { key: 'exc', header: 'Exceptions', render: (r) => r.exceptions.join(', ') },
          { key: 'view', header: '', render: (r) => <Button size="xs" variant="subtle" onClick={() => setOpen({ employeeId: r.employeeId, date: r.date })}>Open</Button> },
        ]}
      />
      {open && <DayDetailDrawer employeeId={open.employeeId} date={open.date} onClose={() => setOpen(null)} />}
    </>
  );
}
