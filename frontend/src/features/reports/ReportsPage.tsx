import { Tabs, Text } from '@mantine/core';
import { useTranslation } from 'react-i18next';
import { useCompany } from '../../app/CompanyContext';
import { useAuth } from '../../auth/AuthContext';
import { PageHeader } from '../../components/PageHeader';
import { AuditLogPanel } from '../admin/AuditLogPanel';
import { ReportsPanel } from './ReportsPanel';

export function ReportsPage() {
  const { t } = useTranslation();
  const { companyId } = useCompany();
  const { can } = useAuth();
  const reports = can('REPORT:VIEW');
  return (
    <>
      <PageHeader title={t('nav.reports')} />
      <Tabs defaultValue={reports ? 'reports' : 'audit'} keepMounted={false}>
        <Tabs.List mb="md">
          {reports && <Tabs.Tab value="reports">Reports</Tabs.Tab>}
          {can('AUDIT:VIEW') && <Tabs.Tab value="audit">Audit log</Tabs.Tab>}
        </Tabs.List>
        <Tabs.Panel value="reports">
          {companyId ? <ReportsPanel companyId={companyId} /> : <Text>Select a company first.</Text>}
        </Tabs.Panel>
        <Tabs.Panel value="audit"><AuditLogPanel /></Tabs.Panel>
      </Tabs>
    </>
  );
}
