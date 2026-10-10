import { Paper, Tabs } from '@mantine/core';
import { IconAlertTriangle, IconBuildingSkyscraper, IconCalendarStats, IconClockHour4, IconPlugConnected } from '@tabler/icons-react';
import { useTranslation } from 'react-i18next';
import { useCompany } from '../../app/CompanyContext';
import { EmptyState } from '../../components/EmptyState';
import { PageHeader } from '../../components/PageHeader';
import { useUrlTab } from '../../components/useUrlTab';
import { DailyPanel } from './DailyPanel';
import { IntegrationPanel } from './IntegrationPanel';

const TABS = ['daily', 'exceptions', 'integration'];

export function AttendancePage() {
  const { t } = useTranslation();
  const { companyId } = useCompany();
  const [tab, setTab] = useUrlTab('daily', TABS);
  const header = <PageHeader title={t('nav.attendance')} description={t('navHint.attendance')} icon={<IconClockHour4 size={22} />} />;
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
          <Tabs.Tab value="daily" leftSection={<IconCalendarStats size={16} />}>Daily attendance</Tabs.Tab>
          <Tabs.Tab value="exceptions" leftSection={<IconAlertTriangle size={16} />}>Exceptions</Tabs.Tab>
          <Tabs.Tab value="integration" leftSection={<IconPlugConnected size={16} />}>Import & devices</Tabs.Tab>
        </Tabs.List>
        <Tabs.Panel value="daily"><DailyPanel companyId={companyId} /></Tabs.Panel>
        <Tabs.Panel value="exceptions"><DailyPanel companyId={companyId} exceptionsOnly /></Tabs.Panel>
        <Tabs.Panel value="integration"><IntegrationPanel companyId={companyId} /></Tabs.Panel>
      </Tabs>
    </>
  );
}
