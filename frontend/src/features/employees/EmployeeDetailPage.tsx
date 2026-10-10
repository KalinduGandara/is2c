import { Alert, Anchor, Avatar, Badge, Button, Group, Modal, Paper, Radio, SimpleGrid, Stack, Tabs, Text, TextInput, Title } from '@mantine/core';
import { DateInput } from '@mantine/dates';
import {
  IconArrowLeft,
  IconBriefcase,
  IconBuildingSkyscraper,
  IconCalendarEvent,
  IconFiles,
  IconHierarchy2,
  IconHistory,
  IconInfoCircle,
  IconMapPin,
  IconReportMoney,
  IconUserCircle,
  IconUserCog,
  IconUserPlus,
} from '@tabler/icons-react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useNavigate, useParams } from 'react-router';
import { api, type Page } from '../../api/client';
import { useCompany } from '../../app/CompanyContext';
import { useAuth } from '../../auth/AuthContext';
import { DataTable } from '../../components/DataTable';
import { EmptyState } from '../../components/EmptyState';
import { formatDate, formatDateTime, humanize } from '../../components/format';
import { showError, showSuccess } from '../../components/notify';
import { PageHeader } from '../../components/PageHeader';
import { PageLoader } from '../../components/PageLoader';
import { StatusBadge } from '../../components/StatusBadge';
import { useUrlTab } from '../../components/useUrlTab';
import { SalaryStructurePanel } from '../payroll/SalaryStructurePanel';
import { useBranches, useDepartments, useLookups } from '../org/useOrgData';
import { DocumentsPanel } from './DocumentsPanel';
import { EmployeeForm } from './EmployeeForm';
import { EMPLOYEE_STATUSES, STATUS_COLORS, type Employee, type EmployeeListItem, type EmployeeStatus, type HistoryEntry } from './types';

const STATUS_HELP: Record<EmployeeStatus, { description: string; consequence: string }> = {
  ACTIVE: {
    description: 'Currently employed and working.',
    consequence: 'The employee is included again in attendance, leave and payroll from the effective date.',
  },
  RESIGNED: {
    description: 'Left the company voluntarily.',
    consequence: 'The employee will be excluded from attendance, leave and payroll after the exit date.',
  },
  TERMINATED: {
    description: 'Employment ended by the company.',
    consequence: 'The employee will be excluded from attendance, leave and payroll after the exit date.',
  },
  INACTIVE: {
    description: 'Temporarily inactive (e.g. long-term leave or suspension).',
    consequence: 'The employee is hidden from active lists and is not expected to work until reactivated.',
  },
};

const EVENT_COLORS: Record<string, string> = {
  JOINED: 'green',
  TRANSFER: 'blue',
  PROMOTION: 'grape',
  STATUS_CHANGE: 'orange',
  CHANGE: 'gray',
};

function StatusModal({ employee, onClose, onDone }: { employee: Employee; onClose: () => void; onDone: () => void }) {
  const [status, setStatus] = useState<EmployeeStatus | null>(null);
  const [exitDate, setExitDate] = useState<string | null>(null);
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);
  const leaving = status === 'RESIGNED' || status === 'TERMINATED';
  const submit = async () => {
    setBusy(true);
    try {
      await api(`/employees/${employee.id}/status`, { method: 'POST', body: { status, exitDate: leaving ? exitDate : null, reason: reason || null } });
      showSuccess('Status changed');
      onDone();
    } catch (e) {
      showError(e);
    } finally {
      setBusy(false);
    }
  };
  return (
    <Modal opened onClose={onClose} title="Change employment status" size="lg">
      <Stack>
        <Group gap="xs">
          <Text size="sm" c="dimmed">Current status</Text>
          <StatusBadge status={employee.status} colors={STATUS_COLORS} />
        </Group>
        <Radio.Group label="New status" value={status} onChange={(v) => setStatus(v as EmployeeStatus)} withAsterisk>
          <SimpleGrid cols={{ base: 1, sm: 2 }} mt="xs" spacing="sm">
            {EMPLOYEE_STATUSES.filter((s) => s !== employee.status).map((s) => (
              <Radio.Card key={s} value={s} p="sm" radius="md">
                <Group wrap="nowrap" align="flex-start" gap="sm">
                  <Radio.Indicator />
                  <div>
                    <Text fw={600} size="sm">{humanize(s)}</Text>
                    <Text size="xs" c="dimmed">{STATUS_HELP[s].description}</Text>
                  </div>
                </Group>
              </Radio.Card>
            ))}
          </SimpleGrid>
        </Radio.Group>
        {leaving && (
          <DateInput label="Exit date" withAsterisk placeholder="YYYY-MM-DD" valueFormat="YYYY-MM-DD" minDate={employee.joinDate}
            description="Last day of employment" value={exitDate} onChange={(v) => setExitDate(v ? String(v) : null)} />
        )}
        <TextInput label="Reason" placeholder="Optional — recorded in the employment history" value={reason} onChange={(e) => setReason(e.currentTarget.value)} />
        {status && (
          <Alert variant="light" color={leaving ? 'orange' : 'blue'} icon={<IconInfoCircle size={18} />}>
            {STATUS_HELP[status].consequence}
          </Alert>
        )}
        <Group justify="flex-end">
          <Button variant="default" onClick={onClose}>Cancel</Button>
          <Button onClick={() => void submit()} loading={busy} disabled={!status || (leaving && !exitDate)} color={leaving ? 'red' : undefined}>Save</Button>
        </Group>
      </Stack>
    </Modal>
  );
}

