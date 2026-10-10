import { Anchor, Group, Stack, Text, ThemeIcon, Title } from '@mantine/core';
import { IconArrowLeft } from '@tabler/icons-react';
import type { ReactNode } from 'react';
import { Link } from 'react-router';

interface Props {
  title: ReactNode;
  description?: ReactNode;
  icon?: ReactNode;
  actions?: ReactNode;
  /** Renders a "← label" link above the title. */
  back?: { to: string; label: string };
  /** Extra content rendered next to the title (e.g. a status badge). */
  badge?: ReactNode;
}

export function PageHeader({ title, description, icon, actions, back, badge }: Props) {
  return (
    <Stack gap={6} mb="lg">
      {back && (
        <Anchor component={Link} to={back.to} size="sm" c="dimmed" w="fit-content">
          <Group gap={4} wrap="nowrap">
            <IconArrowLeft size={14} />
            {back.label}
          </Group>
        </Anchor>
      )}
      <Group justify="space-between" align="center" wrap="wrap" gap="sm">
        <Group gap="sm" wrap="nowrap" align="center" style={{ minWidth: 0 }}>
          {icon && (
            <ThemeIcon size={40} radius="md" variant="light" visibleFrom="xs">
              {icon}
            </ThemeIcon>
          )}
          <div style={{ minWidth: 0 }}>
            <Group gap="xs" wrap="wrap">
              <Title order={2}>{title}</Title>
              {badge}
            </Group>
            {description && (
              <Text size="sm" c="dimmed" mt={2}>
                {description}
              </Text>
            )}
          </div>
        </Group>
        {actions && <Group gap="xs">{actions}</Group>}
      </Group>
    </Stack>
  );
}
