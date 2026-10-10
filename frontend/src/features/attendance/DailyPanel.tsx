import { ActionIcon, Avatar, Badge, Button, Checkbox, Group, Paper, Select, SimpleGrid, Text, Tooltip } from '@mantine/core';
import { DateInput } from '@mantine/dates';
import {
  IconAlarm,
  IconBeach,
  IconCheck,
  IconChevronRight,
  IconClockPlus,
  IconRefresh,
  IconUserCheck,
  IconUserX,
  IconX,
} from '@tabler/icons-react';
import { keepPreviousData, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { api, type Page } from '../../api/client';
import { useAuth } from '../../auth/AuthContext';
import { DataTable } from '../../components/DataTable';
import { StatCard } from '../../components/StatCard';
import { StatusBadge } from '../../components/StatusBadge';
import { formatDate, humanize } from '../../components/format';
import { showError, showSuccess } from '../../components/notify';
import { toOptions, useDepartments } from '../org/useOrgData';
import { DayDetailDrawer } from './DayDetailDrawer';
import { EXCEPTION_COLOR, STATUS_COLOR, daysAgo, duration, time, today, totalOt, type Daily } from './types';

const STATUSES = ['PRESENT', 'ABSENT', 'HALF_DAY', 'ON_LEAVE', 'HALF_DAY_LEAVE', 'WEEKLY_OFF', 'HOLIDAY', 'INCOMPLETE', 'UNSCHEDULED'];
const EXCEPTIONS = ['INCOMPLETE', 'ABSENT', 'LATE', 'EARLY', 'UNSCHEDULED', 'OT_PENDING'];
const toSelect = (values: string[]) => values.map((v) => ({ value: v, label: humanize(v) }));

function otState(r: Daily) {
  if (!r.otRequiresApproval) return null;
  if (r.otApproved) return { label: 'Approved', color: 'teal' };
  if (r.otRejected) return { label: 'Rejected', color: 'red' };
  return { label: 'Pending', color: 'yellow' };
}

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
  const [busy, setBusy] = useState<'process' | 'approve' | 'reject' | null>(null);
  const [open, setOpen] = useState<{ employeeId: string; date: string } | null>(null);
  const { data: departments } = useDepartments(companyId);
  const { data, isLoading, isFetching } = useQuery({
    queryKey: ['attendance-daily', companyId, exceptionsOnly, from, to, departmentId, status, type, page],
    queryFn: () =>
      api<Page<Daily>>(exceptionsOnly ? '/attendance/exceptions' : '/attendance/daily', {
        query: { companyId, from, to, departmentId, status, type, page, size: 50 },
      }),
    placeholderData: keepPreviousData,
  });

  const process = async () => {
    setBusy('process');
    try {
      const r = await api<{ processed: number; skippedLocked: number }>('/attendance/process', { method: 'POST', body: { companyId, from, to } });
      showSuccess(`Processed ${r.processed} days${r.skippedLocked ? ` (${r.skippedLocked} locked)` : ''}`);
      await client.invalidateQueries({ queryKey: ['attendance-daily'] });
    } catch (e) {
      showError(e);
    } finally {
      setBusy(null);
    }
  };

  const decideOt = async (approve: boolean) => {
    setBusy(approve ? 'approve' : 'reject');
    try {
      await api(`/attendance/ot/${approve ? 'approve' : 'reject'}`, { method: 'POST', body: { dailyIds: selected } });
      showSuccess(approve ? 'Overtime approved' : 'Overtime rejected');
      setSelected([]);
      await client.invalidateQueries({ queryKey: ['attendance-daily'] });
    } catch (e) {
      showError(e);
    } finally {
      setBusy(null);
    }
  };

  const rows = data?.content ?? [];
  const count = (pred: (r: Daily) => boolean) => rows.filter(pred).length;
  const partial = (data?.totalPages ?? 0) > 1;
  const scope = partial ? 'On this page' : from === to ? formatDate(from) : `${formatDate(from)} – ${formatDate(to)}`;
  const otMinutes = rows.reduce((sum, r) => sum + totalOt(r), 0);

  return (
    <>
      {!exceptionsOnly && (
        <SimpleGrid cols={{ base: 2, md: 5 }} spacing="md" mb="lg">
          <StatCard label="Present" value={count((r) => r.status === 'PRESENT' || r.status === 'HALF_DAY')} icon={<IconUserCheck size={18} />} color="green" hint={scope} />
          <StatCard label="Absent" value={count((r) => r.status === 'ABSENT')} icon={<IconUserX size={18} />} color="red" hint={scope} />
          <StatCard label="Late arrivals" value={count((r) => r.lateMinutes > 0)} icon={<IconAlarm size={18} />} color="orange" hint={scope} />
          <StatCard label="On leave" value={count((r) => r.status === 'ON_LEAVE' || r.status === 'HALF_DAY_LEAVE')} icon={<IconBeach size={18} />} color="blue" hint={scope} />
          <StatCard label="Overtime" value={duration(otMinutes)} icon={<IconClockPlus size={18} />} color="teal" hint={scope} />
        </SimpleGrid>
      )}

      <Paper withBorder p="md" mb="md">
        <Group align="flex-end" wrap="wrap" gap="sm" justify="space-between">
          <Group align="flex-end" wrap="wrap" gap="sm" style={{ flex: 1, minWidth: 0 }}>
            <DateInput label="From" valueFormat="D MMM YYYY" maxDate={to} value={from} onChange={(v) => { if (v) { setFrom(String(v)); setPage(0); } }} w={{ base: '100%', xs: 150 }} />
            <DateInput label="To" valueFormat="D MMM YYYY" minDate={from} value={to} onChange={(v) => { if (v) { setTo(String(v)); setPage(0); } }} w={{ base: '100%', xs: 150 }} />
            <Select label="Department" placeholder="All departments" clearable searchable data={toOptions(departments)} value={departmentId} onChange={(v) => { setDepartmentId(v); setPage(0); }} w={{ base: '100%', sm: 220 }} />
            {exceptionsOnly ? (
              <Select label="Exception" placeholder="All exceptions" clearable data={toSelect(EXCEPTIONS)} value={type} onChange={(v) => { setType(v); setPage(0); }} w={{ base: '100%', sm: 180 }} />
            ) : (
              <Select label="Status" placeholder="All statuses" clearable data={toSelect(STATUSES)} value={status} onChange={(v) => { setStatus(v); setPage(0); }} w={{ base: '100%', sm: 180 }} />
            )}
          </Group>
          <Group gap="xs" wrap="wrap">
            {can('ATTENDANCE:EDIT') && !exceptionsOnly && (
              <Tooltip label="Recalculate attendance from punches for the selected dates">
                <Button variant="light" leftSection={<IconRefresh size={16} />} loading={busy === 'process'} onClick={() => void process()}>Process range</Button>
              </Tooltip>
            )}
            {exceptionsOnly && can('ATTENDANCE:APPROVE') && (
              <>
                <Button leftSection={<IconCheck size={16} />} disabled={selected.length === 0} loading={busy === 'approve'} onClick={() => void decideOt(true)}>
                  Approve OT ({selected.length})
                </Button>
                <Button color="red" variant="light" leftSection={<IconX size={16} />} disabled={selected.length === 0} loading={busy === 'reject'} onClick={() => void decideOt(false)}>
                  Reject OT
                </Button>
              </>
            )}
          </Group>
        </Group>
      </Paper>

      <DataTable
        rows={rows}
        loading={isLoading || (isFetching && rows.length === 0)}
        rowKey={(r) => r.id}
        page={page}
        totalPages={data?.totalPages}
        totalElements={data?.totalElements}
        onPageChange={setPage}
        minWidth={920}
        onRowClick={(r) => setOpen({ employeeId: r.employeeId, date: r.date })}
        rowLabel={(r) => `${r.employeeName ?? r.employeeNumber ?? ''} ${formatDate(r.date)}`}
        emptyTitle={exceptionsOnly ? 'No exceptions' : 'No attendance records'}
        emptyDescription={
          exceptionsOnly
            ? 'Nothing needs attention for the selected dates and filters.'
            : can('ATTENDANCE:EDIT')
              ? 'Try a different date range or filter, or use “Process range” to calculate attendance from punches.'
              : 'Try a different date range or filter.'
        }
        columns={[
          ...(exceptionsOnly
            ? [{
                key: 'sel', header: '', width: 36,
                render: (r: Daily) =>
                  r.otRequiresApproval && !r.otApproved && !r.otRejected ? (
                    <Checkbox aria-label={`Select ${r.employeeNumber} ${r.date}`} checked={selected.includes(r.id)}
                      onClick={(e) => e.stopPropagation()}
                      onChange={(e) => setSelected((s) => (e.currentTarget.checked ? [...s, r.id] : s.filter((x) => x !== r.id)))} />
                  ) : null,
              }]
            : []),
          {
            key: 'emp', header: 'Employee',
            render: (r) => (
              <Group gap="sm" wrap="nowrap">
                <Avatar name={r.employeeName ?? r.employeeNumber ?? '?'} color="initials" size="sm" radius="xl" />
                <div style={{ minWidth: 0 }}>
                  <Text size="sm" fw={500} truncate>{r.employeeName ?? '—'}</Text>
                  <Text size="xs" c="dimmed">{r.employeeNumber}</Text>
                </div>
              </Group>
            ),
          },
          { key: 'date', header: 'Date', render: (r) => <Text size="sm" style={{ whiteSpace: 'nowrap' }}>{formatDate(r.date)}</Text> },
          {
            key: 'status', header: 'Status',
            render: (r) => (
              <Group gap={4} wrap="nowrap">
                <StatusBadge status={r.status} colors={STATUS_COLOR} />
                {r.manual && <Tooltip label="Corrected manually"><Badge size="xs" variant="outline" color="gray">Manual</Badge></Tooltip>}
              </Group>
            ),
          },
          { key: 'inout', header: 'In – Out', render: (r) => (r.firstIn || r.lastOut ? <Text size="sm" className="tabular" style={{ whiteSpace: 'nowrap' }}>{time(r.firstIn)} – {time(r.lastOut)}</Text> : <Text size="sm" c="dimmed">—</Text>) },
          { key: 'worked', header: 'Worked', align: 'right', render: (r) => <Text size="sm" className="tabular">{duration(r.workedMinutes)}</Text> },
          {
            key: 'late', header: 'Late / Early', align: 'right',
            render: (r) =>
              r.lateMinutes || r.earlyMinutes ? (
                <Group gap={4} justify="flex-end" wrap="nowrap">
                  {r.lateMinutes > 0 && <Tooltip label="Late arrival"><Badge size="sm" color="orange" variant="light">{r.lateMinutes}m late</Badge></Tooltip>}
                  {r.earlyMinutes > 0 && <Tooltip label="Early departure"><Badge size="sm" color="yellow" variant="light">{r.earlyMinutes}m early</Badge></Tooltip>}
                </Group>
              ) : <Text size="sm" c="dimmed">—</Text>,
          },
          {
            key: 'ot', header: 'Overtime', align: 'right',
            render: (r) => {
              const ot = totalOt(r);
              if (!ot) return <Text size="sm" c="dimmed">—</Text>;
              const st = otState(r);
              return (
                <Group gap={4} justify="flex-end" wrap="nowrap">
                  <Text size="sm" fw={600} c="teal" className="tabular">{duration(ot)}</Text>
                  {st && <Badge size="xs" variant="light" color={st.color}>{st.label}</Badge>}
                </Group>
              );
            },
          },
          {
            key: 'exc', header: 'Exceptions',
            render: (r) =>
              r.exceptions.length ? (
                <Group gap={4}>
                  {r.exceptions.map((x) => <Badge key={x} size="sm" variant="dot" color={EXCEPTION_COLOR[x] ?? 'gray'}>{humanize(x)}</Badge>)}
                </Group>
              ) : <Text size="sm" c="dimmed">—</Text>,
          },
          {
            key: 'view', header: '', width: 44, align: 'right',
            render: (r) => (
              <Tooltip label="View day details">
                <ActionIcon variant="subtle" color="gray" aria-label={`View ${r.employeeNumber ?? ''} ${r.date}`}
                  onClick={(e) => { e.stopPropagation(); setOpen({ employeeId: r.employeeId, date: r.date }); }}>
                  <IconChevronRight size={16} />
                </ActionIcon>
              </Tooltip>
            ),
          },
        ]}
      />
      {open && <DayDetailDrawer employeeId={open.employeeId} date={open.date} onClose={() => setOpen(null)} />}
    </>
  );
}