function Meta({ icon, children }: { icon: ReactNode; children: ReactNode }) {
  return (
    <Group gap={6} wrap="nowrap">
      <Text c="dimmed" component="span" style={{ display: 'flex' }}>{icon}</Text>
      <Text size="sm">{children}</Text>
    </Group>
  );
}

/** Resolves ids in history entries to readable names using the company's lookups. */
function useNameResolver(companyId: string | null) {
  const { data: branches } = useBranches(companyId);
  const { data: departments } = useDepartments(companyId);
  const { data: designations } = useLookups(companyId, 'designations');
  const { data: grades } = useLookups(companyId, 'grades');
  const { data: categories } = useLookups(companyId, 'employment-categories');
  const { data: managers } = useQuery({
    queryKey: ['employee-options', companyId],
    queryFn: () => api<Page<EmployeeListItem>>('/employees', { query: { companyId, status: 'ACTIVE', size: 200, sort: 'employeeNumber,asc' } }),
    enabled: !!companyId,
  });
  const lists: Record<string, { id: string; name: string }[] | undefined> = {
    branchId: branches,
    departmentId: departments,
    designationId: designations,
    gradeId: grades,
    categoryId: categories,
    reportingManagerId: managers?.content.map((m) => ({ id: m.id, name: `${m.firstName} ${m.lastName}` })),
  };
  return {
    name: (field: string, value: unknown): string => {
      if (value === null || value === undefined || value === '') return '—';
      if (field === 'status') return humanize(String(value));
      const list = lists[field];
      if (list) return list.find((x) => x.id === value)?.name ?? (field === 'reportingManagerId' ? 'Another employee' : 'Unknown');
      return String(value);
    },
    branch: (id: string | null) => branches?.find((b) => b.id === id)?.name,
    department: (id: string | null) => departments?.find((d) => d.id === id)?.name,
    designation: (id: string | null) => designations?.find((d) => d.id === id)?.name,
  };
}

const FIELD_LABELS: Record<string, string> = {
  branchId: 'Branch',
  departmentId: 'Department',
  designationId: 'Designation',
  gradeId: 'Grade',
  categoryId: 'Employment category',
  reportingManagerId: 'Reporting manager',
  status: 'Status',
};

function Changes({ entry, resolve }: { entry: HistoryEntry; resolve: (field: string, value: unknown) => string }) {
  let parsed: Record<string, unknown>;
  try {
    parsed = JSON.parse(entry.changes) as Record<string, unknown>;
  } catch {
    return <Text size="xs" c="dimmed">{entry.changes}</Text>;
  }
  const rows = Object.entries(parsed)
    .filter(([k, v]) => k !== 'companyId' && FIELD_LABELS[k] && (entry.eventType !== 'JOINED' || v !== null))
    .map(([k, v]) => {
      const isDiff = v !== null && typeof v === 'object' && ('from' in v || 'to' in v);
      const { from, to } = isDiff ? (v as { from: unknown; to: unknown }) : { from: undefined, to: v };
      return (
        <Text key={k} size="sm">
          <Text span c="dimmed" size="sm">{FIELD_LABELS[k]}: </Text>
          {isDiff && (
            <>
              <Text span size="sm" td="line-through" c="dimmed">{resolve(k, from)}</Text>
              {' → '}
            </>
          )}
          <Text span size="sm" fw={500}>{resolve(k, to)}</Text>
        </Text>
      );
    });
  return rows.length ? <Stack gap={2}>{rows}</Stack> : <Text size="sm" c="dimmed">—</Text>;
}

