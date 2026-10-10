import { Group, Text, ThemeIcon } from '@mantine/core';
import { IconFingerprint } from '@tabler/icons-react';
import { useTranslation } from 'react-i18next';

export function BrandMark({ inverted }: { inverted?: boolean }) {
  const { t } = useTranslation();
  return (
    <Group gap={10} wrap="nowrap">
      <ThemeIcon
        size={34}
        radius="md"
        variant={inverted ? 'white' : 'gradient'}
        gradient={{ from: 'brand.5', to: 'violet.6', deg: 135 }}
        color={inverted ? 'brand' : undefined}
        aria-hidden
      >
        <IconFingerprint size={20} />
      </ThemeIcon>
      <Text fw={700} size="lg" lh={1} c={inverted ? 'white' : undefined} style={{ letterSpacing: '-0.01em' }}>
        {t('app.title')}
      </Text>
    </Group>
  );
}
