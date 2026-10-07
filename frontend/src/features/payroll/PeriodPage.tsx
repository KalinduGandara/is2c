import { Alert, Badge, Button, Drawer, Group, Modal, NumberInput, Paper, Select, SimpleGrid, Stack, Table, Tabs, Text, Textarea, TextInput } from '@mantine/core';
import { modals } from '@mantine/modals';
import { IconAlertTriangle, IconDownload, IconMail, IconPlus, IconTrash } from '@tabler/icons-react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useParams } from 'react-router';
import { api, download, type Page } from '../../api/client';
import { useAuth } from '../../auth/AuthContext';
import { DataTable } from '../../components/DataTable';
import { showError, showSuccess } from '../../components/notify';
import { PageHeader } from '../../components/PageHeader';
import {
  money, periodLabel, PERIOD_STATUS_COLOR,
  type EmailReport, type OneTimeItem, type PayrollComponent, type PayrollExceptionRow, type PayrollPeriod, type PayrollResult, type ResultLine, type Totals,
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

function Workflow({ period, onChanged }: { period: PayrollPeriod; onChanged: () => void }) {
  const { can } = useAuth();
  const [sendingBack, setSendingBack] = useState(false);
  const [comment, setComment] = useState('');
  const act = async (path: string, success: string, body?: unknown) => {
    try {
      await api(`/payroll/periods/${period.id}/${path}`, { method: 'POST', body });
      showSuccess(success);
      onChanged();
    } catch (e) {
      showError(e);
    }
  };
  const editable = period.status === 'OPEN' || period.status === 'DRAFT';
  return (
    <>
      <Group>
        {can('PAYROLL:EDIT') && editable && (
          <Button variant={period.status === 'OPEN' ? 'filled' : 'light'} onClick={() => void act('process', 'Payroll processed')}>
            {period.status === 'OPEN' ? 'Process' : 'Reprocess'}
          </Button>
        )}
        {can('PAYROLL:EDIT') && period.status === 'DRAFT' && (
          <Button disabled={period.stale} onClick={() => void act('submit', 'Submitted for review')}>Submit for review</Button>
        )}
        {(can('PAYROLL:EDIT') || can('PAYROLL:APPROVE')) && period.status === 'IN_REVIEW' && (
          <Button variant="light" color="orange" onClick={() => setSendingBack(true)}>Send back</Button>
        )}
        {can('PAYROLL:APPROVE') && period.status === 'APPROVED' && (
          <Button
            color="green"
            onClick={() =>
              modals.openConfirmModal({
                title: 'Finalize payroll',
                children: 'Finalizing locks all results permanently and assigns payslip numbers. This cannot be undone.',
                labels: { confirm: 'Finalize', cancel: 'Cancel' },
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
          <Textarea label="Comment" withAsterisk value={comment} onChange={(e) => setComment(e.currentTarget.value)} />
          <Group justify="flex-end">
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

function Stat({ label, value }: { label: string; value: string | number }) {
  return (
    <Paper withBorder p="sm">
      <Text size="xs" c="dimmed">{label}</Text>
      <Text fw={600} size="lg">{value}</Text>
    </Paper>
  );
}

function ResultDrawer({ period, employeeId, onClose }: { period: PayrollPeriod; employeeId: string | null; onClose: () => void }) {
  const { data } = useQuery({
    queryKey: ['payroll-result', period.id, employeeId],
    queryFn: () => api<{ result: PayrollResult; lines: ResultLine[] }>(`/payroll/periods/${period.id}/results/${employeeId}`),
    enabled: !!employeeId,
  });
  const section = (kind: ResultLine['kind'], title: string) => {
    const lines = data?.lines.filter((l) => l.kind === kind) ?? [];
    if (lines.length === 0) return null;
    return (
      <>
        <Text fw={600} mt="sm">{title}</Text>
        <Table>
          <Table.Tbody>
            {lines.map((l) => (
              <Table.Tr key={l.componentCode}>
                <Table.Td>{l.componentName}{l.units !== null ? ` (${l.units})` : ''}</Table.Td>
                <Table.Td ta="right">{money(l.amount)}</Table.Td>
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>
      </>
    );
  };
  return (
    <Drawer opened={employeeId !== null} onClose={onClose} title="Payroll result" position="right" size="md">
      {data && (
        <Stack gap="xs">
          <Text fw={500}>{data.result.employeeNumber} {data.result.employeeName}</Text>
          <Text size="sm" c="dimmed">{data.result.departmentName} · {data.result.designationName}</Text>
          <Text size="sm">Worked days {data.result.workedDays} · No-pay days {data.result.noPayDays} · OT hours {data.result.otHours}</Text>
          {section('EARNING', 'Earnings')}
          {section('DEDUCTION', 'Deductions')}
          <Group justify="space-between"><Text fw={600}>Net pay</Text><Text fw={700}>{money(data.result.net)}</Text></Group>
          {section('EMPLOYER_CONTRIBUTION', 'Employer contributions')}
          {period.status === 'FINALIZED' && (
            <Button leftSection={<IconDownload size={16} />} variant="light" mt="md"
              onClick={() => void download(`/payroll/periods/${period.id}/payslips/${employeeId}.pdf`).catch(showError)}>
              Payslip {data.result.payslipNo}
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
  const [draft, setDraft] = useState<{ employeeId: string | null; componentId: string | null; amount: number | string; note: string }>({ employeeId: null, componentId: null, amount: '', note: '' });
  const { data: items = [] } = useQuery({ queryKey: ['payroll-items', period.id], queryFn: () => api<OneTimeItem[]>(`/payroll/periods/${period.id}/items`) });
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
    try {
      await api(`/payroll/periods/${period.id}/items`, { method: 'POST', body: { ...draft, amount: Number(draft.amount), note: draft.note || null } });
      showSuccess('Item added');
      setAdding(false);
      setDraft({ employeeId: null, componentId: null, amount: '', note: '' });
      await refresh();
    } catch (e) {
      showError(e);
    }
  };
  const remove = async (item: OneTimeItem) => {
    try {
      await api(`/payroll/items/${item.id}`, { method: 'DELETE' });
      await refresh();
    } catch (e) {
      showError(e);
    }
  };
  return (
    <>
      {editable && (
        <Group justify="flex-end" mb="sm">
          <Button leftSection={<IconPlus size={16} />} onClick={() => setAdding(true)}>Add item</Button>
        </Group>
      )}
      <DataTable
        rows={items}
        rowKey={(r) => r.id}
        columns={[
          { key: 'emp', header: 'Employee', render: (r) => name(r.employeeId) },
          { key: 'comp', header: 'Component', render: (r) => components.find((c) => c.id === r.componentId)?.name ?? '' },
          { key: 'amount', header: 'Amount', render: (r) => money(r.amount) },
          { key: 'note', header: 'Note', render: (r) => r.note },
          { key: 'x', header: '', render: (r) => editable && (
            <Button size="xs" variant="subtle" color="red" aria-label="Remove item" onClick={() => void remove(r)}><IconTrash size={14} /></Button>
          ) },
        ]}
      />
      <Modal opened={adding} onClose={() => setAdding(false)} title="One-time item">
        <Stack>
          <Select label="Employee" searchable data={employees} value={draft.employeeId} onChange={(v) => setDraft({ ...draft, employeeId: v })} />
          <Select label="Component" data={oneTime.map((c) => ({ value: c.id, label: c.name }))} value={draft.componentId} onChange={(v) => setDraft({ ...draft, componentId: v })} />
          <NumberInput label="Amount" min={0} decimalScale={2} value={draft.amount} onChange={(v) => setDraft({ ...draft, amount: v })} />
          <TextInput label="Note" value={draft.note} onChange={(e) => setDraft({ ...draft, note: e.currentTarget.value })} />
          <Group justify="flex-end"><Button disabled={!draft.employeeId || !draft.componentId || !draft.amount} onClick={() => void save()}>Save</Button></Group>
        </Stack>
      </Modal>
    </>
  );
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
  return (
    <Stack>
      {can('PAYROLL:EXPORT') && (
        <Paper withBorder p="md">
          <Text fw={600} mb="xs">Exports</Text>
          <Group>
            <Button variant="light" onClick={() => get('register', { format: 'xlsx' })}>Payroll register (Excel)</Button>
            <Button variant="light" onClick={() => get('register', { format: 'csv' })}>Payroll register (CSV)</Button>
            <Button variant="light" onClick={() => get('register', { format: 'pdf' })}>Payroll register (PDF)</Button>
            <Button variant="light" onClick={() => get('department-summary', { format: 'xlsx' })}>Department summary (Excel)</Button>
            <Button variant="light" onClick={() => get('department-summary', { format: 'pdf' })}>Department summary (PDF)</Button>
          </Group>
        </Paper>
      )}
      <Paper withBorder p="md">
        <Text fw={600} mb="xs">Payslips</Text>
        {!finalized ? (
          <Text size="sm" c="dimmed">Payslips are available once the period is finalized.</Text>
        ) : (
          <Group>
            <Button leftSection={<IconDownload size={16} />} variant="light" onClick={() => get('payslips.pdf')}>All payslips (PDF)</Button>
            {can('PAYROLL:EXPORT') && (
              <Button leftSection={<IconMail size={16} />} loading={sending} onClick={() => void email()}>Email payslips</Button>
            )}
          </Group>
        )}
        {report && (
          <Stack mt="md" gap="xs">
            <Text size="sm">Sent {report.sent}, not sent {report.failed}</Text>
            <DataTable
              rows={report.results}
              rowKey={(r) => r.employeeId}
              columns={[
                { key: 'no', header: 'No', render: (r) => r.employeeNumber },
                { key: 'name', header: 'Employee', render: (r) => r.employeeName },
                { key: 'status', header: 'Status', render: (r) => <Badge color={r.status === 'SENT' ? 'green' : r.status === 'FAILED' ? 'red' : 'gray'}>{r.status}</Badge> },
              ]}
            />
          </Stack>
        )}
      </Paper>
    </Stack>
  );
}

/** FR-084…089 one payroll period: workflow, review, items, payslips and exports. */
export function PeriodPage() {
  const { id = '' } = useParams();
  const client = useQueryClient();
  const [selected, setSelected] = useState<string | null>(null);
  const { data: period } = useQuery({ queryKey: ['payroll-period', id], queryFn: () => api<PayrollPeriod>(`/payroll/periods/${id}`) });
  const { data: results = [], isLoading } = useQuery({ queryKey: ['payroll-results', id], queryFn: () => api<PayrollResult[]>(`/payroll/periods/${id}/results`) });
  const { data: totals } = useQuery({ queryKey: ['payroll-totals', id], queryFn: () => api<Totals>(`/payroll/periods/${id}/totals`) });
  const { data: exceptions = [] } = useQuery({ queryKey: ['payroll-exceptions', id], queryFn: () => api<PayrollExceptionRow[]>(`/payroll/periods/${id}/exceptions`) });
  const refresh = () => {
    for (const key of ['payroll-period', 'payroll-results', 'payroll-totals', 'payroll-exceptions']) {
      void client.invalidateQueries({ queryKey: [key, id] });
    }
    void client.invalidateQueries({ queryKey: ['payroll-periods'] });
  };
  if (!period) return null;
  const blocking = exceptions.filter((e) => e.blocking).length;
  const name = (employeeId: string | null) => results.find((r) => r.employeeId === employeeId)?.employeeName ?? employeeId ?? '';
  return (
    <>
      <PageHeader title={`Payroll — ${periodLabel(period)}`} />
      <Stack>
        <Group justify="space-between">
          <Group>
            <Badge size="lg" color={PERIOD_STATUS_COLOR[period.status]}>{period.status}</Badge>
            <Text size="sm" c="dimmed">{period.startDate} → {period.endDate}</Text>
          </Group>
          <Workflow period={period} onChanged={refresh} />
        </Group>
        {period.stale && (
          <Alert color="orange" icon={<IconAlertTriangle size={16} />}>
            Attendance, leave, salary or items have changed since it was processed. Reprocess before submitting.
          </Alert>
        )}
        {blocking > 0 && period.status === 'DRAFT' && (
          <Alert color="red" icon={<IconAlertTriangle size={16} />}>{blocking} blocking exception(s) must be resolved before submitting.</Alert>
        )}
        {totals && (
          <SimpleGrid cols={{ base: 2, sm: 5 }}>
            <Stat label="Employees" value={totals.employees} />
            <Stat label="Gross" value={money(totals.gross)} />
            <Stat label="Deductions" value={money(totals.deductions)} />
            <Stat label="Net" value={money(totals.net)} />
            <Stat label="Employer contributions" value={money(totals.employerContributions)} />
          </SimpleGrid>
        )}
        <Tabs defaultValue="results" keepMounted={false}>
          <Tabs.List mb="md">
            <Tabs.Tab value="results">Results</Tabs.Tab>
            <Tabs.Tab value="exceptions">Exceptions ({exceptions.length})</Tabs.Tab>
            <Tabs.Tab value="items">One-time items</Tabs.Tab>
            <Tabs.Tab value="exports">Payslips & exports</Tabs.Tab>
          </Tabs.List>
          <Tabs.Panel value="results">
            <DataTable
              rows={results}
              loading={isLoading}
              rowKey={(r) => r.id}
              onRowClick={(r) => setSelected(r.employeeId)}
              columns={[
                { key: 'no', header: 'No', render: (r) => r.employeeNumber },
                { key: 'name', header: 'Employee', render: (r) => r.employeeName },
                { key: 'dept', header: 'Department', render: (r) => r.departmentName },
                { key: 'gross', header: 'Gross', render: (r) => money(r.gross) },
                { key: 'ded', header: 'Deductions', render: (r) => money(r.deductions) },
                { key: 'net', header: 'Net', render: (r) => money(r.net) },
                { key: 'np', header: 'No-pay days', render: (r) => r.noPayDays },
                { key: 'ot', header: 'OT hours', render: (r) => r.otHours },
                { key: 'slip', header: 'Payslip', render: (r) => r.payslipNo },
              ]}
            />
          </Tabs.Panel>
          <Tabs.Panel value="exceptions">
            <DataTable
              rows={exceptions}
              rowKey={(r) => `${r.employeeId}-${r.code}`}
              columns={[
                { key: 'emp', header: 'Employee', render: (r) => name(r.employeeId) },
                { key: 'code', header: 'Code', render: (r) => <Badge color={r.blocking ? 'red' : 'yellow'}>{r.code}</Badge> },
                { key: 'msg', header: 'Message', render: (r) => r.message },
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
