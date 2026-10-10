import {
  Avatar,
  Badge,
  Button,
  ColorSwatch,
  Divider,
  Drawer,
  Group,
  Modal,
  NumberInput,
  Paper,
  Select,
  SimpleGrid,
  Stack,
  Tabs,
  Text,
  Textarea,
  TextInput,
  ThemeIcon,
  Title,
} from '@mantine/core';
import { DateInput } from '@mantine/dates';
import { modals } from '@mantine/modals';
import {
  IconAdjustmentsHorizontal,
  IconBeach,
  IconBuildingSkyscraper,
  IconCalendarEvent,
  IconCalendarRepeat,
  IconCategory,
  IconClockPause,
  IconHourglassLow,
  IconListDetails,
  IconPaperclip,
  IconPlayerPlay,
  IconPlus,
  IconScale,
  IconSearch,
  IconUser,
  IconX,
} from '@tabler/icons-react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { api, download, type Page } from '../../api/client';
import { useCompany } from '../../app/CompanyContext';
import { useAuth } from '../../auth/AuthContext';
import { CrudPanel } from '../../components/CrudPanel';
import { DataTable } from '../../components/DataTable';
import { EmptyState } from '../../components/EmptyState';
import { formatDate, formatDateTime, humanize } from '../../components/format';
import { showError, showSuccess } from '../../components/notify';
import { PageHeader } from '../../components/PageHeader';
import { StatusBadge } from '../../components/StatusBadge';
import { useUrlTab } from '../../components/useUrlTab';
import { toOptions, useLookups } from '../org/useOrgData';
import { ApplyLeaveForm } from './ApplyLeaveForm';
import { LeaveCalendar } from './LeaveCalendar';
import { formatDateRange, formatDays, LEAVE_STATUS_COLOR, type Balance, type LeaveRequest, type LeaveType } from './types';

type EmployeeOption = { id: string; employeeNumber: string; firstName: string; lastName: string };

const STATUSES: LeaveRequest['status'][] = ['PENDING', 'APPROVED', 'REJECTED', 'CANCELLED'];

function useEmployeeOptions(companyId: string) {
  const { data } = useQuery({
    queryKey: ['employee-options', companyId],
    queryFn: () => api<Page<EmployeeOption>>('/employees', { query: { companyId, status: 'ACTIVE', size: 200, sort: 'employeeNumber,asc' } }),
  });
  return (data?.content ?? []).map((e) => ({ value: e.id, label: `${e.employeeNumber} — ${e.firstName} ${e.lastName}` }));
}

function useLeaveTypes(companyId: string) {
  return useQuery({ queryKey: ['leave-types', companyId], queryFn: () => api<LeaveType[]>(`/companies/${companyId}/leave-types`) });
}

function Person({ name, sub }: { name: string | null; sub?: string | null }) {
  return (
    <Group gap="sm" wrap="nowrap">
      <Avatar name={name ?? undefined} color="initials" size="sm" radius="xl" />
      <div style={{ minWidth: 0 }}>
        <Text size="sm" fw={500} truncate>{name ?? '—'}</Text>
        {sub && <Text size="xs" c="dimmed">{sub}</Text>}
      </div>
    </Group>
  );
}

function DetailRow({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div>
      <Text size="xs" c="dimmed" fw={500}>{label}</Text>
      <Text size="sm" component="div">{children}</Text>
    </div>
  );
}

