import {
  Alert,
  Badge,
  Button,
  Checkbox,
  Code,
  Group,
  MultiSelect,
  NumberInput,
  Paper,
  Select,
  SimpleGrid,
  Stack,
  Tabs,
  Text,
  TextInput,
  ThemeIcon,
  Title,
} from '@mantine/core';
import { useForm } from '@mantine/form';
import { useMediaQuery } from '@mantine/hooks';
import {
  IconBuilding,
  IconBuildingSkyscraper,
  IconCalendarEvent,
  IconCalendarWeek,
  IconCash,
  IconClockHour4,
  IconCornerDownRight,
  IconForms,
  IconHierarchy2,
  IconId,
  IconLock,
  IconMapPin,
  IconSettings,
  IconStairsUp,
  IconBriefcase,
  IconUsersGroup,
  type Icon,
} from '@tabler/icons-react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useRef, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { api } from '../../api/client';
import { useCompany } from '../../app/CompanyContext';
import { useAuth } from '../../auth/AuthContext';
import { CrudPanel } from '../../components/CrudPanel';
import { EmptyState } from '../../components/EmptyState';
import { WEEKDAYS } from '../../components/EntityForm';
import { applyServerErrors, showError, showSuccess } from '../../components/notify';
import { PageHeader } from '../../components/PageHeader';
import { PageLoader } from '../../components/PageLoader';
import { useUrlTab } from '../../components/useUrlTab';
import { BoolIcon } from './BoolIcon';
import type { Branch, CompanyDetail, CompanySettings, Department, Lookup } from './types';
import { departmentTreeRows, toOptions, useBranches, useDepartments } from './useOrgData';
import { CustomFieldsPanel } from './CustomFieldsPanel';
import { HolidaysPanel } from './HolidaysPanel';

const companyFields = [
  { name: 'code', label: 'Code', required: true, placeholder: 'ACME' },
  { name: 'legalName', label: 'Legal name', required: true },
  { name: 'registrationNo', label: 'Registration number' },
  { name: 'address', label: 'Address', type: 'textarea' as const },
  { name: 'phone', label: 'Phone' },
  { name: 'email', label: 'Email' },
  { name: 'currency', label: 'Currency (ISO 4217)', placeholder: 'LKR' },
  { name: 'epfEmployerNo', label: 'EPF employer number' },
  { name: 'etfNo', label: 'ETF number' },
  { name: 'statutoryPack', label: 'Statutory pack', placeholder: 'LK', description: 'Country rules used for EPF/ETF, tax and holidays.' },
  { name: 'timeZone', label: 'Time zone', placeholder: 'Asia/Colombo' },
];

function CompaniesPanel() {
  const { can } = useAuth();
  const { companyId } = useCompany();
  const client = useQueryClient();
  return (
    <CrudPanel<CompanyDetail>
      entityName="company"
      description="Legal entities managed in this system. Pick the working company from the header switcher."
      queryKey={['companies']}
      load={() => api<CompanyDetail[]>('/companies')}
      rowKey={(r) => r.id}
      columns={[
        {
          key: 'code', header: 'Code',
          render: (r) => (
            <Group gap={6} wrap="nowrap">
              <Text size="sm" fw={600}>{r.code}</Text>
              {r.id === companyId && <Badge size="xs" variant="light">Current</Badge>}
            </Group>
          ),
        },
        { key: 'name', header: 'Legal name', render: (r) => r.legalName },
        { key: 'currency', header: 'Currency', render: (r) => r.currency },
        { key: 'tz', header: 'Time zone', render: (r) => <Text size="sm" c="dimmed">{r.timeZone}</Text> },
        { key: 'pack', header: 'Statutory pack', render: (r) => <Badge variant="light" color="gray">{r.statutoryPack}</Badge> },
      ]}
      fields={companyFields}
      emptyValues={{ code: '', legalName: '', currency: 'LKR', statutoryPack: 'LK', timeZone: 'Asia/Colombo' }}
      toValues={(r) => ({ ...r })}
      create={can('ORG:CONFIGURE') ? (v) => api('/companies', { method: 'POST', body: v }).then(() => client.invalidateQueries({ queryKey: ['companies'] })) : undefined}
      update={can('ORG:EDIT') ? (r, v) => api(`/companies/${r.id}`, { method: 'PUT', body: { ...v, version: r.version } }) : undefined}
    />
  );
}

