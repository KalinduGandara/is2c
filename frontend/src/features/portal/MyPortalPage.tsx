import {
  Avatar,
  Badge,
  Button,
  Drawer,
  Group,
  Modal,
  Paper,
  Progress,
  SimpleGrid,
  Skeleton,
  Stack,
  Tabs,
  Text,
  ThemeIcon,
  Timeline,
  Title,
  Tooltip,
} from '@mantine/core';
import { MonthPickerInput } from '@mantine/dates';
import { modals } from '@mantine/modals';
import {
  IconBeach,
  IconBriefcase,
  IconBuilding,
  IconCalendarEvent,
  IconCalendarTime,
  IconClockHour4,
  IconDownload,
  IconFiles,
  IconHierarchy2,
  IconKey,
  IconLogin2,
  IconLogout2,
  IconMail,
  IconMapPin,
  IconPhone,
  IconPlus,
  IconReportMoney,
  IconUser,
  IconUserCircle,
} from '@tabler/icons-react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router';
import { api, download } from '../../api/client';
import { DataTable } from '../../components/DataTable';
import { EmptyState } from '../../components/EmptyState';
import { formatDate, humanize } from '../../components/format';
import { showError, showSuccess } from '../../components/notify';
import { PageHeader } from '../../components/PageHeader';
import { PageLoader } from '../../components/PageLoader';
import { StatusBadge } from '../../components/StatusBadge';
import { useUrlTab } from '../../components/useUrlTab';
import { hours, STATUS_COLOR, time, totalOt, type Daily, type DayDetail } from '../attendance/types';
import { ExpiryCell } from '../employees/DocumentsPanel';
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

const TABS = ['profile', 'attendance', 'schedule', 'leave', 'payslips'];

const iso = (d: Date) => d.toISOString().slice(0, 10);
const addDays = (d: Date, n: number) => new Date(d.getTime() + n * 86_400_000);
const weekday = (date: string, style: 'short' | 'long' = 'short') =>
  new Date(`${date}T00:00:00Z`).toLocaleDateString('en-GB', { weekday: style, timeZone: 'UTC' });

function Field({ label, value, icon }: { label: string; value: string | null | undefined; icon: ReactNode }) {
  return (
    <Group gap="sm" wrap="nowrap" align="flex-start">
      <ThemeIcon variant="light" color="gray" size={32} radius="md">{icon}</ThemeIcon>
      <div style={{ minWidth: 0 }}>
        <Text size="xs" c="dimmed">{label}</Text>
        <Text size="sm" fw={500} style={{ overflowWrap: 'anywhere' }}>{value || '—'}</Text>
      </div>
    </Group>
  );
}

function ProfileHero({ profile }: { profile: Profile }) {
  const e = profile.employee;
  const fullName = `${e.firstName} ${e.lastName}`;
  const role = [profile.designationName, profile.departmentName].filter(Boolean).join(' · ');
  return (
    <Paper withBorder p="lg" mb="lg">
      <Group gap="lg" wrap="nowrap" align="center">
        <Avatar name={fullName} color="initials" size={64} radius="xl" visibleFrom="xs" />
        <Avatar name={fullName} color="initials" size={48} radius="xl" hiddenFrom="xs" />
        <Stack gap={4} style={{ minWidth: 0 }}>
          <Group gap="xs" wrap="wrap">
            <Title order={3}>{fullName}</Title>
            <StatusBadge status={e.status} />
          </Group>
          {role && <Text size="sm" c="dimmed">{role}</Text>}
          <Group gap="xs" wrap="wrap">
            <Badge variant="default" radius="sm" className="tabular">{e.employeeNumber}</Badge>
            <Text size="sm" c="dimmed">{profile.companyName}</Text>
          </Group>
        </Stack>
      </Group>
    </Paper>
  );
}

