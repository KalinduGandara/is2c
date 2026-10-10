import {
  ActionIcon, Alert, Avatar, Badge, Box, Button, Drawer, Group, Modal, NumberInput, Paper, Select, SimpleGrid, Skeleton, Stack, Table, Tabs, Text,
  Textarea, TextInput, ThemeIcon, Title, Tooltip,
} from '@mantine/core';
import { modals } from '@mantine/modals';
import {
  IconAlertTriangle, IconArrowBackUp, IconBuildingBank, IconCalculator, IconCash, IconCheck, IconCircleCheck, IconCoins, IconDownload, IconFileSpreadsheet,
  IconFileTypeCsv, IconFileTypePdf, IconLock, IconMail, IconPlus, IconReceipt, IconReportMoney, IconSend, IconTrash, IconUsers, IconWallet,
} from '@tabler/icons-react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type ReactNode } from 'react';
import { useParams } from 'react-router';
import { api, download, type Page } from '../../api/client';
import { useAuth } from '../../auth/AuthContext';
import { DataTable } from '../../components/DataTable';
import { EmptyState } from '../../components/EmptyState';
import { formatDate, formatDateTime, humanize } from '../../components/format';
import { showError, showSuccess } from '../../components/notify';
import { PageHeader } from '../../components/PageHeader';
import { PageLoader } from '../../components/PageLoader';
import { StatCard } from '../../components/StatCard';
import { StatusBadge } from '../../components/StatusBadge';
import { useUrlTab } from '../../components/useUrlTab';
import {
  money, periodLabel, PERIOD_STATUS_COLOR,
  type EmailReport, type OneTimeItem, type PayrollComponent, type PayrollExceptionRow, type PayrollPeriod, type PayrollResult, type PeriodStatus,
  type ResultLine, type Totals,
} from './types';

type EmployeeOption = { id: string; employeeNumber: string; firstName: string; lastName: string };

function useEmployeeOptions(companyId: string | undefined) {
  const { data } = useQuery({
    queryKey: ['employee-options', companyId],
    queryFn: () => api<Page<EmployeeOption>>('/employees', { query: { companyId, size: 200, sort: 'employeeNumber,asc' } }),
    enabled: !!companyId,
  });
  return (data?.content ?? []).map((e) => ({ value: e.id, label: `${e.employeeNumber} — ${e.firstName} ${e.lastName}` }));
}

const Money = ({ value, strong }: { value: number | null | undefined; strong?: boolean }) => (
  <Text span size="sm" fw={strong ? 600 : undefined} className="tabular">{money(value)}</Text>
);

/** Workflow steps; labels are verbs so they never repeat the status badge text. */
const STEPS: { status: PeriodStatus; label: string; hint: string }[] = [
  { status: 'OPEN', label: 'Open', hint: 'Period created' },
  { status: 'DRAFT', label: 'Process', hint: 'Calculate pay' },
  { status: 'IN_REVIEW', label: 'Review', hint: 'Check results' },
  { status: 'APPROVED', label: 'Approve', hint: 'Approver sign-off' },
  { status: 'FINALIZED', label: 'Finalize', hint: 'Lock & issue payslips' },
];

function PeriodProgress({ status }: { status: PeriodStatus }) {
  const current = STEPS.findIndex((s) => s.status === status);
  const finished = status === 'FINALIZED';
  return (
    <Paper withBorder p="md">
      <Box component="ol" aria-label="Payroll progress" m={0} p={0} style={{ display: 'flex', listStyle: 'none' }}>
        {STEPS.map((s, i) => {
          const complete = i < current || finished;
          const active = i === current && !finished;
          const reached = complete || active;
          return (
            <Box component="li" key={s.status} aria-current={active ? 'step' : undefined} style={{ flex: 1, minWidth: 0, position: 'relative', textAlign: 'center' }}>
              {i > 0 && (
                <Box aria-hidden style={{
                  position: 'absolute', top: 15, left: '-50%', right: '50%', height: 2,
                  background: reached ? 'var(--mantine-primary-color-filled)' : 'var(--app-border)',
                }} />
              )}
              <ThemeIcon size={32} radius="xl" variant={complete ? 'filled' : active ? 'light' : 'default'} color={reached ? undefined : 'gray'}
                style={{ position: 'relative', zIndex: 1, outline: active ? '2px solid var(--mantine-primary-color-filled)' : undefined }}>
                {complete ? <IconCheck size={16} /> : <Text span size="xs" fw={700}>{i + 1}</Text>}
              </ThemeIcon>
              <Text size="sm" fw={active ? 700 : 500} c={reached ? undefined : 'dimmed'} mt={6} truncate>{s.label}</Text>
              <Text size="xs" c="dimmed" visibleFrom="sm" truncate px={4}>{s.hint}</Text>
            </Box>
          );
        })}
      </Box>
    </Paper>
  );
}

