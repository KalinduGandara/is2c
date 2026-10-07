import { Badge, Button, Drawer, Group, Modal, Paper, SimpleGrid, Stack, Table, Tabs, Text, TextInput } from '@mantine/core';
import { IconDownload, IconKey, IconPlus } from '@tabler/icons-react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router';
import { api, download } from '../../api/client';
import { DataTable } from '../../components/DataTable';
import { showError, showSuccess } from '../../components/notify';
import { PageHeader } from '../../components/PageHeader';
import { hours, STATUS_COLOR, time, totalOt, type Daily, type DayDetail } from '../attendance/types';
import { ApplyLeaveForm } from '../leave/ApplyLeaveForm';
import { LEAVE_STATUS_COLOR, type Balance, type LeaveRequest } from '../leave/types';
import { money, periodLabel } from '../payroll/types';

interface Profile {
  employee: { id: string; companyId: string; employeeNumber: string; firstName: string; lastName: string; personalEmail: string | null; mobile: string | null; joinDate: string; status: string };
  companyName: string;
  branchName: string | null;
  departmentName: string | null;
  designationName: string | null;
  reportingManagerName: string | null;
}

interface OwnPayslip { periodId: string; year: number; month: number; payslipNo: string; gross: number; net: number }
interface ScheduleDay { date: string; dayType: string; shiftCode: string | null; holidayName?: string | null }
interface Doc { id: string; docType: string; title: string; fileName: string; expiryDate: string | null }

const iso = (d: Date) => d.toISOString().slice(0, 10);
const addDays = (d: Date, n: number) => new Date(d.getTime() + n * 86_400_000);

function Field({ label, value }: { label: string; value: string | null | undefined }) {
  return (
    <div>
      <Text size="xs" c="dimmed">{label}</Text>
      <Text>{value || '—'}</Text>
    </div>
  );
}

function ProfileTab({ profile }: { profile: Profile }) {
  const { data: docs = [] } = useQuery({ queryKey: ['my-documents'], queryFn: () => api<Doc[]>('/me/documents') });
  const e = profile.employee;
  return (
    <Stack>
      <Paper withBorder p="md">
        <Text fw={600} size="lg">{e.firstName} {e.lastName}</Text>
        <Text size="sm" c="dimmed" mb="sm">{e.employeeNumber} · {profile.companyName}</Text>
        <SimpleGrid cols={{ base: 1, sm: 3 }}>
          <Field label="Department" value={profile.departmentName} />
          <Field label="Designation" value={profile.designationName} />
          <Field label="Branch" value={profile.branchName} />
          <Field label="Reporting manager" value={profile.reportingManagerName} />
          <Field label="Joined" value={e.joinDate} />
          <Field label="Email" value={e.personalEmail} />
        </SimpleGrid>
        <Button mt="md" variant="light" component={Link} to="/change-password" leftSection={<IconKey size={16} />}>Change password</Button>
      </Paper>
      <Paper withBorder p="md">
        <Text fw={600} mb="xs">My documents</Text>
        {docs.length === 0 && <Text size="sm" c="dimmed">No documents.</Text>}
        {docs.map((d) => (
          <Group key={d.id} justify="space-between">
            <Text size="sm">{d.title} <Text span c="dimmed">({d.docType}{d.expiryDate ? `, expires ${d.expiryDate}` : ''})</Text></Text>
            <Button size="xs" variant="subtle" leftSection={<IconDownload size={14} />}
              onClick={() => void download(`/me/documents/${d.id}/content`).catch(showError)}>{d.fileName}</Button>
          </Group>
        ))}
      </Paper>
    </Stack>
  );
}