/** Expands `{COMPANY}-{YYYY}-{SEQ:4}` into a sample employee number. */
function previewEmployeeNumber(format: string, companyCode: string, branchCode: string): string {
  return format
    .replaceAll('{COMPANY}', companyCode)
    .replaceAll('{BRANCH}', branchCode)
    .replaceAll('{YYYY}', String(new Date().getFullYear()))
    .replace(/\{SEQ:(\d+)\}/g, (_, n: string) => '1'.padStart(Math.min(Number(n), 12), '0'));
}

function SettingsSection({ icon: SectionIcon, title, description, children }: { icon: Icon; title: string; description: string; children: ReactNode }) {
  return (
    <Paper withBorder p="lg">
      <Group gap="sm" mb="md" wrap="nowrap" align="flex-start">
        <ThemeIcon variant="light" size={34} radius="md">
          <SectionIcon size={18} />
        </ThemeIcon>
        <div>
          <Title order={4}>{title}</Title>
          <Text size="sm" c="dimmed">{description}</Text>
        </div>
      </Group>
      {children}
    </Paper>
  );
}

function SettingsForm({ companyId, data, disabled }: { companyId: string; data: CompanySettings; disabled: boolean }) {
  const client = useQueryClient();
  const { company } = useCompany();
  const { data: branches } = useBranches(companyId);
  const form = useForm<CompanySettings>({
    initialValues: { ...data },
    validate: {
      employeeNumberFormat: (v) => (!v?.trim() ? 'Required' : (v.match(/\{SEQ:\d+\}/g) ?? []).length !== 1 ? 'Must contain exactly one {SEQ:n} token' : null),
      payrollStartDay: (v) => (v === null || v === undefined || (v as unknown) === '' ? 'Required' : v < 1 || v > 28 ? 'Between 1 and 28' : null),
      noPayDivisor: (v) => (v === null || v === undefined || (v as unknown) === '' ? 'Required' : v < 1 ? 'Must be at least 1' : null),
      punchWindowBeforeMinutes: (v) => (v === null || v === undefined || (v as unknown) === '' ? 'Required' : null),
      punchWindowAfterMinutes: (v) => (v === null || v === undefined || (v as unknown) === '' ? 'Required' : null),
      weekStartDay: (v) => (!v ? 'Required' : null),
    },
  });

  const submit = form.onSubmit(async (values) => {
    if (disabled) return;
    try {
      await api(`/companies/${companyId}/settings`, { method: 'PUT', body: { ...values, version: data.version } });
      showSuccess();
      await client.invalidateQueries({ queryKey: ['company-settings', companyId] });
    } catch (e) {
      if (!applyServerErrors(e, form.setErrors)) showError(e);
    }
  });

  const values = form.getValues();
  const preview = previewEmployeeNumber(values.employeeNumberFormat ?? '', company?.code ?? 'ACME', branches?.[0]?.code ?? 'CMB');

  return (
    <form onSubmit={submit} noValidate>
      <Stack gap="md" maw={880}>
        {disabled && (
          <Alert color="gray" variant="light" icon={<IconLock size={18} />} title="Read only">
            You can view these settings, but changing them requires the ORG:CONFIGURE permission.
          </Alert>
        )}
        <SettingsSection icon={IconCalendarWeek} title="Working week" description="Days that are normally off for everyone in this company.">
          <MultiSelect
            label="Default weekly-off days"
            data={WEEKDAYS}
            disabled={disabled}
            placeholder="Select days"
            key={form.key('weeklyOffDays')}
            {...form.getInputProps('weeklyOffDays')}
          />
        </SettingsSection>
        <SettingsSection icon={IconId} title="Employee numbering" description="How new employee numbers are generated.">
          <TextInput
            label="Employee number format"
            withAsterisk
            disabled={disabled}
            description={<>Tokens: <Code>{'{COMPANY}'}</Code> <Code>{'{BRANCH}'}</Code> <Code>{'{YYYY}'}</Code> <Code>{'{SEQ:n}'}</Code>. Exactly one <Code>{'{SEQ:n}'}</Code> is required.</>}
            key={form.key('employeeNumberFormat')}
            {...form.getInputProps('employeeNumberFormat')}
          />
          <Group gap="xs" mt="sm">
            <Text size="sm" c="dimmed">Next number looks like</Text>
            <Badge variant="light" size="lg" radius="sm" tt="none" ff="monospace">{preview || '—'}</Badge>
          </Group>
        </SettingsSection>
        <SettingsSection icon={IconCash} title="Payroll" description="Pay period boundaries and no-pay deductions.">
          <SimpleGrid cols={{ base: 1, sm: 2 }}>
            <NumberInput
              label="Payroll period start day (1-28)"
              description="Day of the month each payroll period starts."
              withAsterisk min={1} max={28} disabled={disabled}
              key={form.key('payrollStartDay')}
              {...form.getInputProps('payrollStartDay')}
            />
            <NumberInput
              label="No-pay divisor (days)"
              description="Monthly salary is divided by this for a day's no-pay."
              withAsterisk min={1} disabled={disabled}
              key={form.key('noPayDivisor')}
              {...form.getInputProps('noPayDivisor')}
            />
          </SimpleGrid>
          <Checkbox
            mt="md"
            label="Treat absent days as no-pay"
            description="Unapproved absences reduce pay automatically."
            disabled={disabled}
            key={form.key('absentAsNoPay')}
            {...form.getInputProps('absentAsNoPay', { type: 'checkbox' })}
          />
        </SettingsSection>
        <SettingsSection icon={IconClockHour4} title="Attendance punch window" description="How far from a shift a punch is still matched to it.">
          <SimpleGrid cols={{ base: 1, sm: 2 }}>
            <NumberInput
              label="Punch window before shift (minutes)"
              withAsterisk min={0} max={720} disabled={disabled}
              key={form.key('punchWindowBeforeMinutes')}
              {...form.getInputProps('punchWindowBeforeMinutes')}
            />
            <NumberInput
              label="Punch window after shift (minutes)"
              withAsterisk min={0} max={720} disabled={disabled}
              key={form.key('punchWindowAfterMinutes')}
              {...form.getInputProps('punchWindowAfterMinutes')}
            />
          </SimpleGrid>
        </SettingsSection>
        <SettingsSection icon={IconCalendarEvent} title="Rostering" description="Layout of the weekly roster grid.">
          <Select
            label="Roster week starts on"
            data={WEEKDAYS}
            withAsterisk
            disabled={disabled}
            maw={{ base: '100%', sm: 280 }}
            key={form.key('weekStartDay')}
            {...form.getInputProps('weekStartDay')}
          />
        </SettingsSection>
        {!disabled && (
          <Group justify="flex-end">
            <Button variant="default" onClick={() => form.reset()} disabled={!form.isDirty()}>
              Discard changes
            </Button>
            <Button type="submit" loading={form.submitting}>Save</Button>
          </Group>
        )}
      </Stack>
    </form>
  );
}

