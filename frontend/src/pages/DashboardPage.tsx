import { Title } from '@mantine/core';
import { useTranslation } from 'react-i18next';

export function DashboardPage() {
  const { t } = useTranslation();
  return <Title order={2}>{t('nav.dashboard')}</Title>;
}
