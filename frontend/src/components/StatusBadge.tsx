import { Badge, type BadgeProps } from '@mantine/core';
import { humanize } from './format';

/** Default colours for workflow and record states across modules; callers may override. */
export const STATUS_COLORS: Record<string, string> = {
  ACTIVE: 'green',
  INACTIVE: 'gray',
  RESIGNED: 'orange',
  TERMINATED: 'red',
  PENDING: 'yellow',
  APPROVED: 'teal',
  REJECTED: 'red',
  CANCELLED: 'gray',
  OPEN: 'gray',
  DRAFT: 'blue',
  IN_REVIEW: 'yellow',
  FINALIZED: 'green',
  PUBLISHED: 'green',
  PRESENT: 'green',
  ABSENT: 'red',
  HALF_DAY: 'orange',
  ON_LEAVE: 'blue',
  HALF_DAY_LEAVE: 'cyan',
  WEEKLY_OFF: 'gray',
  HOLIDAY: 'grape',
  INCOMPLETE: 'yellow',
  UNSCHEDULED: 'gray',
  LOCKED: 'dark',
};

interface Props extends Omit<BadgeProps, 'children'> {
  status: string | null | undefined;
  /** Override the colour map for module-specific states. */
  colors?: Record<string, string>;
  /** Text to show instead of the humanized status. */
  label?: string;
}

/** Coloured, human-readable status pill (`IN_REVIEW` → "In review") with a status dot. */
export function StatusBadge({ status, colors, label, ...rest }: Props) {
  if (!status) return null;
  const color = colors?.[status] ?? STATUS_COLORS[status] ?? 'gray';
  return (
    <Badge color={color} variant="light" leftSection={<span aria-hidden style={{ width: 6, height: 6, borderRadius: 6, background: 'currentColor', display: 'inline-block' }} />} {...rest}>
      {label ?? humanize(status)}
    </Badge>
  );
}