function SettingsPanel({ companyId }: { companyId: string }) {
  const { can } = useAuth();
  const { data, isLoading } = useQuery({
    queryKey: ['company-settings', companyId],
    queryFn: () => api<CompanySettings>(`/companies/${companyId}/settings`),
  });
  if (isLoading || !data) return <PageLoader />;
  return <SettingsForm key={`${companyId}-${data.version}`} companyId={companyId} data={data} disabled={!can('ORG:CONFIGURE')} />;
}

function BranchesPanel({ companyId }: { companyId: string }) {
  const { can } = useAuth();
  return (
    <CrudPanel<Branch>
      entityName="branch"
      description="Physical locations or sites of the selected company."
      queryKey={['branches', companyId]}
      load={() => api<Branch[]>(`/companies/${companyId}/branches`)}
      rowKey={(r) => r.id}
      columns={[
        { key: 'code', header: 'Code', render: (r) => <Text size="sm" fw={600}>{r.code}</Text> },
        { key: 'name', header: 'Name', render: (r) => r.name },
        { key: 'address', header: 'Address', render: (r) => <Text size="sm" c="dimmed" lineClamp={1} maw={320}>{r.address ?? '—'}</Text> },
        { key: 'phone', header: 'Phone', render: (r) => r.phone ?? '—' },
      ]}
      fields={[
        { name: 'code', label: 'Code', required: true, placeholder: 'CMB' },
        { name: 'name', label: 'Name', required: true, placeholder: 'Colombo' },
        { name: 'address', label: 'Address', type: 'textarea' },
        { name: 'phone', label: 'Phone' },
      ]}
      emptyValues={{ code: '', name: '', address: '', phone: '' }}
      toValues={(r) => ({ ...r })}
      create={can('ORG:ADD') ? (v) => api(`/companies/${companyId}/branches`, { method: 'POST', body: v }) : undefined}
      update={can('ORG:EDIT') ? (r, v) => api(`/branches/${r.id}`, { method: 'PUT', body: { ...v, version: r.version } }) : undefined}
      remove={can('ORG:DELETE') ? (r) => api(`/branches/${r.id}`, { method: 'DELETE' }) : undefined}
    />
  );
}