function RequestsPanel({ companyId }: { companyId: string }) {
  const { can } = useAuth();
  const client = useQueryClient();
  const [status, setStatus] = useState<string | null>('PENDING');
  const [typeFilter, setTypeFilter] = useState<string | null>(null);
  const [search, setSearch] = useState('');
  const [open, setOpen] = useState<LeaveRequest | null>(null);
  const [applying, setApplying] = useState(false);
  const [onBehalf, setOnBehalf] = useState<string | null>(null);
  const [cancelReason, setCancelReason] = useState('');
  const [cancelling, setCancelling] = useState(false);
  const employees = useEmployeeOptions(companyId);
  const { data: types = [] } = useLeaveTypes(companyId);
  const { data = [], isLoading } = useQuery({
    queryKey: ['leave-requests', companyId, status],
    queryFn: () => api<LeaveRequest[]>('/leave/requests', { query: { companyId, status } }),
  });
  const colorOf = (name: string | null) => types.find((t) => t.name === name)?.color ?? undefined;
  const q = search.trim().toLowerCase();
  const rows = data.filter(
    (r) =>
      (!typeFilter || r.leaveType === typeFilter) &&
      (!q || `${r.employeeNumber ?? ''} ${r.employeeName ?? ''}`.toLowerCase().includes(q)),
  );
  const refresh = () =>
    Promise.all([client.invalidateQueries({ queryKey: ['leave-requests'] }), client.invalidateQueries({ queryKey: ['leave-balances'] })]);

  const cancel = async () => {
    if (!open) return;
    setCancelling(true);
    try {
      await api(`/leave/requests/${open.id}/cancel`, { method: 'POST', body: { reason: cancelReason } });
      showSuccess('Leave cancelled');
      setOpen(null);
      setCancelReason('');
      await refresh();
    } catch (e) {
      showError(e);
    } finally {
      setCancelling(false);
    }
  };
  const confirmCancel = () =>
    modals.openConfirmModal({
      title: 'Cancel this leave?',
      children: (
        <Text size="sm">
          {open?.employeeName}’s {open?.leaveType ?? 'leave'} for {open ? formatDateRange(open.fromDate, open.toDate) : ''} will be cancelled and the days returned to the balance.
        </Text>
      ),
      labels: { confirm: 'Cancel leave', cancel: 'Keep leave' },
      confirmProps: { color: 'red' },
      onConfirm: () => void cancel(),
    });

  const openRequest = async (r: LeaveRequest) => {
    setCancelReason('');
    setOpen(r);
    try {
      setOpen(await api<LeaveRequest>(`/leave/requests/${r.id}`));
    } catch (e) {
      showError(e);
    }
  };

  const filtered = !!typeFilter || !!q;

  return (
    <Stack gap="md">
      <Group justify="space-between" wrap="wrap" gap="sm">
        <Group gap="sm" wrap="wrap" style={{ flex: '1 1 320px' }}>
          <TextInput
            aria-label="Search employee"
            placeholder="Search employee"
            leftSection={<IconSearch size={16} />}
            value={search}
            onChange={(e) => setSearch(e.currentTarget.value)}
            w={{ base: '100%', sm: 220 }}
          />
          <Select
            aria-label="Status"
            placeholder="All statuses"
            clearable
            data={STATUSES.map((s) => ({ value: s, label: humanize(s) }))}
            value={status}
            onChange={setStatus}
            w={{ base: '100%', sm: 180 }}
          />
          <Select
            aria-label="Leave type filter"
            placeholder="All leave types"
            clearable
            data={types.map((t) => t.name)}
            value={typeFilter}
            onChange={setTypeFilter}
            w={{ base: '100%', sm: 200 }}
          />
        </Group>
        {can('LEAVE:ADD') && (
          <Button leftSection={<IconPlus size={16} />} onClick={() => setApplying(true)}>
            Apply on behalf
          </Button>
        )}
      </Group>
      <DataTable
        rows={rows}
        loading={isLoading}
        rowKey={(r) => r.id}
        onRowClick={(r) => void openRequest(r)}
        rowLabel={(r) => `${r.employeeName ?? ''} ${r.leaveType ?? ''} ${formatDateRange(r.fromDate, r.toDate)}`}
        emptyTitle={filtered ? 'No matching requests' : status ? `No ${humanize(status).toLowerCase()} leave requests` : 'No leave requests yet'}
        emptyDescription={filtered ? 'Try a different search or filter.' : 'Leave requests from employees will appear here.'}
        emptyIcon={<IconBeach size={20} stroke={1.5} />}
        minWidth={720}
        columns={[
          { key: 'emp', header: 'Employee', render: (r) => <Person name={r.employeeName} sub={r.employeeNumber} /> },
          {
            key: 'type',
            header: 'Type',
            render: (r) => (
              <Badge variant="light" color={colorOf(r.leaveType) ?? 'indigo'}>
                {r.leaveType ?? '—'}
              </Badge>
            ),
          },
          {
            key: 'dates',
            header: 'Dates',
            render: (r) => (
              <div>
                <Text size="sm" style={{ whiteSpace: 'nowrap' }}>{formatDateRange(r.fromDate, r.toDate)}</Text>
                {r.halfDayPart && <Text size="xs" c="dimmed">{humanize(r.halfDayPart)} half day</Text>}
              </div>
            ),
          },
          { key: 'days', header: 'Days', align: 'right', render: (r) => <Text size="sm" fw={600} className="tabular">{formatDays(r.days)}</Text> },
          { key: 'applied', header: 'Applied', render: (r) => <Text size="sm" c="dimmed">{formatDate(r.appliedAt)}</Text> },
          { key: 'status', header: 'Status', render: (r) => <StatusBadge status={r.status} colors={LEAVE_STATUS_COLOR} /> },
        ]}
      />

      <Modal opened={applying} onClose={() => setApplying(false)} title="Apply leave on behalf of an employee" size="lg">
        <Stack>
          <Select
            label="Employee"
            placeholder="Search by name or number"
            searchable
            leftSection={<IconUser size={16} />}
            data={employees}
            value={onBehalf}
            onChange={setOnBehalf}
            nothingFoundMessage="No matching employees"
          />
          {onBehalf ? (
            <>
              <Divider />
              <ApplyLeaveForm companyId={companyId} employeeId={onBehalf} onDone={() => { setApplying(false); void refresh(); }} />
            </>
          ) : (
            <Text size="sm" c="dimmed">Choose the employee to continue.</Text>
          )}
        </Stack>
      </Modal>

      <Drawer opened={open !== null} onClose={() => setOpen(null)} title="Leave request" position="right" size="md">
        {open && (
          <Stack gap="lg">
            <Paper withBorder p="md">
              <Group justify="space-between" align="flex-start" wrap="nowrap">
                <Person name={open.employeeName} sub={open.employeeNumber} />
                <StatusBadge status={open.status} colors={LEAVE_STATUS_COLOR} />
              </Group>
              <Divider my="sm" />
              <SimpleGrid cols={2} spacing="sm">
                <DetailRow label="Leave type">
                  <Badge variant="light" color={colorOf(open.leaveType) ?? 'indigo'}>{open.leaveType ?? '—'}</Badge>
                </DetailRow>
                <DetailRow label="Days">
                  <Text fw={700} className="tabular">{formatDays(open.days)}</Text>
                </DetailRow>
                <DetailRow label="Dates">
                  {formatDateRange(open.fromDate, open.toDate)}
                  {open.halfDayPart && <Text size="xs" c="dimmed">{humanize(open.halfDayPart)} half day</Text>}
                </DetailRow>
                <DetailRow label="Applied">{formatDateTime(open.appliedAt)}</DetailRow>
              </SimpleGrid>
            </Paper>

            {(open.reason || open.decisionNote) && (
              <Stack gap="sm">
                {open.reason && (
                  <DetailRow label="Reason">
                    <Text size="sm" style={{ whiteSpace: 'pre-wrap' }}>{open.reason}</Text>
                  </DetailRow>
                )}
                {open.decisionNote && (
                  <DetailRow label="Decision note">
                    <Text size="sm" style={{ whiteSpace: 'pre-wrap' }}>{open.decisionNote}</Text>
                  </DetailRow>
                )}
              </Stack>
            )}

            {open.attachments?.length ? (
              <Stack gap={4}>
                <Text size="xs" c="dimmed" fw={500}>Attachments</Text>
                {open.attachments.map((a) => (
                  <Button
                    key={a.id}
                    variant="default"
                    justify="flex-start"
                    leftSection={<IconPaperclip size={16} />}
                    onClick={() => void download(`/leave/requests/${open.id}/attachments/${a.id}`).catch(showError)}
                  >
                    {a.fileName}
                  </Button>
                ))}
              </Stack>
            ) : null}

            {can('LEAVE:EDIT') && (open.status === 'PENDING' || open.status === 'APPROVED') && (
              <Paper withBorder p="md">
                <Title order={5} mb={4}>Cancel this leave</Title>
                <Text size="xs" c="dimmed" mb="sm">The employee is notified and any deducted days are restored.</Text>
                <Textarea
                  label="Cancellation reason"
                  withAsterisk
                  autosize
                  minRows={2}
                  value={cancelReason}
                  onChange={(e) => setCancelReason(e.currentTarget.value)}
                />
                <Group justify="flex-end" mt="sm">
                  <Button color="red" variant="light" leftSection={<IconX size={16} />} loading={cancelling} disabled={!cancelReason.trim()} onClick={confirmCancel}>
                    Cancel leave
                  </Button>
                </Group>
              </Paper>
            )}
          </Stack>
        )}
      </Drawer>
    </Stack>
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
  const [saving, setSaving] = useState(false);
  const { data = [], isLoading } = useQuery({
    queryKey: ['leave-balances', employeeId, year],
    queryFn: () => api<Balance[]>('/leave/balances', { query: { employeeId, year } }),
    enabled: !!employeeId,
  });
  const post = async () => {
    if (!posting) return;
    setSaving(true);
    try {
      await api(`/leave/balances/${posting.kind}`, { method: 'POST', body: { employeeId, leaveTypeId: posting.balance.leaveTypeId, year, days: Number(days), reason } });
      showSuccess();
      setPosting(null);
      setReason('');
      await client.invalidateQueries({ queryKey: ['leave-balances'] });
    } catch (e) {
      showError(e);
    } finally {
      setSaving(false);
    }
  };
  const num = (v: number | null, opts?: { strong?: boolean; color?: string }) => (
    <Text size="sm" className="tabular" fw={opts?.strong ? 700 : undefined} c={opts?.color ?? (v ? undefined : 'dimmed')}>
      {formatDays(v)}
    </Text>
  );
  const numCol = (key: keyof Balance & string, header: string) => ({
    key,
    header,
    align: 'right' as const,
    render: (b: Balance) => (b.unlimited ? <Text size="sm" c="dimmed">—</Text> : num(b[key] as number | null)),
  });
  return (
    <Stack gap="md">
      <Paper withBorder p="md">
        <Group gap="sm" wrap="wrap" align="flex-end">
          <Select
            label="Employee"
            placeholder="Search by name or number"
            searchable
            leftSection={<IconUser size={16} />}
            data={employees}
            value={employeeId}
            onChange={setEmployeeId}
            w={{ base: '100%', sm: 340 }}
            nothingFoundMessage="No matching employees"
          />
          <NumberInput label="Year" value={year} onChange={(v) => setYear(Number(v) || year)} w={{ base: '100%', sm: 110 }} min={2000} max={2100} />
        </Group>
      </Paper>
      {!employeeId ? (
        <Paper withBorder>
          <EmptyState icon={<IconScale size={28} stroke={1.5} />} title="Select an employee" description="Choose an employee to see their leave balances for the year." />
        </Paper>
      ) : (
        <DataTable
          rows={data}
          loading={isLoading}
          rowKey={(b) => b.leaveTypeId}
          minWidth={860}
          emptyTitle="No balances"
          emptyDescription="This employee has no eligible leave types for the selected year."
          columns={[
            { key: 'type', header: 'Leave type', render: (b) => <Text size="sm" fw={500}>{b.leaveTypeName}</Text> },
            numCol('opening', 'Opening'),
            numCol('entitled', 'Entitled'),
            numCol('carriedForward', 'Carried'),
            numCol('adjustment', 'Adjust'),
            numCol('taken', 'Taken'),
            numCol('pending', 'Pending'),
            numCol('expired', 'Expired'),
            {
              key: 'available',
              header: 'Available',
              align: 'right',
              render: (b) =>
                b.unlimited ? (
                  <Badge variant="light" color="gray">Unlimited</Badge>
                ) : (
                  num(b.available, { strong: true, color: (b.available ?? 0) < 0 ? 'red' : (b.available ?? 0) > 0 ? 'teal' : 'dimmed' })
                ),
            },
            {
              key: 'actions',
              header: '',
              align: 'right',
              render: (b) =>
                !b.unlimited &&
                can('LEAVE:EDIT') && (
                  <Group gap={4} wrap="nowrap" justify="flex-end">
                    <Button size="compact-xs" variant="subtle" onClick={() => { setPosting({ kind: 'opening', balance: b }); setDays(b.opening ?? 0); setReason(''); }}>
                      Opening
                    </Button>
                    <Button size="compact-xs" variant="subtle" onClick={() => { setPosting({ kind: 'adjust', balance: b }); setDays(0); setReason(''); }}>
                      Adjust
                    </Button>
                  </Group>
                ),
            },
          ]}
        />
      )}
      <Modal
        opened={posting !== null}
        onClose={() => setPosting(null)}
        title={posting?.kind === 'opening' ? `Set opening balance — ${posting.balance.leaveTypeName}` : `Adjust balance — ${posting?.balance.leaveTypeName ?? ''}`}
      >
        <Stack>
          <NumberInput
            label={posting?.kind === 'opening' ? 'Opening days' : 'Days (+/-)'}
            description={posting?.kind === 'opening' ? `Opening balance for ${year}.` : 'Use a negative number to deduct days.'}
            step={0.5}
            decimalScale={1}
            value={days}
            onChange={setDays}
          />
          <TextInput label="Reason" withAsterisk placeholder="Recorded in the balance ledger" value={reason} onChange={(e) => setReason(e.currentTarget.value)} />
          <Group justify="flex-end">
            <Button variant="default" onClick={() => setPosting(null)}>Cancel</Button>
            <Button loading={saving} disabled={!reason.trim()} onClick={() => void post()}>Save</Button>
          </Group>
        </Stack>
      </Modal>
    </Stack>
  );
}

function LeaveTypesPanel({ companyId }: { companyId: string }) {
  const { can } = useAuth();
  const { data: categories } = useLookups(companyId, 'employment-categories');
  const editable = can('LEAVE:CONFIGURE');
  return (
    <CrudPanel<LeaveType>
      entityName="leave type"
      description="Leave policies: entitlement, accrual, carry-forward and rules for each type."
      queryKey={['leave-types', companyId]}
      load={() => api<LeaveType[]>(`/companies/${companyId}/leave-types`)}
      rowKey={(r) => r.id}
      columns={[
        { key: 'code', header: 'Code', render: (r) => <Text size="sm" ff="monospace">{r.code}</Text> },
        {
          key: 'name',
          header: 'Name',
          render: (r) => (
            <Group gap="xs" wrap="nowrap">
              <ColorSwatch color={r.color ?? 'var(--mantine-color-indigo-6)'} size={12} withShadow={false} />
              <Text size="sm" fw={500}>{r.name}</Text>
            </Group>
          ),
        },
        { key: 'days', header: 'Days/year', align: 'right', render: (r) => (r.unlimited ? <Badge variant="light" color="gray">Unlimited</Badge> : <Text size="sm" className="tabular">{formatDays(r.annualDays)}</Text>) },
        { key: 'paid', header: 'Paid', render: (r) => <Badge variant="light" color={r.paid ? 'teal' : 'gray'}>{r.paid ? 'Paid' : 'Unpaid'}</Badge> },
        { key: 'accrual', header: 'Accrual', render: (r) => (r.unlimited ? '—' : r.accrualMethod === 'ANNUAL_UPFRONT' ? 'Annual (upfront)' : humanize(r.accrualMethod)) },
        { key: 'cf', header: 'Carry forward', render: (r) => (r.cfMaxDays ? `${r.cfMaxDays} days${r.cfExpiryMonths ? ` (expires month ${r.cfExpiryMonths})` : ''}` : '—') },
        { key: 'active', header: 'Status', render: (r) => <StatusBadge status={r.active ? 'ACTIVE' : 'INACTIVE'} /> },
      ]}
      fields={[
        { name: 'code', label: 'Code', required: true, placeholder: 'e.g. ANNUAL' },
        { name: 'name', label: 'Name', required: true, placeholder: 'e.g. Annual leave' },
        { name: 'paid', label: 'Paid leave', type: 'checkbox' },
        { name: 'unlimited', label: 'Unlimited (no balance)', type: 'checkbox' },
        { name: 'annualDays', label: 'Days per year', type: 'number', min: 0, visible: (v) => !v.unlimited },
        { name: 'accrualMethod', label: 'Accrual', type: 'select', options: [{ value: 'ANNUAL_UPFRONT', label: 'Annual (upfront)' }, { value: 'MONTHLY', label: 'Monthly' }], visible: (v) => !v.unlimited },
        { name: 'proRata', label: 'Pro-rate for joiners', type: 'checkbox', visible: (v) => !v.unlimited },
        { name: 'cfMaxDays', label: 'Max carry-forward days', type: 'number', min: 0, visible: (v) => !v.unlimited },
        { name: 'cfExpiryMonths', label: 'Carry-forward expires after month (0 = never)', type: 'number', min: 0, max: 12, visible: (v) => !v.unlimited, description: 'Month of the new year after which unused carried-forward days lapse.' },
        { name: 'allowHalfDay', label: 'Allow half days', type: 'checkbox' },
        { name: 'allowNegative', label: 'Allow negative balance', type: 'checkbox' },
        { name: 'countWeeklyOff', label: 'Count weekly-off days', type: 'checkbox' },
        { name: 'countHoliday', label: 'Count holidays', type: 'checkbox' },
        { name: 'attachmentAboveDays', label: 'Require attachment above (days)', type: 'number', min: 0, description: 'Leave empty if attachments are never required.' },
        { name: 'eligibleCategoryIds', label: 'Eligible categories (empty = all)', type: 'multiselect', options: toOptions(categories) },
        { name: 'color', label: 'Colour (#RRGGBB)', placeholder: '#4C6EF5', description: 'Used on the leave calendar.' },
        { name: 'active', label: 'Active', type: 'checkbox' },
      ]}
      emptyValues={{ code: '', name: '', paid: true, unlimited: false, annualDays: 0, accrualMethod: 'ANNUAL_UPFRONT', proRata: true, cfMaxDays: 0, cfExpiryMonths: 0, allowHalfDay: true, allowNegative: false, countWeeklyOff: false, countHoliday: false, attachmentAboveDays: null, eligibleCategoryIds: [], active: true }}
      toValues={(r) => ({ ...r })}
      create={editable ? (v) => api(`/companies/${companyId}/leave-types`, { method: 'POST', body: v }) : undefined}
      update={editable ? (r, v) => api(`/leave-types/${r.id}`, { method: 'PUT', body: { ...v, version: r.version } }) : undefined}
    />
  );
}

interface Job {
  key: string;
  title: string;
  description: string;
  icon: ReactNode;
  run: (asOf: string) => [string, Record<string, string | number>];
}

const JOBS: Job[] = [
  {
    key: 'accrual',
    title: 'Run accrual',
    description: 'Credits monthly-accruing leave up to the selected date.',
    icon: <IconCalendarRepeat size={18} />,
    run: (asOf) => ['accrual', { asOf }],
  },
  {
    key: 'year-end',
    title: 'Carry forward last year',
    description: 'Moves unused days from the previous year into this year, within each policy’s limit.',
    icon: <IconHourglassLow size={18} />,
    run: (asOf) => ['year-end', { year: Number(asOf.slice(0, 4)) - 1 }],
  },
  {
    key: 'expiry',
    title: 'Expire carried-forward',
    description: 'Lapses carried-forward days whose expiry month has passed.',
    icon: <IconClockPause size={18} />,
    run: (asOf) => ['expiry', { asOf }],
  },
];

function JobsPanel({ companyId }: { companyId: string }) {
  const [asOf, setAsOf] = useState<string | null>(new Date().toISOString().slice(0, 10));
  const [running, setRunning] = useState<string | null>(null);
  const run = async (job: Job) => {
    if (!asOf) return;
    const [path, query] = job.run(asOf);
    setRunning(job.key);
    try {
      const r = await api<{ affected: number }>(`/leave/jobs/${path}`, { method: 'POST', query: { companyId, ...query } });
      showSuccess(`Done — ${r.affected} balance(s) affected`);
    } catch (e) {
      showError(e);
    } finally {
      setRunning(null);
    }
  };
  return (
    <Paper withBorder p="lg" maw={720}>
      <Stack gap="md">
        <div>
          <Title order={4}>Balance jobs</Title>
          <Text size="sm" c="dimmed">These jobs also run automatically every night. They are safe to repeat.</Text>
        </div>
        <DateInput label="As of date" valueFormat="D MMM YYYY" value={asOf} onChange={(v) => setAsOf(v ? String(v) : null)} w={{ base: '100%', sm: 220 }} />
        <Stack gap={0}>
          {JOBS.map((job, i) => (
            <div key={job.key}>
              {i > 0 && <Divider />}
              <Group justify="space-between" wrap="wrap" gap="sm" py="sm">
                <Group gap="sm" wrap="nowrap" align="flex-start" style={{ flex: 1, minWidth: 220 }}>
                  <ThemeIcon variant="light" radius="md" size={34}>{job.icon}</ThemeIcon>
                  <div>
                    <Text size="sm" fw={600}>{job.title}</Text>
                    <Text size="xs" c="dimmed">{job.description}</Text>
                  </div>
                </Group>
                <Button
                  variant="light"
                  leftSection={<IconPlayerPlay size={14} />}
                  loading={running === job.key}
                  disabled={!asOf || (running !== null && running !== job.key)}
                  onClick={() => void run(job)}
                  aria-label={job.title}
                >
                  Run
                </Button>
              </Group>
            </div>
          ))}
        </Stack>
      </Stack>
    </Paper>
  );
}

const TABS = ['requests', 'calendar', 'balances', 'types', 'jobs'];

export function LeavePage() {
  const { t } = useTranslation();
  const { companyId } = useCompany();
  const { can } = useAuth();
  const [tab, setTab] = useUrlTab('requests', TABS);
  const header = <PageHeader title={t('nav.leave')} icon={<IconBeach size={22} />} description={t('navHint.leave')} />;
  if (!companyId) {
    return (
      <>
        {header}
        <Paper withBorder>
          <EmptyState icon={<IconBuildingSkyscraper size={28} stroke={1.5} />} title={t('app.selectCompanyFirst')} description={t('app.selectCompanyHint')} />
        </Paper>
      </>
    );
  }
  const showJobs = can('LEAVE:CONFIGURE');
  return (
    <>
      {header}
      <Tabs value={tab === 'jobs' && !showJobs ? 'requests' : tab} onChange={setTab} keepMounted={false}>
        <Tabs.List className="app-tabs-list" mb="lg">
          <Tabs.Tab value="requests" leftSection={<IconListDetails size={16} />}>Requests</Tabs.Tab>
          <Tabs.Tab value="calendar" leftSection={<IconCalendarEvent size={16} />}>Calendar</Tabs.Tab>
          <Tabs.Tab value="balances" leftSection={<IconScale size={16} />}>Balances</Tabs.Tab>
          <Tabs.Tab value="types" leftSection={<IconCategory size={16} />}>Leave types</Tabs.Tab>
          {showJobs && <Tabs.Tab value="jobs" leftSection={<IconAdjustmentsHorizontal size={16} />}>Jobs</Tabs.Tab>}
        </Tabs.List>
        <Tabs.Panel value="requests"><RequestsPanel companyId={companyId} /></Tabs.Panel>
        <Tabs.Panel value="calendar"><LeaveCalendar companyId={companyId} /></Tabs.Panel>
        <Tabs.Panel value="balances"><BalancesPanel companyId={companyId} /></Tabs.Panel>
        <Tabs.Panel value="types"><LeaveTypesPanel companyId={companyId} /></Tabs.Panel>
        {showJobs && <Tabs.Panel value="jobs"><JobsPanel companyId={companyId} /></Tabs.Panel>}
      </Tabs>
    </>
  );
}
