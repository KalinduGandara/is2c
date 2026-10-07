import { Badge, Button, Drawer, Group, Modal, NumberInput, Paper, Select, Stack, Table, Tabs, Text, TextInput } from '@mantine/core';
import { IconPlus } from '@tabler/icons-react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { api, download, type Page } from '../../api/client';
import { useCompany } from '../../app/CompanyContext';
import { useAuth } from '../../auth/AuthContext';
import { CrudPanel } from '../../components/CrudPanel';
import { DataTable } from '../../components/DataTable';
import { showError, showSuccess } from '../../components/notify';
import { PageHeader } from '../../components/PageHeader';
import { toOptions, useLookups } from '../org/useOrgData';
import { ApplyLeaveForm } from './ApplyLeaveForm';
import { LeaveCalendar } from './LeaveCalendar';
import { LEAVE_STATUS_COLOR, type Balance, type LeaveRequest, type LeaveType } from './types';

type EmployeeOption = { id: string; employeeNumber: string; firstName: string; lastName: string };

function useEmployeeOptions(companyId: string) {
  const { data } = useQuery({
    queryKey: ['employee-options', companyId],
    queryFn: () => api<Page<EmployeeOption>>('/employees', { query: { companyId, status: 'ACTIVE', size: 200, sort: 'employeeNumber,asc' } }),
  });
  return (data?.content ?? []).map((e) => ({ value: e.id, label: `${e.employeeNumber} — ${e.firstName} ${e.lastName}` }));
}

function RequestsPanel({ companyId }: { companyId: string }) {
  const { can } = useAuth();
  const client = useQueryClient();
  const [status, setStatus] = useState<string | null>('PENDING');
  const [open, setOpen] = useState<LeaveRequest | null>(null);
  const [applying, setApplying] = useState(false);
  const [onBehalf, setOnBehalf] = useState<string | null>(null);
  const [cancelReason, setCancelReason] = useState('');
  const employees = useEmployeeOptions(companyId);
  const { data = [], isLoading } = useQuery({
    queryKey: ['leave-requests', companyId, status],
    queryFn: () => api<LeaveRequest[]>('/leave/requests', { query: { companyId, status } }),
  });
  const refresh = () => client.invalidateQueries({ queryKey: ['leave-requests'] });
  const cancel = async () => {
    if (!open) return;
    try {
      await api(`/leave/requests/${open.id}/cancel`, { method: 'POST', body: { reason: cancelReason } });
      showSuccess('Leave cancelled');
      setOpen(null);
      await refresh();
    } catch (e) {
      showError(e);
    }
  };
  return (
    <>
      <Group justify="space-between" mb="sm">
        <Select placeholder="Status" clearable data={['PENDING', 'APPROVED', 'REJECTED', 'CANCELLED']} value={status} onChange={setStatus} w={180} />
        {can('LEAVE:ADD') && <Button leftSection={<IconPlus size={16} />} onClick={() => setApplying(true)}>Apply on behalf</Button>}
      </Group>
      <DataTable
        rows={data}
        loading={isLoading}
        rowKey={(r) => r.id}
        onRowClick={async (r) => setOpen(await api<LeaveRequest>(`/leave/requests/${r.id}`))}
        columns={[
          { key: 'emp', header: 'Employee', render: (r) => `${r.employeeNumber} ${r.employeeName}` },
          { key: 'type', header: 'Type', render: (r) => r.leaveType },
          { key: 'dates', header: 'Dates', render: (r) => (r.fromDate === r.toDate ? r.fromDate : `${r.fromDate} → ${r.toDate}`) + (r.halfDayPart ? ` (${r.halfDayPart.toLowerCase()})` : '') },
          { key: 'days', header: 'Days', render: (r) => r.days },
          { key: 'status', header: 'Status', render: (r) => <Badge color={LEAVE_STATUS_COLOR[r.status]}>{r.status}</Badge> },
        ]}
      />
      <Modal opened={applying} onClose={() => setApplying(false)} title="Apply leave on behalf of an employee" size="lg">
        <Stack>
          <Select label="Employee" searchable data={employees} value={onBehalf} onChange={setOnBehalf} />
          {onBehalf && <ApplyLeaveForm companyId={companyId} employeeId={onBehalf} onDone={() => { setApplying(false); void refresh(); }} />}
        </Stack>
      </Modal>
      <Drawer opened={open !== null} onClose={() => setOpen(null)} title="Leave request" position="right">
        {open && (
          <Stack>
            <Text fw={500}>{open.employeeNumber} {open.employeeName}</Text>
            <Text>{open.leaveType}: {open.fromDate} → {open.toDate} ({open.days} day(s))</Text>
            <Badge color={LEAVE_STATUS_COLOR[open.status]}>{open.status}</Badge>
            {open.reason && <Text size="sm">Reason: {open.reason}</Text>}
            {open.decisionNote && <Text size="sm">Note: {open.decisionNote}</Text>}
            {open.attachments.map((a) => (
              <Button key={a.id} variant="subtle" onClick={() => void download(`/leave/requests/${open.id}/attachments/${a.id}`).catch(showError)}>{a.fileName}</Button>
            ))}
            {can('LEAVE:EDIT') && (open.status === 'PENDING' || open.status === 'APPROVED') && (
              <Paper withBorder p="sm">
                <TextInput label="Cancellation reason" value={cancelReason} onChange={(e) => setCancelReason(e.currentTarget.value)} />
                <Group justify="flex-end" mt="sm"><Button color="red" variant="light" disabled={!cancelReason.trim()} onClick={() => void cancel()}>Cancel leave</Button></Group>
              </Paper>
            )}
          </Stack>
        )}
      </Drawer>
    </>
  );
}