type DepartmentRow = Department & { depth: number };

function DepartmentsPanel({ companyId }: { companyId: string }) {
  const { can } = useAuth();
  const { data: departments = [] } = useDepartments(companyId);
  const { data: branches } = useBranches(companyId);
  const rows = departmentTreeRows(departments);
  const byId = new Map(departments.map((d) => [d.id, d]));
  const branchCode = new Map((branches ?? []).map((b) => [b.id, b.code]));
  return (
    <CrudPanel<DepartmentRow>
      entityName="department"
      description="Department hierarchy; sub-departments are indented under their parent."
      queryKey={['departments', companyId, 'tree']}
      load={async () => departmentTreeRows(await api<Department[]>(`/companies/${companyId}/departments`))}
      rowKey={(r) => r.id}
      columns={[
        {
          key: 'name', header: 'Department',
          render: (r) => (
            <Group gap={6} wrap="nowrap" pl={Math.max(0, r.depth - 1) * 22 + (r.depth > 0 ? 4 : 0)}>
              {r.depth > 0 ? (
                <IconCornerDownRight size={16} color="var(--mantine-color-dimmed)" aria-hidden />
              ) : (
                <ThemeIcon size={22} variant="light" radius="sm" aria-hidden>
                  <IconHierarchy2 size={14} />
                </ThemeIcon>
              )}
              <div style={{ minWidth: 0 }}>
                <Text size="sm" fw={r.depth === 0 ? 600 : 500}>{r.name}</Text>
                <Text size="xs" c="dimmed">{r.code}</Text>
              </div>
            </Group>
          ),
        },
        {
          key: 'parent', header: 'Parent',
          render: (r) => {
            const parent = r.parentId ? byId.get(r.parentId) : undefined;
            return <Text size="sm" c="dimmed">{parent ? parent.name : '—'}</Text>;
          },
        },
        { key: 'branch', header: 'Branch', render: (r) => (r.branchId && branchCode.get(r.branchId) ? <Badge variant="outline" color="gray" size="sm">{branchCode.get(r.branchId)}</Badge> : <Text size="sm" c="dimmed">—</Text>) },
        { key: 'cc', header: 'Cost center', render: (r) => r.costCenterCode ?? '—' },
      ]}
      fields={(row) => [
        { name: 'code', label: 'Code', required: true },
        { name: 'name', label: 'Name', required: true },
        {
          name: 'parentId', label: 'Parent department', type: 'select', placeholder: 'None (top level)',
          options: rows.filter((d) => d.id !== row?.id).map((d) => ({ value: d.id, label: `${'— '.repeat(d.depth)}${d.code} — ${d.name}` })),
        },
        { name: 'branchId', label: 'Branch', type: 'select', options: toOptions(branches), placeholder: 'Any branch' },
        { name: 'costCenterCode', label: 'Cost center code' },
      ]}
      emptyValues={{ code: '', name: '', parentId: null, branchId: null, costCenterCode: '' }}
      toValues={(r) => ({ ...r })}
      create={can('ORG:ADD') ? (v) => api(`/companies/${companyId}/departments`, { method: 'POST', body: v }) : undefined}
      update={can('ORG:EDIT') ? (r, v) => api(`/departments/${r.id}`, { method: 'PUT', body: { ...v, managerEmployeeId: r.managerEmployeeId, version: r.version } }) : undefined}
      remove={can('ORG:DELETE') ? (r) => api(`/departments/${r.id}`, { method: 'DELETE' }) : undefined}
    />
  );
}

