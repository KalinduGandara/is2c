import { Avatar, Badge, Button, Group, Paper, Select, Stack, Text, TextInput } from '@mantine/core';
import { useDebouncedValue } from '@mantine/hooks';
import { IconBuildingSkyscraper, IconFilterOff, IconPlus, IconSearch, IconUserPlus, IconUsers } from '@tabler/icons-react';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router';
import { api, type Page } from '../../api/client';
import { useCompany } from '../../app/CompanyContext';
import { useAuth } from '../../auth/AuthContext';
import { DataTable } from '../../components/DataTable';
import { EmptyState } from '../../components/EmptyState';
import { formatDate, humanize } from '../../components/format';
import { PageHeader } from '../../components/PageHeader';
import { StatusBadge } from '../../components/StatusBadge';
import { toOptions, useDepartments } from '../org/useOrgData';
import { EMPLOYEE_STATUSES, STATUS_COLORS, type EmployeeListItem } from './types';

/** Name cell: avatar, full name and a secondary line (preferred name / designation). */
function EmployeeName({ row }: { row: EmployeeListItem }) {
  const name = `${row.firstName} ${row.lastName}`;
  const secondary = [row.preferredName ? `“${row.preferredName}”` : null, row.designationName].filter(Boolean).join(' · ');
  return (
    <Group gap="sm" wrap="nowrap">
      <Avatar name={name} color="initials" size="md" radius="xl" aria-hidden />
      <div style={{ minWidth: 0 }}>
        <Text size="sm" fw={600} truncate>{name}</Text>
        {secondary && <Text size="xs" c="dimmed" truncate>{secondary}</Text>}
      </div>
    </Group>
  );
}

export function EmployeesPage() {
  const { t } = useTranslation();
  const { can } = useAuth();
  const { companyId } = useCompany();
  const navigate = useNavigate();
  const [q, setQ] = useState('');
  const [debouncedQ] = useDebouncedValue(q, 300);
  const [departmentId, setDepartmentId] = useState<string | null>(null);
  const [status, setStatus] = useState<string | null>('ACTIVE');
  const [page, setPage] = useState(0);
  const { data: departments } = useDepartments(companyId);
  const { data, isLoading } = useQuery({
    queryKey: ['employees', companyId, debouncedQ, departmentId, status, page],
    queryFn: () =>
      api<Page<EmployeeListItem>>('/employees', {
        query: { companyId, q: debouncedQ, departmentId, status, page, size: 25, sort: 'employeeNumber,asc' },
      }),
    enabled: !!companyId,
    placeholderData: keepPreviousData,
  });

  const filtered = !!debouncedQ || !!departmentId || (status !== null && status !== 'ACTIVE');
  const clearFilters = () => {
    setQ('');
    setDepartmentId(null);
    setStatus('ACTIVE');
    setPage(0);
  };

  const header = (
    <PageHeader
      title={t('nav.employees')}
      icon={<IconUsers size={22} />}
      description={t('navHint.employees')}
      badge={data && companyId ? <Badge variant="light" color="gray" size="lg">{data.totalElements}</Badge> : undefined}
      actions={
        can('EMPLOYEE:ADD') && companyId && (
          <Button leftSection={<IconPlus size={16} />} onClick={() => navigate('/employees/new')}>
            New employee
          </Button>
        )
      }
    />
  );

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

  return (
    <>
      {header}
      <Stack gap="md">
        <Paper withBorder p="sm">
          <Group gap="sm" wrap="wrap">
            <TextInput
              aria-label="Search employees"
              leftSection={<IconSearch size={16} />}
              placeholder="Number, name, NIC or badge"
              value={q}
              onChange={(e) => {
                setQ(e.currentTarget.value);
                setPage(0);
              }}
              w={{ base: '100%', sm: 280 }}
            />
            <Select aria-label="Filter by department" placeholder="All departments" clearable searchable data={toOptions(departments)} value={departmentId}
              onChange={(v) => { setDepartmentId(v); setPage(0); }} w={{ base: '100%', sm: 220 }} />
            <Select aria-label="Filter by status" placeholder="All statuses" clearable
              data={EMPLOYEE_STATUSES.map((s) => ({ value: s, label: humanize(s) }))} value={status}
              onChange={(v) => { setStatus(v); setPage(0); }} w={{ base: '100%', sm: 180 }} />
            {filtered && (
              <Button variant="subtle" color="gray" leftSection={<IconFilterOff size={16} />} onClick={clearFilters}>
                Clear filters
              </Button>
            )}
          </Group>
        </Paper>
        <DataTable
          rows={data?.content ?? []}
          loading={isLoading}
          rowKey={(r) => r.id}
          page={page}
          totalPages={data?.totalPages}
          totalElements={data?.totalElements}
          onPageChange={setPage}
          onRowClick={(r) => navigate(`/employees/${r.id}`)}
          rowLabel={(r) => `${r.firstName} ${r.lastName} (${r.employeeNumber})`}
          minWidth={720}
          emptyIcon={filtered ? <IconSearch size={20} /> : <IconUsers size={20} />}
          emptyTitle={filtered ? 'No matching employees' : status ? `No ${humanize(status).toLowerCase()} employees yet` : 'No employees yet'}
          emptyDescription={filtered ? 'Try a different search term or clear the filters.' : 'Employees you add to this company will appear here.'}
          emptyAction={
            filtered ? (
              <Button variant="default" size="xs" leftSection={<IconFilterOff size={14} />} onClick={clearFilters}>Clear filters</Button>
            ) : can('EMPLOYEE:ADD') ? (
              <Button size="xs" leftSection={<IconUserPlus size={14} />} onClick={() => navigate('/employees/new')}>Add employee</Button>
            ) : undefined
          }
          columns={[
            { key: 'name', header: t('app.name'), render: (r) => <EmployeeName row={r} /> },
            { key: 'number', header: 'Number', render: (r) => <Text size="sm" c="dimmed" className="tabular">{r.employeeNumber}</Text> },
            { key: 'dept', header: 'Department', render: (r) => r.departmentName ?? '—' },
            { key: 'branch', header: 'Branch', render: (r) => r.branchName ?? '—' },
            { key: 'join', header: 'Joined', render: (r) => formatDate(r.joinDate) },
            { key: 'status', header: t('app.status'), render: (r) => <StatusBadge status={r.status} colors={STATUS_COLORS} /> },
          ]}
        />
      </Stack>
    </>
  );
}
