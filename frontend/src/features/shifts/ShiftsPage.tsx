import { Paper, Tabs } from '@mantine/core';
import { IconBuildingSkyscraper, IconCalendarTime, IconCalendarWeek, IconClock, IconClockPlus, IconLayoutGrid, IconRepeat } from '@tabler/icons-react';
import { useTranslation } from 'react-i18next';
import { useCompany } from '../../app/CompanyContext';
import { useAuth } from '../../auth/AuthContext';
import { EmptyState } from '../../components/EmptyState';
import { PageHeader } from '../../components/PageHeader';
import { useUrlTab } from '../../components/useUrlTab';
import { OtPoliciesPanel } from './OtPoliciesPanel';
import { RostersPanel } from './RostersPanel';
import { RotationsPanel } from './RotationsPanel';
import { SchedulePanel } from './SchedulePanel';
import { ShiftsPanel } from './ShiftsPanel';

export function ShiftsPage() {
  const { t } = useTranslation();
  const { companyId } = useCompany();
  const { can } = useAuth();
  const rosters = can('ROSTER:VIEW');
  const tabs = [...(rosters ? ['rosters', 'schedule'] : []), 'shifts', 'rotations', 'ot'];
  const [tab, setTab] = useUrlTab(tabs[0], tabs);
  const header = <PageHeader title={t('nav.shifts')} description={t('navHint.shifts')} icon={<IconCalendarTime size={22} />} />;
  if (!companyId)
    return (
      <>
        {header}
        <Paper withBorder>
          <EmptyState icon={<IconBuildingSkyscraper size={28} stroke={1.5} />} title={t('app.selectCompanyFirst')} description={t('app.selectCompanyHint')} />
        </Paper>
      </>
    );
  return (
    <>
      {header}
      <Tabs value={tab} onChange={setTab} keepMounted={false}>
        <Tabs.List className="app-tabs-list" mb="lg">
          {rosters && <Tabs.Tab value="rosters" leftSection={<IconLayoutGrid size={16} />}>Rosters</Tabs.Tab>}
          {rosters && <Tabs.Tab value="schedule" leftSection={<IconCalendarWeek size={16} />}>Schedule</Tabs.Tab>}
          <Tabs.Tab value="shifts" leftSection={<IconClock size={16} />}>Shifts</Tabs.Tab>
          <Tabs.Tab value="rotations" leftSection={<IconRepeat size={16} />}>Rotations</Tabs.Tab>
          <Tabs.Tab value="ot" leftSection={<IconClockPlus size={16} />}>OT policies</Tabs.Tab>
        </Tabs.List>
        <Tabs.Panel value="rosters"><RostersPanel companyId={companyId} /></Tabs.Panel>
        <Tabs.Panel value="schedule"><SchedulePanel companyId={companyId} /></Tabs.Panel>
        <Tabs.Panel value="shifts"><ShiftsPanel companyId={companyId} /></Tabs.Panel>
        <Tabs.Panel value="rotations"><RotationsPanel companyId={companyId} /></Tabs.Panel>
        <Tabs.Panel value="ot"><OtPoliciesPanel companyId={companyId} /></Tabs.Panel>
      </Tabs>
    </>
  );
}