const LOOKUP_DESCRIPTIONS = {
  designations: 'Job titles assigned to employees.',
  grades: 'Pay or seniority grades; a lower rank is more junior.',
  'employment-categories': 'Employment types such as permanent, contract or casual, and whether they earn overtime.',
};

function LookupPanel({ companyId, kind, entityName }: { companyId: string; kind: 'designations' | 'grades' | 'employment-categories'; entityName: string }) {
  const { can } = useAuth();
  const extra =
    kind === 'grades'
      ? [{ name: 'rank', label: 'Rank', type: 'number' as const, description: 'Used to order grades.' }]
      : kind === 'employment-categories'
        ? [{ name: 'otEligible', label: 'Eligible for overtime', type: 'checkbox' as const }]
        : [];
  return (
    <CrudPanel<Lookup>
      entityName={entityName}
      description={LOOKUP_DESCRIPTIONS[kind]}
      queryKey={[kind, companyId]}
      load={() => api<Lookup[]>(`/companies/${companyId}/${kind}`)}
      rowKey={(r) => r.id}
      columns={[
        { key: 'code', header: 'Code', render: (r) => <Text size="sm" fw={600}>{r.code}</Text> },
        { key: 'name', header: 'Name', render: (r) => r.name },
        ...(kind === 'grades' ? [{ key: 'rank', header: 'Rank', align: 'right' as const, render: (r: Lookup) => <span className="tabular">{r.rank ?? '—'}</span> }] : []),
        ...(kind === 'employment-categories'
          ? [{ key: 'ot', header: 'OT eligible', align: 'center' as const, render: (r: Lookup) => <BoolIcon value={r.otEligible} yes="Eligible for overtime" no="Not eligible for overtime" /> }]
          : []),
      ]}
      fields={[{ name: 'code', label: 'Code', required: true }, { name: 'name', label: 'Name', required: true }, ...extra]}
      emptyValues={{ code: '', name: '', rank: 0, otEligible: true }}
      toValues={(r) => ({ ...r })}
      create={can('ORG:ADD') ? (v) => api(`/companies/${companyId}/${kind}`, { method: 'POST', body: v }) : undefined}
      update={can('ORG:EDIT') ? (r, v) => api(`/${kind}/${r.id}`, { method: 'PUT', body: { ...v, version: r.version } }) : undefined}
      remove={can('ORG:DELETE') ? (r) => api(`/${kind}/${r.id}`, { method: 'DELETE' }) : undefined}
    />
  );
}

interface TabDef {
  value: string;
  label: string;
  icon: Icon;
  show: boolean;
}

