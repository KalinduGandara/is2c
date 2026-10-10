import { Group, Paper, Text, ThemeIcon } from '@mantine/core';
import type { ReactNode } from 'react';
import { Link } from 'react-router';

interface Props {
  label: string;
  value: ReactNode;
  icon?: ReactNode;
  color?: string;
  hint?: ReactNode;
  to?: string;
}

/** KPI tile: label, large figure, tinted icon; optionally a link. */
export function StatCard({ label, value, icon, color = 'brand', hint, to }: Props) {
  const body = (
    <>
      <Group justify="space-between" align="flex-start" wrap="nowrap">
        <Text size="sm" c="dimmed" fw={500}>{label}</Text>
        {icon && <ThemeIcon variant="light" color={color} size={34} radius="md">{icon}</ThemeIcon>}
      </Group>
      <Text fw={700} fz={30} lh={1.1} mt={6} className="tabular">{value}</Text>
      {hint && <Text size="xs" c="dimmed" mt={6}>{hint}</Text>}
    </>
  );
  return to ? (
    <Paper withBorder p="md" component={Link} to={to} className="app-hover-card">{body}</Paper>
  ) : (
    <Paper withBorder p="md">{body}</Paper>
  );
}