function AttendanceTab() {
  const [month, setMonth] = useState(iso(new Date()).slice(0, 7));
  const [open, setOpen] = useState<string | null>(null);
  const from = `${month}-01`;
  const to = iso(addDays(new Date(`${from}T00:00:00Z`), 40)).slice(0, 7) + '-01';
  const last = iso(addDays(new Date(`${to}T00:00:00Z`), -1));
  const { data = [], isLoading } = useQuery({
    queryKey: ['my-attendance', month],
    queryFn: () => api<Daily[]>('/me/attendance', { query: { from, to: last } }),
    enabled: /^\d{4}-\d{2}$/.test(month),
  });
  const { data: detail } = useQuery({
    queryKey: ['my-attendance-day', open],
    queryFn: () => api<DayDetail>(`/me/attendance/${open}`),
    enabled: !!open,
  });
  return (
    <>
      <TextInput label="Month" placeholder="YYYY-MM" value={month} onChange={(e) => setMonth(e.currentTarget.value)} w={140} mb="sm" />
      <DataTable
        rows={data}
        loading={isLoading}
        rowKey={(r) => r.date}
        onRowClick={(r) => setOpen(r.date)}
        columns={[
          { key: 'date', header: 'Date', render: (r) => r.date },
          { key: 'status', header: 'Status', render: (r) => <Badge color={STATUS_COLOR[r.status]}>{r.status}</Badge> },
          { key: 'in', header: 'IN', render: (r) => time(r.firstIn) },
          { key: 'out', header: 'OUT', render: (r) => time(r.lastOut) },
          { key: 'late', header: 'Late (min)', render: (r) => r.lateMinutes || '' },
          { key: 'ot', header: 'OT (h)', render: (r) => hours(totalOt(r)) },
        ]}
      />
      <Drawer opened={open !== null} onClose={() => setOpen(null)} title={`Attendance ${open ?? ''}`} position="right">
        {detail && (
          <Stack gap="xs">
            <Text fw={600}>Punches</Text>
            {detail.events.length === 0 && <Text size="sm" c="dimmed">No punches.</Text>}
            {detail.events.map((e) => <Text key={e.id} size="sm">{e.time.replace('T', ' ').slice(0, 16)} — {e.direction}</Text>)}
          </Stack>
        )}
      </Drawer>
    </>
  );
}

function ScheduleTab() {
  const start = new Date();
  const { data = [] } = useQuery({
    queryKey: ['my-schedule'],
    queryFn: () => api<ScheduleDay[]>('/me/schedule', { query: { from: iso(start), to: iso(addDays(start, 13)) } }),
  });
  return (
    <Table striped withTableBorder>
      <Table.Thead><Table.Tr><Table.Th>Date</Table.Th><Table.Th>Day</Table.Th><Table.Th>Shift</Table.Th></Table.Tr></Table.Thead>
      <Table.Tbody>
        {data.map((d) => (
          <Table.Tr key={d.date}>
            <Table.Td>{d.date} {new Date(`${d.date}T00:00:00Z`).toLocaleDateString('en-US', { weekday: 'short', timeZone: 'UTC' })}</Table.Td>
            <Table.Td>{d.dayType === 'WORKDAY' ? 'Work' : d.dayType === 'HOLIDAY' ? `Holiday${d.holidayName ? ` — ${d.holidayName}` : ''}` : 'Off'}</Table.Td>
            <Table.Td>{d.shiftCode ?? '—'}</Table.Td>
          </Table.Tr>
        ))}
      </Table.Tbody>
    </Table>
  );
}