function Workflow({ period, onChanged }: { period: PayrollPeriod; onChanged: () => void }) {
  const { can } = useAuth();
  const [sendingBack, setSendingBack] = useState(false);
  const [comment, setComment] = useState('');
  const [busy, setBusy] = useState<string | null>(null);
  const act = async (path: string, success: string, body?: unknown) => {
    setBusy(path);
    try {
      await api(`/payroll/periods/${period.id}/${path}`, { method: 'POST', body });
      showSuccess(success);
      onChanged();
    } catch (e) {
      showError(e);
    } finally {
      setBusy(null);
    }
  };
  const editable = period.status === 'OPEN' || period.status === 'DRAFT';
  const label = periodLabel(period);
  return (
    <>
      <Group gap="xs" wrap="wrap">
        {can('PAYROLL:EDIT') && editable && (
          <Button variant={period.status === 'OPEN' ? 'filled' : 'default'} leftSection={<IconCalculator size={16} />} loading={busy === 'process'}
            onClick={() => void act('process', 'Payroll processed')}>
            {period.status === 'OPEN' ? 'Process' : 'Reprocess'}
          </Button>
        )}
        {can('PAYROLL:EDIT') && period.status === 'DRAFT' && (
          <Tooltip label="Reprocess first — data changed since the last run" disabled={!period.stale}>
            <Button disabled={period.stale} leftSection={<IconSend size={16} />} loading={busy === 'submit'} onClick={() => void act('submit', 'Submitted for review')}>
              Submit for review
            </Button>
          </Tooltip>
        )}
        {(can('PAYROLL:EDIT') || can('PAYROLL:APPROVE')) && period.status === 'IN_REVIEW' && (
          <Button variant="light" color="orange" leftSection={<IconArrowBackUp size={16} />} loading={busy === 'send-back'} onClick={() => setSendingBack(true)}>
            Send back
          </Button>
        )}
        {can('PAYROLL:APPROVE') && period.status === 'APPROVED' && (
          <Button
            color="green"
            leftSection={<IconLock size={16} />}
            loading={busy === 'finalize'}
            onClick={() =>
              modals.openConfirmModal({
                title: `Finalize ${label} payroll?`,
                children: (
                  <Text size="sm">
                    Finalizing locks all results permanently and assigns payslip numbers. This cannot be undone.
                  </Text>
                ),
                labels: { confirm: 'Finalize', cancel: 'Cancel' },
                confirmProps: { color: 'red' },
                onConfirm: () => void act('finalize', 'Payroll finalized'),
              })
            }
          >
            Finalize
          </Button>
        )}
      </Group>
      <Modal opened={sendingBack} onClose={() => setSendingBack(false)} title="Send back to draft">
        <Stack>
          <Text size="sm" c="dimmed">The period returns to draft so it can be corrected and reprocessed. Explain what needs to change.</Text>
          <Textarea label="Comment" withAsterisk autosize minRows={3} placeholder="e.g. Missing March performance bonus" value={comment}
            onChange={(e) => setComment(e.currentTarget.value)} />
          <Group justify="flex-end">
            <Button variant="default" onClick={() => setSendingBack(false)}>Cancel</Button>
            <Button
              color="orange"
              disabled={!comment.trim()}
              onClick={() => {
                setSendingBack(false);
                void act('send-back', 'Sent back to draft', { comment: comment.trim() });
                setComment('');
              }}
            >
              Send back
            </Button>
          </Group>
        </Stack>
      </Modal>
    </>
  );
}

