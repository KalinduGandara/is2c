import { Alert, Badge, Paper, Tabs, Text } from '@mantine/core';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { api } from '../../api/client';
import { useCompany } from '../../app/CompanyContext';
import { useAuth } from '../../auth/AuthContext';
import { CrudPanel } from '../../components/CrudPanel';
import { EntityForm, WEEKDAYS } from '../../components/EntityForm';
import { showSuccess } from '../../components/notify';
import { PageHeader } from '../../components/PageHeader';
import type { Branch, CompanyDetail, CompanySettings, Department, Lookup } from './types';
import { departmentTreeRows, toOptions, useBranches, useDepartments } from './useOrgData';
import { CustomFieldsPanel } from './CustomFieldsPanel';
import { HolidaysPanel } from './HolidaysPanel';

const companyFields = [
  { name: 'code', label: 'Code', required: true },
  { name: 'legalName', label: 'Legal name', required: true },
  { name: 'registrationNo', label: 'Registration number' },
  { name: 'address', label: 'Address', type: 'textarea' as const },
  { name: 'phone', label: 'Phone' },
  { name: 'email', label: 'Email' },
  { name: 'currency', label: 'Currency (ISO 4217)', placeholder: 'LKR' },
  { name: 'epfEmployerNo', label: 'EPF employer number' },
  { name: 'etfNo', label: 'ETF number' },
  { name: 'statutoryPack', label: 'Statutory pack', placeholder: 'LK' },
  { name: 'timeZone', label: 'Time zone', placeholder: 'Asia/Colombo' },
];

function CompaniesPanel() {
  const { can } = useAuth();
  const client = useQueryClient();
  return (
    <CrudPanel<CompanyDetail>
      entityName="company"
      queryKey={['companies']}
      load={() => api<CompanyDetail[]>('/companies')}
      rowKey={(r) => r.id}
      columns={[
        { key: 'code', header: 'Code', render: (r) => r.code },
        { key: 'name', header: 'Legal name', render: (r) => r.legalName },
        { key: 'currency', header: 'Currency', render: (r) => r.currency },
        { key: 'pack', header: 'Statutory pack', render: (r) => <Badge variant="light">{r.statutoryPack}</Badge> },
      ]}
      fields={companyFields}
      emptyValues={{ code: '', legalName: '', currency: 'LKR', statutoryPack: 'LK', timeZone: 'Asia/Colombo' }}
      toValues={(r) => ({ ...r })}
      create={can('ORG:CONFIGURE') ? (v) => api('/companies', { method: 'POST', body: v }).then(() => client.invalidateQueries({ queryKey: ['companies'] })) : undefined}
      update={can('ORG:EDIT') ? (r, v) => api(`/companies/${r.id}`, { method: 'PUT', body: { ...v, version: r.version } }) : undefined}
    />
  );
}

function SettingsPanel({ companyId }: { companyId: string }) {
  const { can } = useAuth();
  const client = useQueryClient();
  const { data } = useQuery({
    queryKey: ['company-settings', companyId],
    queryFn: () => api<CompanySettings>(`/companies/${companyId}/settings`),
  });
  if (!data) return null;
  const disabled = !can('ORG:CONFIGURE');
  return (
    <Paper withBorder p="md" maw={560}>
      {disabled && <Alert mb="sm">Read only — requires ORG:CONFIGURE.</Alert>}
      <EntityForm
        key={data.version}
        fields={[
          { name: 'weeklyOffDays', label: 'Default weekly-off days', type: 'multiselect', options: WEEKDAYS },
          {
            name: 'employeeNumberFormat', label: 'Employee number format', required: true,
            description: 'Tokens: {COMPANY} {BRANCH} {YYYY} {SEQ:n}. Exactly one {SEQ:n} is required.',
          },
          { name: 'payrollStartDay', label: 'Payroll period start day (1-28)', type: 'number', min: 1, max: 28, required: true },
          { name: 'absentAsNoPay', label: 'Treat absent days as no-pay', type: 'checkbox' },
          { name: 'noPayDivisor', label: 'No-pay divisor (days)', type: 'number', min: 1, required: true },
          { name: 'punchWindowBeforeMinutes', label: 'Punch window before shift (minutes)', type: 'number', min: 0, max: 720, required: true },
          { name: 'punchWindowAfterMinutes', label: 'Punch window after shift (minutes)', type: 'number', min: 0, max: 720, required: true },
          { name: 'weekStartDay', label: 'Roster week starts on', type: 'select', options: WEEKDAYS, required: true },
        ]}
        initialValues={{ ...data }}
        onSubmit={async (values) => {
          if (disabled) return;
          await api(`/companies/${companyId}/settings`, { method: 'PUT', body: { ...values, version: data.version } });
          showSuccess();
          await client.invalidateQueries({ queryKey: ['company-settings', companyId] });
        }}
      />
    </Paper>
  );
}