function BalancesPanel({ companyId }: { companyId: string }) {
  const { can } = useAuth();
  const client = useQueryClient();
  const employees = useEmployeeOptions(companyId);
  const [employeeId, setEmployeeId] = useState<string | null>(null);
  const [year, setYear] = useState(new Date().getFullYear());
  const [posting, setPosting] = useState<{ kind: 'opening' | 'adjust'; balance: Balance } | null>(null);
  const [days, setDays] = useState<number | string>(0);
  const [reason, setReason] = useState('');
  const { data = [] } = useQuery({
    queryKey: ['leave-balances', employeeId, year],
    queryFn: () => api<Balance[]>('/leave/balances', { query: { employeeId, year } }),
    enabled: !!employeeId,
  });
  const post = async () => {
    if (!posting) return;
    try {
      await api(`/leave/balances/${posting.kind}`, { method: 'POST', body: { employeeId, leaveTypeId: posting.balance.leaveTypeId, year, days: Number(days), reason } });
      showSuccess();
      setPosting(null);
      setReason('');
      await client.invalidateQueries({ queryKey: ['leave-balances'] });
    } catch (e) {
      showError(e);
    }
  };
  const n = (v: number | null) => (v === null ? '' : v);
  return (
    <>
      <Group mb="sm">
        <Select label="Employee" searchable data={employees} value={employeeId} onChange={setEmployeeId} w={320} />
        <NumberInput label="Year" value={year} onChange={(v) => setYear(Number(v) || year)} w={100} />
      </Group>
      {employeeId && (
        <Table withTableBorder striped>
          <Table.Thead>
            <Table.Tr>{['Type', 'Opening', 'Entitled', 'Carried', 'Adjust', 'Taken', 'Pending', 'Expired', 'Available', ''].map((h) => <Table.Th key={h}>{h}</Table.Th>)}</Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {data.map((b) => (
              <Table.Tr key={b.leaveTypeId}>
                <Table.Td>{b.leaveTypeName}</Table.Td>
                {b.unlimited ? <Table.Td colSpan={8}>Unlimited</Table.Td> : (
                  <>
                    <Table.Td>{n(b.opening)}</Table.Td><Table.Td>{n(b.entitled)}</Table.Td><Table.Td>{n(b.carriedForward)}</Table.Td>
                    <Table.Td>{n(b.adjustment)}</Table.Td><Table.Td>{n(b.taken)}</Table.Td><Table.Td>{n(b.pending)}</Table.Td>
                    <Table.Td>{n(b.expired)}</Table.Td><Table.Td fw={600}>{n(b.available)}</Table.Td>
                  </>
                )}
                <Table.Td>
                  {!b.unlimited && can('LEAVE:EDIT') && (
                    <Group gap={4} wrap="nowrap">
                      <Button size="xs" variant="subtle" onClick={() => { setPosting({ kind: 'opening', balance: b }); setDays(b.opening ?? 0); }}>Opening</Button>
                      <Button size="xs" variant="subtle" onClick={() => { setPosting({ kind: 'adjust', balance: b }); setDays(0); }}>Adjust</Button>
                    </Group>
                  )}
                </Table.Td>
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>
      )}
      <Modal opened={posting !== null} onClose={() => setPosting(null)} title={posting?.kind === 'opening' ? 'Set opening balance' : 'Adjust balance'}>
        <Stack>
          <NumberInput label={posting?.kind === 'opening' ? 'Opening days' : 'Days (+/-)'} step={0.5} decimalScale={1} value={days} onChange={setDays} />
          <TextInput label="Reason" withAsterisk value={reason} onChange={(e) => setReason(e.currentTarget.value)} />
          <Group justify="flex-end"><Button disabled={!reason.trim()} onClick={() => void post()}>Save</Button></Group>
        </Stack>
      </Modal>
    </>
  );
}

function LeaveTypesPanel({ companyId }: { companyId: string }) {
  const { can } = useAuth();
  const { data: categories } = useLookups(companyId, 'employment-categories');
  const editable = can('LEAVE:CONFIGURE');
  return (
    <CrudPanel<LeaveType>
      entityName="leave type"
      queryKey={['leave-types', companyId]}
      load={() => api<LeaveType[]>(`/companies/${companyId}/leave-types`)}
      rowKey={(r) => r.id}
      columns={[
        { key: 'code', header: 'Code', render: (r) => r.code },
        { key: 'name', header: 'Name', render: (r) => r.name },
        { key: 'days', header: 'Days/year', render: (r) => (r.unlimited ? 'Unlimited' : r.annualDays) },
        { key: 'paid', header: 'Paid', render: (r) => (r.paid ? 'Yes' : 'No') },
        { key: 'accrual', header: 'Accrual', render: (r) => r.accrualMethod },
        { key: 'cf', header: 'Carry forward', render: (r) => (r.cfMaxDays ? `${r.cfMaxDays} (exp. ${r.cfExpiryMonths || '—'} m)` : '—') },
        { key: 'active', header: 'Active', render: (r) => (r.active ? 'Yes' : 'No') },
      ]}
      fields={[
        { name: 'code', label: 'Code', required: true },
        { name: 'name', label: 'Name', required: true },
        { name: 'paid', label: 'Paid leave', type: 'checkbox' },
        { name: 'unlimited', label: 'Unlimited (no balance)', type: 'checkbox' },
        { name: 'annualDays', label: 'Days per year', type: 'number', min: 0, visible: (v) => !v.unlimited },
        { name: 'accrualMethod', label: 'Accrual', type: 'select', options: [{ value: 'ANNUAL_UPFRONT', label: 'Annual (upfront)' }, { value: 'MONTHLY', label: 'Monthly' }], visible: (v) => !v.unlimited },
        { name: 'proRata', label: 'Pro-rate for joiners', type: 'checkbox', visible: (v) => !v.unlimited },
        { name: 'cfMaxDays', label: 'Max carry-forward days', type: 'number', min: 0, visible: (v) => !v.unlimited },
        { name: 'cfExpiryMonths', label: 'Carry-forward expires after month (0 = never)', type: 'number', min: 0, max: 12, visible: (v) => !v.unlimited },
        { name: 'allowHalfDay', label: 'Allow half days', type: 'checkbox' },
        { name: 'allowNegative', label: 'Allow negative balance', type: 'checkbox' },
        { name: 'countWeeklyOff', label: 'Count weekly-off days', type: 'checkbox' },
        { name: 'countHoliday', label: 'Count holidays', type: 'checkbox' },
        { name: 'attachmentAboveDays', label: 'Require attachment above (days)', type: 'number', min: 0 },
        { name: 'eligibleCategoryIds', label: 'Eligible categories (empty = all)', type: 'multiselect', options: toOptions(categories) },
        { name: 'color', label: 'Colour (#RRGGBB)' },
        { name: 'active', label: 'Active', type: 'checkbox' },
      ]}
      emptyValues={{ code: '', name: '', paid: true, unlimited: false, annualDays: 0, accrualMethod: 'ANNUAL_UPFRONT', proRata: true, cfMaxDays: 0, cfExpiryMonths: 0, allowHalfDay: true, allowNegative: false, countWeeklyOff: false, countHoliday: false, attachmentAboveDays: null, eligibleCategoryIds: [], active: true }}
      toValues={(r) => ({ ...r })}
      create={editable ? (v) => api(`/companies/${companyId}/leave-types`, { method: 'POST', body: v }) : undefined}
      update={editable ? (r, v) => api(`/leave-types/${r.id}`, { method: 'PUT', body: { ...v, version: r.version } }) : undefined}
    />
  );
}

function JobsPanel({ companyId }: { companyId: string }) {
  const [asOf, setAsOf] = useState(new Date().toISOString().slice(0, 10));
  const run = async (path: string, query: Record<string, string | number>) => {
    try {
      const r = await api<{ affected: number }>(`/leave/jobs/${path}`, { method: 'POST', query: { companyId, ...query } });
      showSuccess(`Done — ${r.affected} balance(s) affected`);
    } catch (e) {
      showError(e);
    }
  };
  return (
    <Paper withBorder p="md" maw={520}>
      <Stack>
        <Text size="sm" c="dimmed">These jobs also run automatically every night. They are safe to repeat.</Text>
        <TextInput label="As of date" value={asOf} onChange={(e) => setAsOf(e.currentTarget.value)} />
        <Group>
          <Button variant="light" onClick={() => void run('accrual', { asOf })}>Run accrual</Button>
          <Button variant="light" onClick={() => void run('year-end', { year: Number(asOf.slice(0, 4)) - 1 })}>Carry forward last year</Button>
          <Button variant="light" onClick={() => void run('expiry', { asOf })}>Expire carried-forward</Button>
        </Group>
      </Stack>
    </Paper>
  );
}

export function LeavePage() {
  const { t } = useTranslation();
  const { companyId } = useCompany();
  const { can } = useAuth();
  if (!companyId) return <Text>Select a company first.</Text>;
  return (
    <>
      <PageHeader title={t('nav.leave')} />
      <Tabs defaultValue="requests" keepMounted={false}>
        <Tabs.List mb="md">
          <Tabs.Tab value="requests">Requests</Tabs.Tab>
          <Tabs.Tab value="calendar">Calendar</Tabs.Tab>
          <Tabs.Tab value="balances">Balances</Tabs.Tab>
          <Tabs.Tab value="types">Leave types</Tabs.Tab>
          {can('LEAVE:CONFIGURE') && <Tabs.Tab value="jobs">Jobs</Tabs.Tab>}
        </Tabs.List>
        <Tabs.Panel value="requests"><RequestsPanel companyId={companyId} /></Tabs.Panel>
        <Tabs.Panel value="calendar"><LeaveCalendar companyId={companyId} /></Tabs.Panel>
        <Tabs.Panel value="balances"><BalancesPanel companyId={companyId} /></Tabs.Panel>
        <Tabs.Panel value="types"><LeaveTypesPanel companyId={companyId} /></Tabs.Panel>
        <Tabs.Panel value="jobs"><JobsPanel companyId={companyId} /></Tabs.Panel>
      </Tabs>
    </>
  );
}
