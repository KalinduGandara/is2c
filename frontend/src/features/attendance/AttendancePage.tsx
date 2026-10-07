import { Tabs, Text } from '@mantine/core';
import { useTranslation } from 'react-i18next';
import { useCompany } from '../../app/CompanyContext';
import { PageHeader } from '../../components/PageHeader';
import { DailyPanel } from './DailyPanel';
import { IntegrationPanel } from './IntegrationPanel';

export function AttendancePage() {
  const { t } = useTranslation();
  const { companyId } = useCompany();
  if (!companyId) return <Text>Select a company first.</Text>;
  return (
    <>
      <PageHeader title={t('nav.attendance')} />
      <Tabs defaultValue="daily" keepMounted={false}>
        <Tabs.List mb="md">
          <Tabs.Tab value="daily">Daily attendance</Tabs.Tab>
          <Tabs.Tab value="exceptions">Exceptions</Tabs.Tab>
          <Tabs.Tab value="integration">Import & devices</Tabs.Tab>
        </Tabs.List>
        <Tabs.Panel value="daily"><DailyPanel companyId={companyId} /></Tabs.Panel>
        <Tabs.Panel value="exceptions"><DailyPanel companyId={companyId} exceptionsOnly /></Tabs.Panel>
        <Tabs.Panel value="integration"><IntegrationPanel companyId={companyId} /></Tabs.Panel>
      </Tabs>
    </>
  );
}
