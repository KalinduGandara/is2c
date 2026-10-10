import { Badge, Button, Group, Modal, NumberInput, Paper, SimpleGrid, Stack, Tabs, Text, Tooltip } from '@mantine/core';
import {
  IconBuildingSkyscraper,
  IconCalendarStats,
  IconCalendarPlus,
  IconListDetails,
  IconPlus,
  IconReportMoney,
  IconScale,
  IconStairs,
} from '@tabler/icons-react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router';
import { api } from '../../api/client';
import { useCompany } from '../../app/CompanyContext';
import { useAuth } from '../../auth/AuthContext';
import { DataTable } from '../../components/DataTable';
import { EmptyState } from '../../components/EmptyState';
import { formatDate, formatDateTime } from '../../components/format';
import { showError } from '../../components/notify';
import { PageHeader } from '../../components/PageHeader';
import { StatusBadge } from '../../components/StatusBadge';
import { useUrlTab } from '../../components/useUrlTab';
import { ComponentsPanel } from './ComponentsPanel';
import { SlabTablesPanel } from './SlabTablesPanel';
import { StatutoryPanel } from './StatutoryPanel';
import { periodLabel, PERIOD_STATUS_COLOR, type PayrollPeriod } from './types';

const validMonth = (year: number | string, month: number | string) => {
  const y = Number(year);
  const m = Number(month);
  return year !== '' && month !== '' && Number.isInteger(y) && Number.isInteger(m) && y >= 2000 && y <= 2100 && m >= 1 && m <= 12;
};

