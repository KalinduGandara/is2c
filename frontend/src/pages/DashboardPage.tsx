import { Text } from '@mantine/core';
import { useTranslation } from 'react-i18next';
import { useCompany } from '../app/CompanyContext';
import { PageHeader } from '../components/PageHeader';
import { DashboardView } from '../features/reports/DashboardView';

export function DashboardPage() {
  const { t } = useTranslation();
  const { companyId } = useCompany();
  return (
    <>
      <PageHeader title={t('nav.dashboard')} />
      {companyId ? <DashboardView companyId={companyId} /> : <Text>Select a company first.</Text>}
    </>
  );
}
