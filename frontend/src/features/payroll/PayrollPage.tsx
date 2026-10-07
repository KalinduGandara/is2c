import { Badge, Button, Group, Modal, NumberInput, Stack, Tabs, Text } from '@mantine/core';
import { IconPlus } from '@tabler/icons-react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router';
import { api } from '../../api/client';
import { useCompany } from '../../app/CompanyContext';
import { useAuth } from '../../auth/AuthContext';
import { DataTable } from '../../components/DataTable';
import { showError } from '../../components/notify';
import { PageHeader } from '../../components/PageHeader';
import { ComponentsPanel } from './ComponentsPanel';
import { SlabTablesPanel } from './SlabTablesPanel';
import { StatutoryPanel } from './StatutoryPanel';
import { periodLabel, PERIOD_STATUS_COLOR, type PayrollPeriod } from './types';

function PeriodsPanel({ companyId }: { companyId: string }) {
  const { can } = useAuth();
  const navigate = useNavigate();
  const client = useQueryClient();
  const now = new Date();
  const [opening, setOpening] = useState(false);
  const [year, setYear] = useState<number | string>(now.getFullYear());
  const [month, setMonth] = useState<number | string>(now.getMonth() + 1);
  const { data = [], isLoading } = useQuery({
    queryKey: ['payroll-periods', companyId],
    queryFn: () => api<PayrollPeriod[]>('/payroll/periods', { query: { companyId } }),
  });
  const open = async () => {
    try {
      const p = await api<PayrollPeriod>('/payroll/periods', { method: 'POST', body: { companyId, year: Number(year), month: Number(month) } });
      setOpening(false);
      await client.invalidateQueries({ queryKey: ['payroll-periods'] });
      navigate(`/payroll/periods/${p.id}`);
    } catch (e) {
      showError(e);
    }
  };
  return (
    <>
      {can('PAYROLL:ADD') && (
        <Group justify="flex-end" mb="sm"><Button leftSection={<IconPlus size={16} />} onClick={() => setOpening(true)}>Open period</Button></Group>
      )}
      <DataTable
        rows={data}
        loading={isLoading}
        rowKey={(r) => r.id}
        onRowClick={(r) => navigate(`/payroll/periods/${r.id}`)}
        columns={[
          { key: 'period', header: 'Period', render: (r) => periodLabel(r) },
          { key: 'dates', header: 'Dates', render: (r) => `${r.startDate} → ${r.endDate}` },
          { key: 'status', header: 'Status', render: (r) => (
            <Group gap={4}>
              <Badge color={PERIOD_STATUS_COLOR[r.status]}>{r.status}</Badge>
              {r.stale && <Badge color="orange" variant="light">stale</Badge>}
            </Group>
          ) },
          { key: 'processed', header: 'Processed', render: (r) => (r.processedAt ? new Date(r.processedAt).toLocaleString() : '') },
        ]}
      />
      <Modal opened={opening} onClose={() => setOpening(false)} title="Open payroll period">
        <Stack>
          <NumberInput label="Year" value={year} onChange={setYear} min={2000} max={2100} />
          <NumberInput label="Month" value={month} onChange={setMonth} min={1} max={12} />
          <Group justify="flex-end"><Button onClick={() => void open()}>Open</Button></Group>
        </Stack>
      </Modal>
    </>
  );
}

export function PayrollPage() {
  const { t } = useTranslation();
  const { companyId } = useCompany();
  if (!companyId) return <Text>Select a company first.</Text>;
  return (
    <>
      <PageHeader title={t('nav.payroll')} />
      <Tabs defaultValue="periods" keepMounted={false}>
        <Tabs.List mb="md">
          <Tabs.Tab value="periods">Periods</Tabs.Tab>
          <Tabs.Tab value="components">Components</Tabs.Tab>
          <Tabs.Tab value="slabs">Slab tables</Tabs.Tab>
          <Tabs.Tab value="statutory">Statutory</Tabs.Tab>
        </Tabs.List>
        <Tabs.Panel value="periods"><PeriodsPanel companyId={companyId} /></Tabs.Panel>
        <Tabs.Panel value="components"><ComponentsPanel companyId={companyId} /></Tabs.Panel>
        <Tabs.Panel value="slabs"><SlabTablesPanel companyId={companyId} /></Tabs.Panel>
        <Tabs.Panel value="statutory"><StatutoryPanel companyId={companyId} /></Tabs.Panel>
      </Tabs>
    </>
  );
}
