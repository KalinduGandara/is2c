import { Stack, Text, ThemeIcon } from '@mantine/core';
import { IconInbox } from '@tabler/icons-react';
import type { ReactNode } from 'react';

interface Props {
  title: string;
  description?: ReactNode;
  icon?: ReactNode;
  action?: ReactNode;
  compact?: boolean;
}

/** Friendly placeholder for lists and pages with nothing to show yet. */
export function EmptyState({ title, description, icon, action, compact }: Props) {
  return (
    <Stack align="center" gap={compact ? 6 : 'xs'} py={compact ? 'lg' : 48} px="md" ta="center">
      <ThemeIcon size={compact ? 40 : 56} radius="xl" variant="light" color="gray">
        {icon ?? <IconInbox size={compact ? 20 : 28} stroke={1.5} />}
      </ThemeIcon>
      <Text fw={600} mt={4}>{title}</Text>
      {description && <Text size="sm" c="dimmed" maw={420}>{description}</Text>}
      {action && <div style={{ marginTop: 8 }}>{action}</div>}
    </Stack>
  );
}
