import { Text } from '@mantine/core';
import { useTranslation } from 'react-i18next';

export function NotFoundPage() {
  const { t } = useTranslation();
  return <Text>{t('app.notFound')}</Text>;
}