function LeaveTab({ companyId }: { companyId: string }) {
  const client = useQueryClient();
  const [applying, setApplying] = useState(false);
  const year = new Date().getFullYear();
  const { data: balances = [] } = useQuery({ queryKey: ['my-balances', year], queryFn: () => api<Balance[]>('/me/leave/balances', { query: { year } }) });
  const { data: requests = [] } = useQuery({ queryKey: ['my-leave'], queryFn: () => api<LeaveRequest[]>('/me/leave/requests') });
  const refresh = () => {
    void client.invalidateQueries({ queryKey: ['my-leave'] });
    void client.invalidateQueries({ queryKey: ['my-balances'] });
  };
  const cancel = async (r: LeaveRequest) => {
    try {
      await api(`/me/leave/requests/${r.id}/cancel`, { method: 'POST', body: {} });
      showSuccess('Leave cancelled');
      refresh();
    } catch (e) {
      showError(e);
    }
  };
  return (
    <Stack>
      <SimpleGrid cols={{ base: 2, sm: 4 }}>
        {balances.map((b) => (
          <Paper key={b.leaveTypeId} withBorder p="sm">
            <Text size="sm" c="dimmed">{b.leaveTypeName}</Text>
            <Text fw={700} fz={24}>{b.unlimited ? '∞' : b.available}</Text>
            {!b.unlimited && <Text size="xs" c="dimmed">taken {b.taken} · pending {b.pending}</Text>}
          </Paper>
        ))}
      </SimpleGrid>
      <Group justify="flex-end"><Button leftSection={<IconPlus size={16} />} onClick={() => setApplying(true)}>Apply for leave</Button></Group>
      <DataTable
        rows={requests}
        rowKey={(r) => r.id}
        columns={[
          { key: 'type', header: 'Type', render: (r) => r.leaveType },
          { key: 'dates', header: 'Dates', render: (r) => (r.fromDate === r.toDate ? r.fromDate : `${r.fromDate} → ${r.toDate}`) },
          { key: 'days', header: 'Days', render: (r) => r.days },
          { key: 'status', header: 'Status', render: (r) => <Badge color={LEAVE_STATUS_COLOR[r.status]}>{r.status}</Badge> },
          { key: 'x', header: '', render: (r) => (r.status === 'PENDING' || r.status === 'APPROVED') && (
            <Button size="xs" variant="subtle" color="red" onClick={() => void cancel(r)}>Cancel</Button>
          ) },
        ]}
      />
      <Modal opened={applying} onClose={() => setApplying(false)} title="Apply for leave" size="lg">
        <ApplyLeaveForm companyId={companyId} onDone={() => { setApplying(false); refresh(); }} />
      </Modal>
    </Stack>
  );
}

function PayslipsTab() {
  const { data = [] } = useQuery({ queryKey: ['my-payslips'], queryFn: () => api<OwnPayslip[]>('/me/payslips') });
  return (
    <DataTable
      rows={data}
      rowKey={(r) => r.periodId}
      columns={[
        { key: 'period', header: 'Period', render: (r) => periodLabel(r) },
        { key: 'no', header: 'Payslip No', render: (r) => r.payslipNo },
        { key: 'gross', header: 'Gross', render: (r) => money(r.gross) },
        { key: 'net', header: 'Net', render: (r) => money(r.net) },
        { key: 'dl', header: '', render: (r) => (
          <Button size="xs" variant="light" leftSection={<IconDownload size={14} />} aria-label={`Download ${periodLabel(r)}`}
            onClick={() => void download(`/me/payslips/${r.periodId}.pdf`).catch(showError)}>PDF</Button>
        ) },
      ]}
    />
  );
}

/** FR-100 employee self-service portal (own data only; no module permissions needed). */
export function MyPortalPage() {
  const { t } = useTranslation();
  const { data: profile } = useQuery({ queryKey: ['my-profile'], queryFn: () => api<Profile>('/me/profile') });
  if (!profile) return null;
  return (
    <>
      <PageHeader title={t('nav.selfService')} />
      <Tabs defaultValue="profile" keepMounted={false}>
        <Tabs.List mb="md">
          <Tabs.Tab value="profile">Profile</Tabs.Tab>
          <Tabs.Tab value="attendance">Attendance</Tabs.Tab>
          <Tabs.Tab value="schedule">Schedule</Tabs.Tab>
          <Tabs.Tab value="leave">Leave</Tabs.Tab>
          <Tabs.Tab value="payslips">Payslips</Tabs.Tab>
        </Tabs.List>
        <Tabs.Panel value="profile"><ProfileTab profile={profile} /></Tabs.Panel>
        <Tabs.Panel value="attendance"><AttendanceTab /></Tabs.Panel>
        <Tabs.Panel value="schedule"><ScheduleTab /></Tabs.Panel>
        <Tabs.Panel value="leave"><LeaveTab companyId={profile.employee.companyId} /></Tabs.Panel>
        <Tabs.Panel value="payslips"><PayslipsTab /></Tabs.Panel>
      </Tabs>
    </>
  );
}
