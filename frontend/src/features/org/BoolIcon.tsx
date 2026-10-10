import { ThemeIcon, Tooltip } from '@mantine/core';
import { IconCheck, IconMinus } from '@tabler/icons-react';

/** Compact yes/no indicator for boolean columns (check vs. dash), readable by screen readers. */
export function BoolIcon({ value, yes = 'Yes', no = 'No' }: { value: boolean | null | undefined; yes?: string; no?: string }) {
  const label = value ? yes : no;
  return (
    <Tooltip label={label}>
      <ThemeIcon size={22} radius="xl" variant="light" color={value ? 'teal' : 'gray'} role="img" aria-label={label}>
        {value ? <IconCheck size={14} stroke={2.5} /> : <IconMinus size={14} />}
      </ThemeIcon>
    </Tooltip>
  );
}