function BranchesPanel({ companyId }: { companyId: string }) {
  const { can } = useAuth();
  return (
    <CrudPanel<Branch>
      entityName="branch"
      queryKey={['branches', companyId]}
      load={() => api<Branch[]>(`/companies/${companyId}/branches`)}
      rowKey={(r) => r.id}
      columns={[
        { key: 'code', header: 'Code', render: (r) => r.code },
        { key: 'name', header: 'Name', render: (r) => r.name },
        { key: 'phone', header: 'Phone', render: (r) => r.phone },
      ]}
      fields={[
        { name: 'code', label: 'Code', required: true },
        { name: 'name', label: 'Name', required: true },
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
  return (
    <CrudPanel<DepartmentRow>
      entityName="department"
      queryKey={['departments', companyId, 'tree']}
      load={async () => departmentTreeRows(await api<Department[]>(`/companies/${companyId}/departments`))}
      rowKey={(r) => r.id}
      columns={[
        { key: 'code', header: 'Code', render: (r) => <Text pl={r.depth * 16}>{r.code}</Text> },
        { key: 'name', header: 'Name', render: (r) => r.name },
        { key: 'cc', header: 'Cost center', render: (r) => r.costCenterCode },
      ]}
      fields={(row) => [
        { name: 'code', label: 'Code', required: true },
        { name: 'name', label: 'Name', required: true },
        {
          name: 'parentId', label: 'Parent department', type: 'select',
          options: rows.filter((d) => d.id !== row?.id).map((d) => ({ value: d.id, label: `${d.code} — ${d.name}` })),
        },
        { name: 'branchId', label: 'Branch', type: 'select', options: toOptions(branches) },
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

function LookupPanel({ companyId, kind, entityName }: { companyId: string; kind: 'designations' | 'grades' | 'employment-categories'; entityName: string }) {
  const { can } = useAuth();
  const extra =
    kind === 'grades'
      ? [{ name: 'rank', label: 'Rank', type: 'number' as const }]
      : kind === 'employment-categories'
        ? [{ name: 'otEligible', label: 'Eligible for overtime', type: 'checkbox' as const }]
        : [];
  return (
    <CrudPanel<Lookup>
      entityName={entityName}
      queryKey={[kind, companyId]}
      load={() => api<Lookup[]>(`/companies/${companyId}/${kind}`)}
      rowKey={(r) => r.id}
      columns={[
        { key: 'code', header: 'Code', render: (r) => r.code },
        { key: 'name', header: 'Name', render: (r) => r.name },
        ...(kind === 'grades' ? [{ key: 'rank', header: 'Rank', render: (r: Lookup) => r.rank }] : []),
        ...(kind === 'employment-categories'
          ? [{ key: 'ot', header: 'OT eligible', render: (r: Lookup) => (r.otEligible ? 'Yes' : 'No') }]
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

export function OrganizationPage() {
  const { t } = useTranslation();
  const { companyId } = useCompany();
  const { can } = useAuth();
  return (
    <>
      <PageHeader title={t('nav.organization')} />
      <Tabs defaultValue="companies" keepMounted={false}>
        <Tabs.List mb="md" style={{ overflowX: 'auto', flexWrap: 'nowrap' }}>
          <Tabs.Tab value="companies">Companies</Tabs.Tab>
          {companyId && (
            <>
              <Tabs.Tab value="settings">Settings</Tabs.Tab>
              <Tabs.Tab value="branches">Branches</Tabs.Tab>
              <Tabs.Tab value="departments">Departments</Tabs.Tab>
              <Tabs.Tab value="designations">Designations</Tabs.Tab>
              <Tabs.Tab value="grades">Grades</Tabs.Tab>
              <Tabs.Tab value="categories">Employment categories</Tabs.Tab>
              <Tabs.Tab value="holidays">Holidays</Tabs.Tab>
              {can('EMPLOYEE:VIEW') && <Tabs.Tab value="custom-fields">Custom fields</Tabs.Tab>}
            </>
          )}
        </Tabs.List>
        <Tabs.Panel value="companies">
          <CompaniesPanel />
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
      </Tabs>
    </>
  );
}
