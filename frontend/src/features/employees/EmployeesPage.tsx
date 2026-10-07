import { Badge, Button, Group, Select, TextInput } from '@mantine/core';
import { useDebouncedValue } from '@mantine/hooks';
import { IconPlus, IconSearch } from '@tabler/icons-react';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router';
import { api, type Page } from '../../api/client';
import { useCompany } from '../../app/CompanyContext';
import { useAuth } from '../../auth/AuthContext';
import { DataTable } from '../../components/DataTable';
import { PageHeader } from '../../components/PageHeader';
import { toOptions, useDepartments } from '../org/useOrgData';
import { STATUS_COLORS, type EmployeeListItem } from './types';

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

  return (
    <>
      <PageHeader
        title={t('nav.employees')}
        actions={
          can('EMPLOYEE:ADD') && (
            <Button leftSection={<IconPlus size={16} />} onClick={() => navigate('/employees/new')}>
              New employee
            </Button>
          )
        }
      />
      <Group mb="sm" gap="sm" wrap="wrap">
        <TextInput
          leftSection={<IconSearch size={14} />}
          placeholder="Number, name, NIC or badge"
          value={q}
          onChange={(e) => {
            setQ(e.currentTarget.value);
            setPage(0);
          }}
          w={260}
        />
        <Select placeholder="Department" clearable searchable data={toOptions(departments)} value={departmentId}
          onChange={(v) => { setDepartmentId(v); setPage(0); }} w={220} />
        <Select placeholder={t('app.status')} clearable data={['ACTIVE', 'RESIGNED', 'TERMINATED', 'INACTIVE']} value={status}
          onChange={(v) => { setStatus(v); setPage(0); }} w={160} />
      </Group>
      <DataTable
        rows={data?.content ?? []}
        loading={isLoading}
        rowKey={(r) => r.id}
        page={page}
        totalPages={data?.totalPages}
        onPageChange={setPage}
        onRowClick={(r) => navigate(`/employees/${r.id}`)}
        columns={[
          { key: 'number', header: 'Number', render: (r) => r.employeeNumber },
          { key: 'name', header: t('app.name'), render: (r) => `${r.firstName} ${r.lastName}` },
          { key: 'dept', header: 'Department', render: (r) => r.departmentName },
          { key: 'desig', header: 'Designation', render: (r) => r.designationName },
          { key: 'join', header: 'Joined', render: (r) => r.joinDate },
          { key: 'status', header: t('app.status'), render: (r) => <Badge color={STATUS_COLORS[r.status]}>{r.status}</Badge> },
        ]}
      />
    </>
  );
}
