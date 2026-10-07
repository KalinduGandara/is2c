import { Tabs, Text } from '@mantine/core';
import { useTranslation } from 'react-i18next';
import { useCompany } from '../../app/CompanyContext';
import { useAuth } from '../../auth/AuthContext';
import { PageHeader } from '../../components/PageHeader';
import { OtPoliciesPanel } from './OtPoliciesPanel';
import { RostersPanel } from './RostersPanel';
import { RotationsPanel } from './RotationsPanel';
import { SchedulePanel } from './SchedulePanel';
import { ShiftsPanel } from './ShiftsPanel';

export function ShiftsPage() {
  const { t } = useTranslation();
  const { companyId } = useCompany();
  const { can } = useAuth();
  if (!companyId) return <Text>Select a company first.</Text>;
  const first = can('ROSTER:VIEW') ? 'rosters' : 'shifts';
  return (
    <>
      <PageHeader title={t('nav.shifts')} />
      <Tabs defaultValue={first} keepMounted={false}>
        <Tabs.List mb="md">
          {can('ROSTER:VIEW') && <Tabs.Tab value="rosters">Rosters</Tabs.Tab>}
          {can('ROSTER:VIEW') && <Tabs.Tab value="schedule">Schedule</Tabs.Tab>}
          <Tabs.Tab value="shifts">Shifts</Tabs.Tab>
          <Tabs.Tab value="rotations">Rotations</Tabs.Tab>
          <Tabs.Tab value="ot">OT policies</Tabs.Tab>
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