function NewEmployee({ companyId }: { companyId: string | null }) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const client = useQueryClient();
  const back = { to: '/employees', label: 'All employees' };
  if (!companyId) {
    return (
      <>
        <PageHeader title="New employee" back={back} icon={<IconUserPlus size={22} />} />
        <Paper withBorder>
          <EmptyState icon={<IconBuildingSkyscraper size={28} stroke={1.5} />} title={t('app.selectCompanyFirst')} description={t('app.selectCompanyHint')} />
        </Paper>
      </>
    );
  }
  return (
    <>
      <PageHeader
        title="New employee"
        back={back}
        icon={<IconUserPlus size={22} />}
        description="Add a person to the company. The employee number is generated when left blank."
      />
      <EmployeeForm
        companyId={companyId}
        initial={null}
        onCancel={() => navigate('/employees')}
        onSubmit={async (values) => {
          const created = await api<Employee>('/employees', { method: 'POST', body: values });
          showSuccess(`Employee ${created.employeeNumber} created`);
          await client.invalidateQueries({ queryKey: ['employees'] });
          navigate(`/employees/${created.id}`, { replace: true });
        }}
      />
    </>
  );
}

export function EmployeeDetailPage() {
  const { id } = useParams();
  const isNew = id === 'new';
  const { companyId } = useCompany();
  if (isNew) return <NewEmployee companyId={companyId} />;
  return <EmployeeProfile id={id!} />;
}