export function OrganizationPage() {
  const { t } = useTranslation();
  const { companyId } = useCompany();
  const { can } = useAuth();
  const tabs: TabDef[] = [
    { value: 'companies', label: 'Companies', icon: IconBuildingSkyscraper, show: true },
    { value: 'settings', label: 'Settings', icon: IconSettings, show: !!companyId },
    { value: 'branches', label: 'Branches', icon: IconMapPin, show: !!companyId },
    { value: 'departments', label: 'Departments', icon: IconHierarchy2, show: !!companyId },
    { value: 'designations', label: 'Designations', icon: IconBriefcase, show: !!companyId },
    { value: 'grades', label: 'Grades', icon: IconStairsUp, show: !!companyId },
    { value: 'categories', label: 'Employment categories', icon: IconUsersGroup, show: !!companyId },
    { value: 'holidays', label: 'Holidays', icon: IconCalendarEvent, show: !!companyId },
    { value: 'custom-fields', label: 'Custom fields', icon: IconForms, show: !!companyId && can('EMPLOYEE:VIEW') },
  ].filter((tab) => tab.show);
  const [tab, setTab] = useUrlTab('companies', tabs.map((x) => x.value));
  // Nine sections: a side list on wide screens, a scrollable strip on narrow ones.
  const wide = useMediaQuery('(min-width: 62em)');
  const listRef = useRef<HTMLDivElement>(null);
  useEffect(() => {
    listRef.current?.querySelector('[data-active]')?.scrollIntoView?.({ block: 'nearest', inline: 'nearest' });
  }, [tab, wide]);

  return (
    <>
      <PageHeader title={t('nav.organization')} description={t('navHint.organization')} icon={<IconBuildingSkyscraper size={22} />} />
      <Tabs value={tab} onChange={setTab} keepMounted={false} orientation={wide ? 'vertical' : 'horizontal'} variant={wide ? 'pills' : 'default'}
        styles={wide ? { tab: { justifyContent: 'flex-start' }, tabLabel: { textAlign: 'left', flex: 1 } } : undefined}
      >
        <Tabs.List
          ref={listRef}
          className={wide ? undefined : 'app-tabs-list'}
          mb={wide ? undefined : 'lg'}
          w={wide ? 220 : undefined}
          mr={wide ? 'lg' : undefined}
          style={wide ? { alignSelf: 'flex-start', position: 'sticky', top: 76, gap: 2 } : undefined}
        >
          {tabs.map(({ value, label, icon: TabIcon }) => (
            <Tabs.Tab key={value} value={value} leftSection={<TabIcon size={16} />}>
              {label}
            </Tabs.Tab>
          ))}
        </Tabs.List>
        <div style={{ flex: 1, minWidth: 0 }}>
          <Tabs.Panel value="companies">
            <CompaniesPanel />
            {!companyId && (
              <Paper withBorder mt="lg">
                <EmptyState
                  icon={<IconBuilding size={28} stroke={1.5} />}
                  title="Select a company first"
                  description="Branches, departments, holidays and other setup become available once a company is selected in the header."
                />
              </Paper>
            )}
          </Tabs.Panel>
          {companyId && (
            <>
              <Tabs.Panel value="settings"><SettingsPanel companyId={companyId} /></Tabs.Panel>
              <Tabs.Panel value="branches"><BranchesPanel companyId={companyId} /></Tabs.Panel>
              <Tabs.Panel value="departments"><DepartmentsPanel companyId={companyId} /></Tabs.Panel>
              <Tabs.Panel value="designations"><LookupPanel companyId={companyId} kind="designations" entityName="designation" /></Tabs.Panel>
              <Tabs.Panel value="grades"><LookupPanel companyId={companyId} kind="grades" entityName="grade" /></Tabs.Panel>
              <Tabs.Panel value="categories"><LookupPanel companyId={companyId} kind="employment-categories" entityName="category" /></Tabs.Panel>
              <Tabs.Panel value="holidays"><HolidaysPanel companyId={companyId} /></Tabs.Panel>
              <Tabs.Panel value="custom-fields"><CustomFieldsPanel companyId={companyId} /></Tabs.Panel>
            </>
          )}
        </div>
      </Tabs>
    </>
  );
}