function PeriodsPanel({ companyId }: { companyId: string }) {
  const { can } = useAuth();
  const navigate = useNavigate();
  const client = useQueryClient();
  const now = new Date();
  const [opening, setOpening] = useState(false);
  const [saving, setSaving] = useState(false);
  const [year, setYear] = useState<number | string>(now.getFullYear());
  const [month, setMonth] = useState<number | string>(now.getMonth() + 1);
  const { data = [], isLoading } = useQuery({
    queryKey: ['payroll-periods', companyId],
    queryFn: () => api<PayrollPeriod[]>('/payroll/periods', { query: { companyId } }),
  });
  const valid = validMonth(year, month);
  const open = async () => {
    setSaving(true);
    try {
      const p = await api<PayrollPeriod>('/payroll/periods', { method: 'POST', body: { companyId, year: Number(year), month: Number(month) } });
      setOpening(false);
      await client.invalidateQueries({ queryKey: ['payroll-periods'] });
      navigate(`/payroll/periods/${p.id}`);
    } catch (e) {
      showError(e);
    } finally {
      setSaving(false);
    }
  };
  return (
    <>
      <Group justify="space-between" mb="sm" wrap="wrap" gap="xs">
        <Text size="sm" c="dimmed">
          {data.length > 0 ? <><Text span fw={700} c="var(--mantine-color-text)">{data.length}</Text> payroll {data.length === 1 ? 'period' : 'periods'} — select one to review, process or finalize.</> : 'One period per month; select a period to work on it.'}
        </Text>
        {can('PAYROLL:ADD') && (
          <Button leftSection={<IconPlus size={16} />} onClick={() => setOpening(true)}>Open period</Button>
        )}
      </Group>
      <DataTable
        rows={data}
        loading={isLoading}
        rowKey={(r) => r.id}
        onRowClick={(r) => navigate(`/payroll/periods/${r.id}`)}
        rowLabel={(r) => `Open ${periodLabel(r)}`}
        minWidth={760}
        emptyTitle="No payroll periods yet"
        emptyDescription={can('PAYROLL:ADD') ? 'Use “Open period” to start a payroll month.' : 'Payroll periods will appear here once they are opened.'}
        emptyIcon={<IconCalendarStats size={22} stroke={1.5} />}
        columns={[
          { key: 'period', header: 'Period', render: (r) => <Text fw={600} size="sm">{periodLabel(r)}</Text> },
          { key: 'dates', header: 'Dates', render: (r) => <Text size="sm" c="dimmed">{formatDate(r.startDate)} – {formatDate(r.endDate)}</Text> },
          { key: 'status', header: 'Status', render: (r) => (
            <Group gap={4} wrap="nowrap">
              <StatusBadge status={r.status} colors={PERIOD_STATUS_COLOR} miw="max-content" />
              {r.stale && (
                <Tooltip label="Data changed after processing — reprocess before submitting">
                  <Badge color="orange" variant="outline" miw="max-content">Needs reprocessing</Badge>
                </Tooltip>
              )}
            </Group>
          ) },
          { key: 'processed', header: 'Last processed', render: (r) => <Text size="sm" c={r.processedAt ? undefined : 'dimmed'}>{r.processedAt ? formatDateTime(r.processedAt) : 'Not yet'}</Text> },
          { key: 'finalized', header: 'Finalized', render: (r) => <Text size="sm" c={r.finalizedAt ? undefined : 'dimmed'}>{r.finalizedAt ? formatDateTime(r.finalizedAt) : '—'}</Text> },
        ]}
      />
      <Modal opened={opening} onClose={() => setOpening(false)} title="Open payroll period">
        <Stack>
          <Text size="sm" c="dimmed">Choose the calendar month to pay. Attendance, leave and salary data for that month will be used when you process it.</Text>
          <SimpleGrid cols={2}>
            <NumberInput label="Year" value={year} onChange={setYear} min={2000} max={2100} allowDecimal={false} withAsterisk />
            <NumberInput label="Month" value={month} onChange={setMonth} min={1} max={12} allowDecimal={false} withAsterisk description="1 = January" />
          </SimpleGrid>
          <Paper withBorder p="sm" bg="var(--mantine-color-default-hover)">
            <Group gap="xs" wrap="nowrap">
              <IconCalendarPlus size={18} stroke={1.5} />
              <Text size="sm">
                {valid ? <>This opens <Text span fw={700}>{periodLabel({ year: Number(year), month: Number(month) })}</Text>.</> : 'Enter a valid year (2000–2100) and month (1–12).'}
              </Text>
            </Group>
          </Paper>
          <Group justify="flex-end">
            <Button variant="default" onClick={() => setOpening(false)}>Cancel</Button>
            <Button disabled={!valid} loading={saving} onClick={() => void open()}>Open</Button>
          </Group>
        </Stack>
      </Modal>
    </>
  );
}

const TABS = ['periods', 'components', 'slabs', 'statutory'];

export function PayrollPage() {
  const { t } = useTranslation();
  const { companyId } = useCompany();
  const [tab, setTab] = useUrlTab('periods', TABS);
  const header = <PageHeader title={t('nav.payroll')} description={t('navHint.payroll')} icon={<IconReportMoney size={22} />} />;
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
      <Tabs value={tab} onChange={setTab} keepMounted={false}>
        <Tabs.List className="app-tabs-list" mb="lg">
          <Tabs.Tab value="periods" leftSection={<IconCalendarStats size={16} />}>Periods</Tabs.Tab>
          <Tabs.Tab value="components" leftSection={<IconListDetails size={16} />}>Components</Tabs.Tab>
          <Tabs.Tab value="slabs" leftSection={<IconStairs size={16} />}>Slab tables</Tabs.Tab>
          <Tabs.Tab value="statutory" leftSection={<IconScale size={16} />}>Statutory</Tabs.Tab>
        </Tabs.List>
        <Tabs.Panel value="periods"><PeriodsPanel companyId={companyId} /></Tabs.Panel>
        <Tabs.Panel value="components"><ComponentsPanel companyId={companyId} /></Tabs.Panel>
        <Tabs.Panel value="slabs"><SlabTablesPanel companyId={companyId} /></Tabs.Panel>
        <Tabs.Panel value="statutory"><StatutoryPanel companyId={companyId} /></Tabs.Panel>
      </Tabs>
    </>
  );
}