function ResultDrawer({ period, employeeId, onClose }: { period: PayrollPeriod; employeeId: string | null; onClose: () => void }) {
  const { data, isLoading } = useQuery({
    queryKey: ['payroll-result', period.id, employeeId],
    queryFn: () => api<{ result: PayrollResult; lines: ResultLine[] }>(`/payroll/periods/${period.id}/results/${employeeId}`),
    enabled: !!employeeId,
  });
  const section = (kind: ResultLine['kind'], title: string, total?: number) => {
    const lines = data?.lines.filter((l) => l.kind === kind) ?? [];
    if (lines.length === 0) return null;
    return (
      <Paper withBorder style={{ overflow: 'hidden' }}>
        <Group justify="space-between" px="md" py="xs" bg="light-dark(var(--mantine-color-gray-0), var(--mantine-color-dark-6))">
          <Text fw={600} size="sm">{title}</Text>
          {total !== undefined && <Money value={total} strong />}
        </Group>
        <Table className="app-table">
          <Table.Tbody>
            {lines.map((l) => (
              <Table.Tr key={l.componentCode}>
                <Table.Td>
                  <Text size="sm">{l.componentName}</Text>
                  {l.units !== null && <Text size="xs" c="dimmed">{l.units} units{l.rate !== null ? ` × ${money(l.rate)}` : ''}</Text>}
                </Table.Td>
                <Table.Td ta="right"><Money value={l.amount} /></Table.Td>
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>
      </Paper>
    );
  };
  const r = data?.result;
  return (
    <Drawer opened={employeeId !== null} onClose={onClose} title={<Text fw={600}>Payroll result · {periodLabel(period)}</Text>} position="right" size="md">
      {isLoading && (
        <Stack>
          <Skeleton h={48} />
          <Skeleton h={120} />
          <Skeleton h={120} />
        </Stack>
      )}
      {r && (
        <Stack gap="md">
          <Group gap="sm" wrap="nowrap">
            <Avatar name={r.employeeName} color="initials" radius="xl" />
            <div style={{ minWidth: 0 }}>
              <Text fw={600} truncate>{r.employeeName}</Text>
              <Text size="sm" c="dimmed" truncate>{[r.employeeNumber, r.departmentName, r.designationName].filter(Boolean).join(' · ')}</Text>
            </div>
          </Group>
          <SimpleGrid cols={3} spacing="xs">
            {[['Worked days', r.workedDays], ['No-pay days', r.noPayDays], ['OT hours', r.otHours]].map(([label, value]) => (
              <Paper key={label} withBorder p="xs" ta="center">
                <Text size="xs" c="dimmed">{label}</Text>
                <Text fw={700} className="tabular">{value}</Text>
              </Paper>
            ))}
          </SimpleGrid>
          {section('EARNING', 'Earnings', r.gross)}
          {section('DEDUCTION', 'Deductions', r.deductions)}
          <Paper p="md" radius="md" bg="var(--mantine-primary-color-light)">
            <Group justify="space-between">
              <Text fw={600}>Net pay</Text>
              <Text fw={800} fz="xl" className="tabular">{money(r.net)}</Text>
            </Group>
          </Paper>
          {section('EMPLOYER_CONTRIBUTION', 'Employer contributions', r.employerContributions)}
          {period.status === 'FINALIZED' && (
            <Button leftSection={<IconDownload size={16} />} variant="light"
              onClick={() => void download(`/payroll/periods/${period.id}/payslips/${employeeId}.pdf`).catch(showError)}>
              Payslip {r.payslipNo}
            </Button>
          )}
        </Stack>
      )}
    </Drawer>
  );
}

function ItemsPanel({ period, onChanged }: { period: PayrollPeriod; onChanged: () => void }) {
  const { can } = useAuth();
  const client = useQueryClient();
  const employees = useEmployeeOptions(period.companyId);
  const [adding, setAdding] = useState(false);
  const [saving, setSaving] = useState(false);
  const [draft, setDraft] = useState<{ employeeId: string | null; componentId: string | null; amount: number | string; note: string }>({ employeeId: null, componentId: null, amount: '', note: '' });
  const { data: items = [], isLoading } = useQuery({ queryKey: ['payroll-items', period.id], queryFn: () => api<OneTimeItem[]>(`/payroll/periods/${period.id}/items`) });
  const { data: components = [] } = useQuery({
    queryKey: ['payroll-components', period.companyId],
    queryFn: () => api<PayrollComponent[]>(`/companies/${period.companyId}/payroll-components`),
  });
  const oneTime = components.filter((c) => c.active && c.frequency === 'ONE_TIME' && c.calcType === 'FIXED');
  const editable = can('PAYROLL:EDIT') && (period.status === 'OPEN' || period.status === 'DRAFT');
  const name = (id: string) => employees.find((e) => e.value === id)?.label ?? id;
  const refresh = async () => {
    await client.invalidateQueries({ queryKey: ['payroll-items', period.id] });
    onChanged();
  };
  const save = async () => {
    setSaving(true);
    try {
      await api(`/payroll/periods/${period.id}/items`, { method: 'POST', body: { ...draft, amount: Number(draft.amount), note: draft.note || null } });
      showSuccess('Item added');
      setAdding(false);
      setDraft({ employeeId: null, componentId: null, amount: '', note: '' });
      await refresh();
    } catch (e) {
      showError(e);
    } finally {
      setSaving(false);
    }
  };
  const remove = (item: OneTimeItem) =>
    modals.openConfirmModal({
      title: 'Remove item?',
      children: <Text size="sm">The {money(item.amount)} item for {name(item.employeeId)} will be removed. Reprocess the period to update results.</Text>,
      labels: { confirm: 'Remove', cancel: 'Cancel' },
      confirmProps: { color: 'red' },
      onConfirm: async () => {
        try {
          await api(`/payroll/items/${item.id}`, { method: 'DELETE' });
          await refresh();
        } catch (e) {
          showError(e);
        }
      },
    });
  return (
    <>
      <Group justify="space-between" mb="sm" wrap="wrap" gap="xs">
        <Text size="sm" c="dimmed">Bonuses, arrears and other one-off amounts for this month only.</Text>
        {editable && <Button leftSection={<IconPlus size={16} />} onClick={() => setAdding(true)}>Add item</Button>}
      </Group>
      <DataTable
        rows={items}
        loading={isLoading}
        rowKey={(r) => r.id}
        emptyTitle="No one-time items"
        emptyDescription={editable ? 'Add a bonus or other one-off amount, then reprocess the period.' : 'No one-off amounts were added to this period.'}
        emptyIcon={<IconReceipt size={22} stroke={1.5} />}
        columns={[
          { key: 'emp', header: 'Employee', render: (r) => name(r.employeeId) },
          { key: 'comp', header: 'Component', render: (r) => components.find((c) => c.id === r.componentId)?.name ?? '' },
          { key: 'amount', header: 'Amount', align: 'right', render: (r) => <Money value={r.amount} strong /> },
          { key: 'note', header: 'Note', render: (r) => <Text size="sm" c="dimmed">{r.note || '—'}</Text> },
          { key: 'x', header: '', width: 50, render: (r) => editable && (
            <Tooltip label="Remove item">
              <ActionIcon variant="subtle" color="red" aria-label="Remove item" onClick={() => remove(r)}><IconTrash size={16} /></ActionIcon>
            </Tooltip>
          ) },
        ]}
      />
      <Modal opened={adding} onClose={() => setAdding(false)} title="Add one-time item">
        <Stack>
          <Select label="Employee" withAsterisk searchable placeholder="Search employee" data={employees} value={draft.employeeId} onChange={(v) => setDraft({ ...draft, employeeId: v })} />
          <Select label="Component" withAsterisk placeholder="Select component" data={oneTime.map((c) => ({ value: c.id, label: c.name }))} value={draft.componentId}
            onChange={(v) => setDraft({ ...draft, componentId: v })}
            description={oneTime.length === 0 ? 'No active one-time fixed components — create one on the Components tab.' : undefined} />
          <NumberInput label="Amount" withAsterisk min={0} decimalScale={2} thousandSeparator="," value={draft.amount} onChange={(v) => setDraft({ ...draft, amount: v })} />
          <TextInput label="Note" placeholder="Optional" value={draft.note} onChange={(e) => setDraft({ ...draft, note: e.currentTarget.value })} />
          <Group justify="flex-end">
            <Button variant="default" onClick={() => setAdding(false)}>Cancel</Button>
            <Button loading={saving} disabled={!draft.employeeId || !draft.componentId || !draft.amount} onClick={() => void save()}>Save</Button>
          </Group>
        </Stack>
      </Modal>
    </>
  );
}

const DELIVERY_COLORS = { SENT: 'green', FAILED: 'red', NO_EMAIL: 'gray' };

function ExportButton({ icon, children, onClick }: { icon: ReactNode; children: string; onClick: () => void }) {
  return <Button variant="default" leftSection={icon} justify="flex-start" onClick={onClick}>{children}</Button>;
}

function ExportsPanel({ period }: { period: PayrollPeriod }) {
  const { can } = useAuth();
  const [report, setReport] = useState<EmailReport | null>(null);
  const [sending, setSending] = useState(false);
  const get = (path: string, query?: Record<string, string>) => void download(`/payroll/periods/${period.id}/${path}`, query).catch(showError);
  const finalized = period.status === 'FINALIZED';
  const email = async () => {
    setSending(true);
    try {
      setReport(await api<EmailReport>(`/payroll/periods/${period.id}/payslips/email`, { method: 'POST', body: {} }));
    } catch (e) {
      showError(e);
    } finally {
      setSending(false);
    }
  };
  const xls = <IconFileSpreadsheet size={16} />;
  const pdf = <IconFileTypePdf size={16} />;
  return (
    <Stack>
      {can('PAYROLL:EXPORT') && (
        <Paper withBorder p="lg">
          <Title order={4}>Exports</Title>
          <Text size="sm" c="dimmed" mb="md">Download the payroll register or department totals for this period.</Text>
          <SimpleGrid cols={{ base: 1, sm: 2 }} spacing="lg">
            <Stack gap="xs">
              <Text size="sm" fw={600}>Payroll register</Text>
              <ExportButton icon={xls} onClick={() => get('register', { format: 'xlsx' })}>Payroll register (Excel)</ExportButton>
              <ExportButton icon={<IconFileTypeCsv size={16} />} onClick={() => get('register', { format: 'csv' })}>Payroll register (CSV)</ExportButton>
              <ExportButton icon={pdf} onClick={() => get('register', { format: 'pdf' })}>Payroll register (PDF)</ExportButton>
            </Stack>
            <Stack gap="xs">
              <Text size="sm" fw={600}>Department summary</Text>
              <ExportButton icon={xls} onClick={() => get('department-summary', { format: 'xlsx' })}>Department summary (Excel)</ExportButton>
              <ExportButton icon={pdf} onClick={() => get('department-summary', { format: 'pdf' })}>Department summary (PDF)</ExportButton>
            </Stack>
          </SimpleGrid>
        </Paper>
      )}
      <Paper withBorder p="lg">
        <Title order={4}>Payslips</Title>
        {!finalized ? (
          <EmptyState compact icon={<IconLock size={20} stroke={1.5} />} title="Payslips are not available yet"
            description="Payslips are available once the period is finalized." />
        ) : (
          <>
            <Text size="sm" c="dimmed" mb="md">Download every payslip as one PDF or email each employee their own payslip.</Text>
            <Group>
              <Button leftSection={<IconDownload size={16} />} variant="default" onClick={() => get('payslips.pdf')}>All payslips (PDF)</Button>
              {can('PAYROLL:EXPORT') && (
                <Button leftSection={<IconMail size={16} />} loading={sending} onClick={() => void email()}>Email payslips</Button>
              )}
            </Group>
          </>
        )}
        {report && (
          <Stack mt="md" gap="xs">
            <Group gap="xs">
              <Badge color="green" variant="light">{report.sent} sent</Badge>
              <Badge color={report.failed > 0 ? 'red' : 'gray'} variant="light">{report.failed} not sent</Badge>
            </Group>
            <DataTable
              rows={report.results}
              rowKey={(r) => r.employeeId}
              minWidth={420}
              columns={[
                { key: 'no', header: 'No', render: (r) => r.employeeNumber },
                { key: 'name', header: 'Employee', render: (r) => r.employeeName },
                { key: 'status', header: 'Status', render: (r) => <StatusBadge status={r.status} colors={DELIVERY_COLORS} /> },
              ]}
            />
          </Stack>
        )}
      </Paper>
    </Stack>
  );
}

const TABS = ['results', 'exceptions', 'items', 'exports'];

/** FR-084…089 one payroll period: workflow, review, items, payslips and exports. */
export function PeriodPage() {
  const { id = '' } = useParams();
  const client = useQueryClient();
  const [selected, setSelected] = useState<string | null>(null);
  const [tab, setTab] = useUrlTab('results', TABS);
  const { data: period, isError } = useQuery({ queryKey: ['payroll-period', id], queryFn: () => api<PayrollPeriod>(`/payroll/periods/${id}`) });
  const { data: results = [], isLoading } = useQuery({ queryKey: ['payroll-results', id], queryFn: () => api<PayrollResult[]>(`/payroll/periods/${id}/results`) });
  const { data: totals } = useQuery({ queryKey: ['payroll-totals', id], queryFn: () => api<Totals>(`/payroll/periods/${id}/totals`) });
  const { data: exceptions = [] } = useQuery({ queryKey: ['payroll-exceptions', id], queryFn: () => api<PayrollExceptionRow[]>(`/payroll/periods/${id}/exceptions`) });
  const refresh = () => {
    for (const key of ['payroll-period', 'payroll-results', 'payroll-totals', 'payroll-exceptions']) {
      void client.invalidateQueries({ queryKey: [key, id] });
    }
    void client.invalidateQueries({ queryKey: ['payroll-periods'] });
  };
  const back = { to: '/payroll', label: 'All payroll periods' };
  if (!period) {
    if (!isError) return <PageLoader />;
    return (
      <>
        <PageHeader title="Payroll period" back={back} icon={<IconReportMoney size={22} />} />
        <Paper withBorder>
          <EmptyState title="Payroll period not found" description="It may have been removed, or you may not have access to it." />
        </Paper>
      </>
    );
  }
  const blocking = exceptions.filter((e) => e.blocking).length;
  const name = (employeeId: string | null) => results.find((r) => r.employeeId === employeeId)?.employeeName ?? employeeId ?? 'Whole period';
  const otHours = results.reduce((sum, r) => sum + (r.otHours ?? 0), 0);
  return (
    <>
      <PageHeader
        title={`Payroll — ${periodLabel(period)}`}
        back={back}
        icon={<IconReportMoney size={22} />}
        badge={
          <Group gap={6}>
            <StatusBadge status={period.status} colors={PERIOD_STATUS_COLOR} size="lg" />
            {period.stale && <Badge color="orange" variant="outline" size="lg">Needs reprocessing</Badge>}
          </Group>
        }
        description={
          <>
            {formatDate(period.startDate)} – {formatDate(period.endDate)}
            {period.processedAt && ` · Last processed ${formatDateTime(period.processedAt)}`}
            {period.finalizedAt && ` · Locked ${formatDateTime(period.finalizedAt)}`}
          </>
        }
        actions={<Workflow period={period} onChanged={refresh} />}
      />
      <Stack gap="lg">
        <PeriodProgress status={period.status} />
        {period.stale && (
          <Alert color="orange" variant="light" icon={<IconAlertTriangle size={18} />} title="Results are out of date">
            Attendance, leave, salary or items have changed since it was processed. Reprocess before submitting.
          </Alert>
        )}
        {blocking > 0 && period.status === 'DRAFT' && (
          <Alert color="red" variant="light" icon={<IconAlertTriangle size={18} />} title="Blocking exceptions">
            {blocking} blocking exception(s) must be resolved before submitting.
          </Alert>
        )}
        {totals && totals.employees > 0 && (
          <SimpleGrid cols={{ base: 1, xs: 2, md: 3 }} spacing={{ base: 'sm', md: 'md' }}>
            <StatCard label="Employees" value={totals.employees} icon={<IconUsers size={18} />} hint={otHours > 0 ? `${otHours} OT hours in total` : undefined} />
            <StatCard label="Gross pay" value={money(totals.gross)} icon={<IconCash size={18} />} color="teal" />
            <StatCard label="Deductions" value={money(totals.deductions)} icon={<IconReceipt size={18} />} color="red" />
            <StatCard label="Net pay" value={money(totals.net)} icon={<IconWallet size={18} />} color="green" />
            <StatCard label="Employer contributions" value={money(totals.employerContributions)} icon={<IconBuildingBank size={18} />} color="grape" />
            <StatCard label="Total cost" value={money(totals.gross + totals.employerContributions)} icon={<IconCoins size={18} />} color="orange"
              hint="Gross pay + employer contributions" />
          </SimpleGrid>
        )}
        <Tabs value={tab} onChange={setTab} keepMounted={false}>
          <Tabs.List className="app-tabs-list" mb="lg">
            <Tabs.Tab value="results" leftSection={<IconUsers size={16} />}>Results</Tabs.Tab>
            <Tabs.Tab value="exceptions" leftSection={<IconAlertTriangle size={16} />}
              rightSection={exceptions.length > 0 ? <Badge size="sm" circle color={blocking > 0 ? 'red' : 'yellow'}>{exceptions.length}</Badge> : undefined}>
              Exceptions
            </Tabs.Tab>
            <Tabs.Tab value="items" leftSection={<IconReceipt size={16} />}>One-time items</Tabs.Tab>
            <Tabs.Tab value="exports" leftSection={<IconDownload size={16} />}>Payslips & exports</Tabs.Tab>
          </Tabs.List>
          <Tabs.Panel value="results">
            <DataTable
              rows={results}
              loading={isLoading}
              rowKey={(r) => r.id}
              onRowClick={(r) => setSelected(r.employeeId)}
              rowLabel={(r) => `${r.employeeName} — view breakdown`}
              minWidth={900}
              emptyTitle={period.status === 'OPEN' ? 'Not processed yet' : 'No results'}
              emptyDescription={period.status === 'OPEN'
                ? 'Processing calculates pay for every active employee from attendance, leave and salary structures.'
                : 'No employees were included in this period.'}
              emptyIcon={<IconCalculator size={22} stroke={1.5} />}
              columns={[
                { key: 'no', header: 'No', render: (r) => <Text size="sm" c="dimmed">{r.employeeNumber}</Text> },
                { key: 'name', header: 'Employee', render: (r) => (
                  <Group gap="xs" wrap="nowrap">
                    <Avatar name={r.employeeName} color="initials" size="sm" radius="xl" aria-hidden />
                    <Text size="sm" fw={500}>{r.employeeName}</Text>
                  </Group>
                ) },
                { key: 'dept', header: 'Department', render: (r) => r.departmentName ?? '—' },
                { key: 'gross', header: 'Gross', align: 'right', render: (r) => <Money value={r.gross} /> },
                { key: 'ded', header: 'Deductions', align: 'right', render: (r) => <Money value={r.deductions} /> },
                { key: 'net', header: 'Net', align: 'right', render: (r) => <Money value={r.net} strong /> },
                { key: 'np', header: 'No-pay days', align: 'right', render: (r) => r.noPayDays },
                { key: 'ot', header: 'OT hours', align: 'right', render: (r) => r.otHours },
                { key: 'slip', header: 'Payslip', render: (r) => r.payslipNo ?? <Text span size="sm" c="dimmed">—</Text> },
              ]}
            />
          </Tabs.Panel>
          <Tabs.Panel value="exceptions">
            <DataTable
              rows={exceptions}
              rowKey={(r) => `${r.employeeId}-${r.code}`}
              emptyTitle="No exceptions"
              emptyDescription="Nothing needs attention for this period."
              emptyIcon={<IconCircleCheck size={22} stroke={1.5} />}
              columns={[
                { key: 'emp', header: 'Employee', render: (r) => name(r.employeeId) },
                { key: 'sev', header: 'Severity', render: (r) => <Badge color={r.blocking ? 'red' : 'yellow'} variant="light">{r.blocking ? 'Blocking' : 'Warning'}</Badge> },
                { key: 'code', header: 'Issue', render: (r) => <Text size="sm" fw={500}>{humanize(r.code)}</Text> },
                { key: 'msg', header: 'Details', render: (r) => <Text size="sm" c="dimmed">{r.message}</Text> },
              ]}
            />
          </Tabs.Panel>
          <Tabs.Panel value="items"><ItemsPanel period={period} onChanged={refresh} /></Tabs.Panel>
          <Tabs.Panel value="exports"><ExportsPanel period={period} /></Tabs.Panel>
        </Tabs>
      </Stack>
      <ResultDrawer period={period} employeeId={selected} onClose={() => setSelected(null)} />
    </>
  );
}
