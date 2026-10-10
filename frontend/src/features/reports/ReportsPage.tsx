import { Paper, Tabs } from '@mantine/core';
import { IconBuildingSkyscraper, IconChartBar, IconFileAnalytics, IconHistory } from '@tabler/icons-react';
import { useTranslation } from 'react-i18next';
import { useCompany } from '../../app/CompanyContext';
import { useAuth } from '../../auth/AuthContext';
import { EmptyState } from '../../components/EmptyState';
import { PageHeader } from '../../components/PageHeader';
import { useUrlTab } from '../../components/useUrlTab';
import { AuditLogPanel } from '../admin/AuditLogPanel';
import { ReportsPanel } from './ReportsPanel';

export function ReportsPage() {
  const { t } = useTranslation();
  const { companyId } = useCompany();
  const { can } = useAuth();
  const reports = can('REPORT:VIEW');
  const audit = can('AUDIT:VIEW');
  const allowed = [...(reports ? ['reports'] : []), ...(audit ? ['audit'] : [])];
  const [tab, setTab] = useUrlTab(reports ? 'reports' : 'audit', allowed);
  return (
    <>
      <PageHeader title={t('nav.reports')} description={t('navHint.reports')} icon={<IconChartBar size={22} />} />
      <Tabs value={tab} onChange={setTab} keepMounted={false}>
        <Tabs.List className="app-tabs-list" mb="lg">
          {reports && <Tabs.Tab value="reports" leftSection={<IconFileAnalytics size={16} />}>Reports</Tabs.Tab>}
          {audit && <Tabs.Tab value="audit" leftSection={<IconHistory size={16} />}>Audit log</Tabs.Tab>}
        </Tabs.List>
        <Tabs.Panel value="reports">
          {companyId ? (
            <ReportsPanel companyId={companyId} />
          ) : (
            <Paper withBorder>
              <EmptyState icon={<IconBuildingSkyscraper size={28} stroke={1.5} />} title={t('app.selectCompanyFirst')} description={t('app.selectCompanyHint')} />
            </Paper>
          )}
        </Tabs.Panel>
        <Tabs.Panel value="audit"><AuditLogPanel /></Tabs.Panel>
      </Tabs>
    </>
  );
}