function EmployeeProfile({ id }: { id: string }) {
  const { can } = useAuth();
  const client = useQueryClient();
  const [statusOpen, setStatusOpen] = useState(false);
  const tabs = ['profile', 'history', 'documents', ...(can('PAYROLL:VIEW') ? ['salary'] : [])];
  const [tab, setTab] = useUrlTab('profile', tabs);
  const { data: employee, refetch, isError } = useQuery({
    queryKey: ['employee', id],
    queryFn: () => api<Employee>(`/employees/${id}`),
  });
  const { data: history = [], isLoading: historyLoading } = useQuery({
    queryKey: ['employee-history', id],
    queryFn: () => api<HistoryEntry[]>(`/employees/${id}/history`),
  });
  const names = useNameResolver(employee?.companyId ?? null);

  if (isError) {
    return (
      <>
        <PageHeader title="Employee" back={{ to: '/employees', label: 'All employees' }} />
        <Paper withBorder>
          <EmptyState title="Employee not available" description="The employee doesn't exist or you don't have access to their record." />
        </Paper>
      </>
    );
  }
  if (!employee) return <PageLoader rows={6} />;

  const fullName = `${employee.firstName} ${employee.lastName}`;
  const designation = names.designation(employee.designationId);
  const department = names.department(employee.departmentId);
  const branch = names.branch(employee.branchId);
  const left = employee.status === 'RESIGNED' || employee.status === 'TERMINATED';

  return (
    <>
      <Anchor component={Link} to="/employees" size="sm" c="dimmed" w="fit-content" display="block" mb={6}>
        <Group gap={4} wrap="nowrap">
          <IconArrowLeft size={14} />
          All employees
        </Group>
      </Anchor>
      <Paper withBorder p="lg" mb="lg">
        <Group justify="space-between" align="flex-start" gap="md" wrap="wrap">
          <Group gap="lg" wrap="nowrap" align="center" style={{ minWidth: 0 }}>
            <Avatar name={fullName} color="initials" size={72} radius="xl" visibleFrom="xs" />
            <Avatar name={fullName} color="initials" size={48} radius="xl" hiddenFrom="xs" />
            <Stack gap={6} style={{ minWidth: 0 }}>
              <Group gap="xs" wrap="wrap">
                <Title order={2}>{fullName}</Title>
                <StatusBadge status={employee.status} colors={STATUS_COLORS} size="lg" />
              </Group>
              <Group gap="xs" wrap="wrap">
                <Badge variant="default" radius="sm" className="tabular">{employee.employeeNumber}</Badge>
                {employee.preferredName && <Text size="sm" c="dimmed">Goes by “{employee.preferredName}”</Text>}
              </Group>
              <Group gap="md" wrap="wrap" mt={2}>
                {designation && <Meta icon={<IconBriefcase size={16} />}>{designation}</Meta>}
                {department && <Meta icon={<IconHierarchy2 size={16} />}>{department}</Meta>}
                {branch && <Meta icon={<IconMapPin size={16} />}>{branch}</Meta>}
                <Meta icon={<IconCalendarEvent size={16} />}>
                  Joined {formatDate(employee.joinDate)}
                  {left && employee.exitDate ? ` · Left ${formatDate(employee.exitDate)}` : ''}
                </Meta>
              </Group>
            </Stack>
          </Group>
          {can('EMPLOYEE:EDIT') && (
            <Button variant="light" leftSection={<IconUserCog size={16} />} onClick={() => setStatusOpen(true)}>Change status</Button>
          )}
        </Group>
      </Paper>

      <Tabs value={tab} onChange={setTab} keepMounted={false}>
        <Tabs.List className="app-tabs-list" mb="lg">
          <Tabs.Tab value="profile" leftSection={<IconUserCircle size={16} />}>Profile</Tabs.Tab>
          <Tabs.Tab value="history" leftSection={<IconHistory size={16} />}>History</Tabs.Tab>
          <Tabs.Tab value="documents" leftSection={<IconFiles size={16} />}>Documents</Tabs.Tab>
          {can('PAYROLL:VIEW') && <Tabs.Tab value="salary" leftSection={<IconReportMoney size={16} />}>Salary</Tabs.Tab>}
        </Tabs.List>
        <Tabs.Panel value="profile">
          <EmployeeForm
            key={employee.version}
            companyId={employee.companyId}
            initial={employee}
            onSubmit={async (values) => {
              await api(`/employees/${employee.id}`, { method: 'PUT', body: { ...values, version: employee.version } });
              showSuccess();
              await refetch();
              await client.invalidateQueries({ queryKey: ['employee-history', id] });
            }}
          />
        </Tabs.Panel>
        <Tabs.Panel value="history">
          <DataTable
            rows={history}
            loading={historyLoading}
            rowKey={(r) => r.id}
            emptyIcon={<IconHistory size={20} />}
            emptyTitle="No history yet"
            emptyDescription="Joining, transfers, promotions and status changes are recorded here."
            minWidth={760}
            columns={[
              { key: 'date', header: 'Effective', width: 130, render: (r) => <Text size="sm" fw={500} style={{ whiteSpace: 'nowrap' }}>{formatDate(r.effectiveDate)}</Text> },
              { key: 'type', header: 'Event', width: 140, render: (r) => <StatusBadge status={r.eventType} colors={EVENT_COLORS} /> },
              { key: 'changes', header: 'Changes', render: (r) => <Changes entry={r} resolve={names.name} /> },
              { key: 'reason', header: 'Reason', render: (r) => r.reason || <Text span c="dimmed" size="sm">—</Text> },
              { key: 'recorded', header: 'Recorded', width: 170, render: (r) => <Text size="xs" c="dimmed" style={{ whiteSpace: 'nowrap' }}>{formatDateTime(r.recordedAt)}</Text> },
            ]}
          />
        </Tabs.Panel>
        <Tabs.Panel value="documents">
          <DocumentsPanel employeeId={employee.id} />
        </Tabs.Panel>
        {can('PAYROLL:VIEW') && (
          <Tabs.Panel value="salary">
            <SalaryStructurePanel employeeId={employee.id} companyId={employee.companyId} />
          </Tabs.Panel>
        )}
      </Tabs>
      {statusOpen && (
        <StatusModal employee={employee} onClose={() => setStatusOpen(false)} onDone={() => {
          setStatusOpen(false);
          void refetch();
          void client.invalidateQueries({ queryKey: ['employee-history', id] });
          void client.invalidateQueries({ queryKey: ['employees'] });
        }} />
      )}
    </>
  );
}