function ProfileTab({ profile }: { profile: Profile }) {
  const { data: docs = [], isLoading } = useQuery({ queryKey: ['my-documents'], queryFn: () => api<Doc[]>('/me/documents') });
  const e = profile.employee;
  return (
    <Stack>
      <Paper withBorder p="lg">
        <Group justify="space-between" mb="md" wrap="wrap" gap="sm">
          <Title order={4}>Employment details</Title>
          <Button variant="default" size="xs" component={Link} to="/change-password" leftSection={<IconKey size={14} />}>Change password</Button>
        </Group>
        <SimpleGrid cols={{ base: 1, xs: 2, md: 3 }} spacing="lg" verticalSpacing="md">
          <Field icon={<IconHierarchy2 size={16} />} label="Department" value={profile.departmentName} />
          <Field icon={<IconBriefcase size={16} />} label="Designation" value={profile.designationName} />
          <Field icon={<IconMapPin size={16} />} label="Branch" value={profile.branchName} />
          <Field icon={<IconUser size={16} />} label="Reporting manager" value={profile.reportingManagerName} />
          <Field icon={<IconCalendarEvent size={16} />} label="Joined" value={formatDate(e.joinDate)} />
          <Field icon={<IconBuilding size={16} />} label="Company" value={profile.companyName} />
        </SimpleGrid>
      </Paper>
      <Paper withBorder p="lg">
        <Title order={4} mb="md">Contact</Title>
        <SimpleGrid cols={{ base: 1, xs: 2 }} spacing="lg">
          <Field icon={<IconMail size={16} />} label="Email" value={e.personalEmail} />
          <Field icon={<IconPhone size={16} />} label="Mobile" value={e.mobile} />
        </SimpleGrid>
      </Paper>
      <Paper withBorder p="lg">
        <Title order={4} mb="sm">My documents</Title>
        {isLoading ? (
          <Skeleton h={40} />
        ) : docs.length === 0 ? (
          <EmptyState compact icon={<IconFiles size={20} />} title="No documents" description="Documents HR adds to your record will appear here." />
        ) : (
          <Stack gap={0}>
            {docs.map((d, i) => (
              <Group key={d.id} justify="space-between" wrap="nowrap" gap="sm" py="sm" style={i > 0 ? { borderTop: '1px solid var(--app-border)' } : undefined}>
                <Group gap="sm" wrap="nowrap" style={{ minWidth: 0 }}>
                  <ThemeIcon variant="light" size={32} radius="md"><IconFiles size={16} /></ThemeIcon>
                  <div style={{ minWidth: 0 }}>
                    <Text size="sm" fw={600} truncate>{d.title}</Text>
                    <Group gap={6} wrap="wrap">
                      <Text size="xs" c="dimmed">{humanize(d.docType)}</Text>
                      {d.expiryDate && <ExpiryCell date={d.expiryDate} />}
                    </Group>
                  </div>
                </Group>
                <Tooltip label={d.fileName}>
                  <Button size="xs" variant="light" leftSection={<IconDownload size={14} />} aria-label={`Download ${d.fileName}`}
                    onClick={() => void download(`/me/documents/${d.id}/content`).catch(showError)}>Download</Button>
                </Tooltip>
              </Group>
            ))}
          </Stack>
        )}
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
  const { data: detail, isLoading: detailLoading } = useQuery({
    queryKey: ['my-attendance-day', open],
    queryFn: () => api<DayDetail>(`/me/attendance/${open}`),
    enabled: !!open,
  });
  const count = (...statuses: string[]) => data.filter((d) => statuses.includes(d.status)).length;
  const summary = [
    { label: 'Present', value: count('PRESENT', 'HALF_DAY'), color: 'teal' },
    { label: 'Absent', value: count('ABSENT'), color: 'red' },
    { label: 'On leave', value: count('ON_LEAVE', 'HALF_DAY_LEAVE'), color: 'blue' },
    { label: 'Late days', value: data.filter((d) => d.lateMinutes > 0).length, color: 'orange' },
  ];
  return (
    <Stack>
      <Group justify="space-between" align="flex-end" wrap="wrap" gap="sm">
        <MonthPickerInput label="Month" value={`${month}-01`} maxDate={new Date()} onChange={(v) => v && setMonth(String(v).slice(0, 7))}
          w={{ base: '100%', xs: 200 }} leftSection={<IconCalendarEvent size={16} />} />
        <Group gap="xs" wrap="wrap">
          {summary.map((s) => (
            <Badge key={s.label} variant="light" color={s.color} size="lg" radius="sm">
              {s.label}: <Text span fw={700} inherit className="tabular">{s.value}</Text>
            </Badge>
          ))}
        </Group>
      </Group>
      <DataTable
        rows={data}
        loading={isLoading}
        rowKey={(r) => r.date}
        onRowClick={(r) => setOpen(r.date)}
        rowLabel={(r) => `Attendance on ${formatDate(r.date)}`}
        minWidth={560}
        emptyIcon={<IconClockHour4 size={20} />}
        emptyTitle="No attendance for this month"
        emptyDescription="Days appear here once your attendance has been processed."
        columns={[
          {
            key: 'date', header: 'Date',
            render: (r) => (
              <Text size="sm" style={{ whiteSpace: 'nowrap' }}>
                <Text span c="dimmed" size="sm" w={36} display="inline-block">{weekday(r.date)}</Text>
                {formatDate(r.date)}
              </Text>
            ),
          },
          { key: 'status', header: 'Status', render: (r) => <StatusBadge status={r.status} colors={STATUS_COLOR} /> },
          { key: 'in', header: 'In', align: 'right', render: (r) => time(r.firstIn) },
          { key: 'out', header: 'Out', align: 'right', render: (r) => time(r.lastOut) },
          { key: 'late', header: 'Late (min)', align: 'right', render: (r) => (r.lateMinutes ? <Text span size="sm" c="orange" fw={600}>{r.lateMinutes}</Text> : '') },
          { key: 'ot', header: 'OT (h)', align: 'right', render: (r) => hours(totalOt(r)) },
        ]}
      />
      <Drawer opened={open !== null} onClose={() => setOpen(null)} position="right"
        title={open ? <Text fw={600}>{weekday(open, 'long')}, {formatDate(open)}</Text> : null}>
        {detailLoading && <Skeleton h={80} />}
        {detail && (
          <Stack gap="lg">
            {detail.daily && (
              <Paper withBorder p="md">
                <Group justify="space-between" mb="sm">
                  <Text size="sm" c="dimmed">Status</Text>
                  <StatusBadge status={detail.daily.status} colors={STATUS_COLOR} />
                </Group>
                <SimpleGrid cols={3} spacing="xs">
                  <div>
                    <Text size="xs" c="dimmed">Worked (h)</Text>
                    <Text fw={600} className="tabular">{hours(detail.daily.workedMinutes) || '0'}</Text>
                  </div>
                  <div>
                    <Text size="xs" c="dimmed">Late (min)</Text>
                    <Text fw={600} className="tabular">{detail.daily.lateMinutes}</Text>
                  </div>
                  <div>
                    <Text size="xs" c="dimmed">OT (h)</Text>
                    <Text fw={600} className="tabular">{hours(totalOt(detail.daily)) || '0'}</Text>
                  </div>
                </SimpleGrid>
              </Paper>
            )}
            <div>
              <Title order={5} mb="sm">Punches</Title>
              {detail.events.length === 0 ? (
                <Text size="sm" c="dimmed">No punches recorded for this day.</Text>
              ) : (
                <Timeline active={detail.events.length - 1} bulletSize={24} lineWidth={2}>
                  {detail.events.map((e) => (
                    <Timeline.Item
                      key={e.id}
                      color={e.direction === 'OUT' ? 'gray' : 'teal'}
                      bullet={e.direction === 'OUT' ? <IconLogout2 size={14} /> : <IconLogin2 size={14} />}
                      title={<Text size="sm" fw={600} className="tabular">{e.time.slice(11, 16)}</Text>}
                    >
                      <Text size="xs" c="dimmed">
                        {humanize(e.direction)}{e.deviceId ? ` · ${e.deviceId}` : ''}
                      </Text>
                    </Timeline.Item>
                  ))}
                </Timeline>
              )}
            </div>
          </Stack>
        )}
      </Drawer>
    </Stack>
  );
}

function ScheduleTab() {
  const start = new Date();
  const todayIso = iso(start);
  const { data = [], isLoading } = useQuery({
    queryKey: ['my-schedule'],
    queryFn: () => api<ScheduleDay[]>('/me/schedule', { query: { from: iso(start), to: iso(addDays(start, 13)) } }),
  });
  if (isLoading) return <Skeleton h={240} radius="md" />;
  if (data.length === 0) {
    return (
      <Paper withBorder>
        <EmptyState icon={<IconCalendarTime size={28} stroke={1.5} />} title="No schedule published" description="Your shifts for the next two weeks will appear here once a roster is published." />
      </Paper>
    );
  }
  return (
    <Paper withBorder style={{ overflow: 'hidden' }}>
      <Group px="md" py="sm" justify="space-between" style={{ borderBottom: '1px solid var(--app-border)' }}>
        <Text fw={600}>Next 14 days</Text>
        <Text size="xs" c="dimmed">{formatDate(data[0].date)} – {formatDate(data[data.length - 1].date)}</Text>
      </Group>
      {data.map((d, i) => {
        const isToday = d.date === todayIso;
        const work = d.dayType === 'WORKDAY';
        const holiday = d.dayType === 'HOLIDAY';
        const label = work ? 'Work day' : holiday ? `Holiday${d.holidayName ? ` — ${d.holidayName}` : ''}` : 'Day off';
        return (
          <Group
            key={d.date}
            px="md"
            py="sm"
            gap="md"
            wrap="nowrap"
            justify="space-between"
            aria-current={isToday ? 'date' : undefined}
            style={{
              borderTop: i > 0 ? '1px solid var(--app-border)' : undefined,
              background: isToday
                ? 'light-dark(var(--mantine-color-brand-0), color-mix(in srgb, var(--mantine-color-brand-9) 30%, transparent))'
                : !work
                  ? 'light-dark(var(--mantine-color-gray-0), var(--mantine-color-dark-6))'
                  : undefined,
            }}
          >
            <Group gap="md" wrap="nowrap" style={{ minWidth: 0 }}>
              <Stack gap={0} align="center" w={44} style={{ flexShrink: 0 }}>
                <Text size="xs" c="dimmed" tt="uppercase" fw={600}>{weekday(d.date)}</Text>
                <Text fw={700} fz={20} lh={1.1} c={isToday ? 'brand' : undefined}>{Number(d.date.slice(8, 10))}</Text>
              </Stack>
              <div style={{ minWidth: 0 }}>
                <Group gap={6} wrap="wrap">
                  <Text size="sm" fw={500} c={work ? undefined : holiday ? 'grape' : 'dimmed'}>{label}</Text>
                  {isToday && <Badge size="xs" variant="filled">Today</Badge>}
                </Group>
                <Text size="xs" c="dimmed">{formatDate(d.date)}</Text>
              </div>
            </Group>
            {d.shiftCode ? (
              <Badge variant="light" radius="sm" size="lg" style={{ flexShrink: 0 }}>{d.shiftCode}</Badge>
            ) : (
              <Text size="sm" c="dimmed">—</Text>
            )}
          </Group>
        );
      })}
    </Paper>
  );
}

function BalanceCard({ b }: { b: Balance }) {
  const taken = b.taken ?? 0;
  const pending = b.pending ?? 0;
  const available = b.available ?? 0;
  const total = Math.max(available + taken + pending, 0);
  const pct = (n: number) => (total > 0 ? (n / total) * 100 : 0);
  return (
    <Paper withBorder p="md">
      <Text size="sm" c="dimmed" fw={500} truncate>{b.leaveTypeName}</Text>
      <Group gap={6} align="baseline" mt={4}>
        <Text fw={700} fz={28} lh={1.1} className="tabular">{b.unlimited ? '∞' : b.available}</Text>
        <Text size="xs" c="dimmed">{b.unlimited ? 'unlimited' : 'days available'}</Text>
      </Group>
      {!b.unlimited && (
        <>
          <Progress.Root size="sm" mt="sm" radius="xl">
            <Progress.Section value={pct(taken)} color="blue" aria-label={`${b.leaveTypeName} taken`} />
            <Progress.Section value={pct(pending)} color="yellow" aria-label={`${b.leaveTypeName} pending`} />
          </Progress.Root>
          <Text size="xs" c="dimmed" mt={6}>taken {taken} · pending {pending}</Text>
        </>
      )}
    </Paper>
  );
}

function LeaveTab({ companyId }: { companyId: string }) {
  const client = useQueryClient();
  const [applying, setApplying] = useState(false);
  const year = new Date().getFullYear();
  const { data: balances = [], isLoading: balancesLoading } = useQuery({ queryKey: ['my-balances', year], queryFn: () => api<Balance[]>('/me/leave/balances', { query: { year } }) });
  const { data: requests = [], isLoading } = useQuery({ queryKey: ['my-leave'], queryFn: () => api<LeaveRequest[]>('/me/leave/requests') });
  const refresh = () => {
    void client.invalidateQueries({ queryKey: ['my-leave'] });
    void client.invalidateQueries({ queryKey: ['my-balances'] });
  };
  const cancel = (r: LeaveRequest) =>
    modals.openConfirmModal({
      title: 'Cancel leave request?',
      children: (
        <Text size="sm">
          Your {r.leaveType ?? 'leave'} request for {r.fromDate === r.toDate ? formatDate(r.fromDate) : `${formatDate(r.fromDate)} → ${formatDate(r.toDate)}`} will be
          cancelled{r.status === 'APPROVED' ? ' and the days returned to your balance' : ''}.
        </Text>
      ),
      labels: { confirm: 'Cancel request', cancel: 'Keep request' },
      confirmProps: { color: 'red' },
      onConfirm: async () => {
        try {
          await api(`/me/leave/requests/${r.id}/cancel`, { method: 'POST', body: {} });
          showSuccess('Leave cancelled');
          refresh();
        } catch (e) {
          showError(e);
        }
      },
    });
  return (
    <Stack>
      <Group justify="space-between" wrap="wrap" gap="sm">
        <div>
          <Title order={4}>Leave balances {year}</Title>
          <Text size="sm" c="dimmed">Available days per leave type.</Text>
        </div>
        <Button leftSection={<IconPlus size={16} />} onClick={() => setApplying(true)}>Apply for leave</Button>
      </Group>
      {balancesLoading ? (
        <SimpleGrid cols={{ base: 1, xs: 2, md: 4 }}>{[0, 1].map((i) => <Skeleton key={i} h={110} radius="md" />)}</SimpleGrid>
      ) : balances.length === 0 ? (
        <Paper withBorder><EmptyState compact icon={<IconBeach size={20} />} title="No leave balances" description="Balances appear once HR assigns leave entitlements." /></Paper>
      ) : (
        <SimpleGrid cols={{ base: 1, xs: 2, md: 4 }}>
          {balances.map((b) => <BalanceCard key={b.leaveTypeId} b={b} />)}
        </SimpleGrid>
      )}
      <Title order={4} mt="sm">My requests</Title>
      <DataTable
        rows={requests}
        loading={isLoading}
        rowKey={(r) => r.id}
        minWidth={540}
        emptyIcon={<IconBeach size={20} />}
        emptyTitle="No leave requests yet"
        emptyDescription="Requests you submit will appear here."
        columns={[
          { key: 'type', header: 'Type', render: (r) => <Text size="sm" fw={500}>{r.leaveType}</Text> },
          {
            key: 'dates', header: 'Dates',
            render: (r) => (
              <>
                <Text size="sm" style={{ whiteSpace: 'nowrap' }}>{r.fromDate === r.toDate ? formatDate(r.fromDate) : `${formatDate(r.fromDate)} → ${formatDate(r.toDate)}`}</Text>
                {r.halfDayPart && <Text size="xs" c="dimmed">{humanize(r.halfDayPart)} half</Text>}
              </>
            ),
          },
          { key: 'days', header: 'Days', align: 'right', render: (r) => <Text span size="sm" fw={600} className="tabular">{r.days}</Text> },
          { key: 'status', header: 'Status', render: (r) => <StatusBadge status={r.status} colors={LEAVE_STATUS_COLOR} /> },
          {
            key: 'x', header: '', align: 'right',
            render: (r) => (r.status === 'PENDING' || r.status === 'APPROVED') && (
              <Button size="xs" variant="subtle" color="red" onClick={() => cancel(r)}>Cancel</Button>
            ),
          },
        ]}
      />
      <Modal opened={applying} onClose={() => setApplying(false)} title="Apply for leave" size="lg">
        <ApplyLeaveForm companyId={companyId} onDone={() => { setApplying(false); refresh(); }} />
      </Modal>
    </Stack>
  );
}

function PayslipsTab() {
  const { data = [], isLoading } = useQuery({ queryKey: ['my-payslips'], queryFn: () => api<OwnPayslip[]>('/me/payslips') });
  return (
    <DataTable
      rows={data}
      loading={isLoading}
      rowKey={(r) => r.periodId}
      minWidth={540}
      emptyIcon={<IconReportMoney size={20} />}
      emptyTitle="No payslips yet"
      emptyDescription="Your payslips appear here once payroll for a month is finalized."
      columns={[
        { key: 'period', header: 'Period', render: (r) => <Text size="sm" fw={600}>{periodLabel(r)}</Text> },
        { key: 'no', header: 'Payslip No', render: (r) => <Text size="sm" c="dimmed" className="tabular">{r.payslipNo}</Text> },
        { key: 'gross', header: 'Gross', align: 'right', render: (r) => <span className="tabular">{money(r.gross)}</span> },
        { key: 'net', header: 'Net', align: 'right', render: (r) => <Text span size="sm" fw={700} className="tabular">{money(r.net)}</Text> },
        {
          key: 'dl', header: '', align: 'right',
          render: (r) => (
            <Button size="xs" variant="light" leftSection={<IconDownload size={14} />} aria-label={`Download ${periodLabel(r)}`}
              onClick={() => void download(`/me/payslips/${r.periodId}.pdf`).catch(showError)}>PDF</Button>
          ),
        },
      ]}
    />
  );
}

/** FR-100 employee self-service portal (own data only; no module permissions needed). */
export function MyPortalPage() {
  const { t } = useTranslation();
  const [tab, setTab] = useUrlTab('profile', TABS);
  const { data: profile, isError } = useQuery({ queryKey: ['my-profile'], queryFn: () => api<Profile>('/me/profile') });
  const header = <PageHeader title={t('nav.selfService')} icon={<IconUserCircle size={22} />} description={t('navHint.self')} />;
  if (isError) {
    return (
      <>
        {header}
        <Paper withBorder>
          <EmptyState icon={<IconUserCircle size={28} stroke={1.5} />} title="No employee profile linked" description="Your user account isn't linked to an employee record. Ask HR to link it to use the portal." />
        </Paper>
      </>
    );
  }
  if (!profile) return <PageLoader rows={5} />;
  return (
    <>
      {header}
      <ProfileHero profile={profile} />
      <Tabs value={tab} onChange={setTab} keepMounted={false}>
        <Tabs.List className="app-tabs-list" mb="lg">
          <Tabs.Tab value="profile" leftSection={<IconUserCircle size={16} />}>Profile</Tabs.Tab>
          <Tabs.Tab value="attendance" leftSection={<IconClockHour4 size={16} />}>Attendance</Tabs.Tab>
          <Tabs.Tab value="schedule" leftSection={<IconCalendarTime size={16} />}>Schedule</Tabs.Tab>
          <Tabs.Tab value="leave" leftSection={<IconBeach size={16} />}>Leave</Tabs.Tab>
          <Tabs.Tab value="payslips" leftSection={<IconReportMoney size={16} />}>Payslips</Tabs.Tab>
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
