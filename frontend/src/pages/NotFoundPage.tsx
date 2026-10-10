import { Button, Group, Paper, Text, Title } from '@mantine/core';
import { IconArrowLeft, IconHome } from '@tabler/icons-react';
import { useTranslation } from 'react-i18next';
import { Link, useNavigate } from 'react-router';

export function NotFoundPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  return (
    <Paper withBorder p={{ base: 'xl', sm: 56 }} ta="center" mt="xl">
      <Text fz={64} fw={800} lh={1} variant="gradient" gradient={{ from: 'brand.4', to: 'violet.6', deg: 135 }}>404</Text>
      <Title order={2} mt="md">{t('app.notFound')}</Title>
      <Text c="dimmed" mt="xs">{t('app.notFoundHint')}</Text>
      <Group justify="center" mt="xl">
        <Button variant="default" leftSection={<IconArrowLeft size={16} />} onClick={() => navigate(-1)}>{t('app.goBack')}</Button>
        <Button component={Link} to="/" leftSection={<IconHome size={16} />}>{t('app.goHome')}</Button>
      </Group>
    </Paper>
  );
}
