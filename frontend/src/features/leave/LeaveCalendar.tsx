import { ActionIcon, Badge, Box, Button, Group, Paper, SimpleGrid, Stack, Switch, Text, Tooltip } from '@mantine/core';
import { MonthPickerInput } from '@mantine/dates';
import { IconCalendar, IconChevronLeft, IconChevronRight } from '@tabler/icons-react';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { api } from '../../api/client';
import { humanize } from '../../components/format';
import { formatDateRange, type CalendarEntry } from './types';

const WEEK = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
const HOLIDAY_COLOR = 'orange';
const LEAVE_COLOR = 'indigo';

function monthDays(month: string) {
  const [y, m] = month.split('-').map(Number);
  const first = new Date(Date.UTC(y, m - 1, 1));
  const last = new Date(Date.UTC(y, m, 0)).getUTCDate();
  const lead = (first.getUTCDay() + 6) % 7;
  const cells: (string | null)[] = Array(lead).fill(null);
  for (let d = 1; d <= last; d++) cells.push(`${month}-${String(d).padStart(2, '0')}`);
  while (cells.length % 7 !== 0) cells.push(null);
  return cells;
}

function shiftMonth(month: string, delta: number) {
  const [y, m] = month.split('-').map(Number);
  const d = new Date(Date.UTC(y, m - 1 + delta, 1));
  return `${d.getUTCFullYear()}-${String(d.getUTCMonth() + 1).padStart(2, '0')}`;
}

function localIso(d: Date) {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

function LegendItem({ color, label, variant = 'light' }: { color: string; label: string; variant?: 'light' | 'outline' }) {
  return (
    <Group gap={6} wrap="nowrap">
      <Badge size="xs" variant={variant} color={color} w={18} px={0} aria-hidden> </Badge>
      <Text size="xs" c="dimmed">{label}</Text>
    </Group>
  );
}

/** FR-065 month calendar of holidays and leave (scope-filtered by the API). */
export function LeaveCalendar({ companyId }: { companyId: string }) {
  const today = localIso(new Date());
  const [month, setMonth] = useState(() => today.slice(0, 7));
  const [pending, setPending] = useState(true);
  const cells = monthDays(month);
  const from = `${month}-01`;
  const to = cells.filter(Boolean).at(-1)!;
  const { data = [], isFetching } = useQuery({
    queryKey: ['leave-calendar', companyId, month, pending],
    queryFn: () => api<CalendarEntry[]>('/leave/calendar', { query: { companyId, from, to, includePending: pending } }),
  });
  const on = (date: string) => data.filter((e) => e.from <= date && e.to >= date);
  const leaveCount = data.filter((e) => e.kind === 'LEAVE').length;
  const holidayCount = data.filter((e) => e.kind === 'HOLIDAY').length;

  return (
    <Paper withBorder p={{ base: 'sm', sm: 'lg' }}>
      <Stack gap="md">
        <Group justify="space-between" wrap="wrap" gap="sm">
          <Group gap="xs" wrap="nowrap">
            <Tooltip label="Previous month">
              <ActionIcon variant="default" size={36} aria-label="Previous month" onClick={() => setMonth(shiftMonth(month, -1))}>
                <IconChevronLeft size={18} />
              </ActionIcon>
            </Tooltip>
            <MonthPickerInput
              aria-label="Month"
              leftSection={<IconCalendar size={16} />}
              valueFormat="MMMM YYYY"
              value={`${month}-01`}
              onChange={(v) => v && setMonth(String(v).slice(0, 7))}
              w={{ base: 160, sm: 190 }}
            />
            <Tooltip label="Next month">
              <ActionIcon variant="default" size={36} aria-label="Next month" onClick={() => setMonth(shiftMonth(month, 1))}>
                <IconChevronRight size={18} />
              </ActionIcon>
            </Tooltip>
            {month !== today.slice(0, 7) && (
              <Button variant="subtle" size="sm" onClick={() => setMonth(today.slice(0, 7))} visibleFrom="xs">
                Today
              </Button>
            )}
          </Group>
          <Switch label="Include pending" checked={pending} onChange={(e) => setPending(e.currentTarget.checked)} />
        </Group>

        <Group justify="space-between" wrap="wrap" gap="xs">
          <Group gap="md" wrap="wrap">
            <LegendItem color={LEAVE_COLOR} label="Approved leave" />
            {pending && <LegendItem color={LEAVE_COLOR} variant="outline" label="Pending leave" />}
            <LegendItem color={HOLIDAY_COLOR} label="Holiday" />
            <Group gap={6} wrap="nowrap">
              <Box w={18} h={12} style={{ border: '2px solid var(--mantine-primary-color-filled)', borderRadius: 4 }} aria-hidden />
              <Text size="xs" c="dimmed">Today</Text>
            </Group>
          </Group>
          <Text size="xs" c="dimmed" aria-live="polite">
            {isFetching ? 'Loading…' : `${leaveCount} leave · ${holidayCount} holiday${holidayCount === 1 ? '' : 's'}`}
          </Text>
        </Group>

        <Box style={{ overflowX: 'auto' }}>
          <SimpleGrid cols={7} spacing={4} verticalSpacing={4} miw={320}>
            {WEEK.map((d, i) => (
              <Text key={d} size="xs" fw={600} ta="center" c={i >= 5 ? 'dimmed' : undefined} tt="uppercase">
                {d}
              </Text>
            ))}
            {cells.map((date, i) => {
              if (!date) return <Box key={`blank-${i}`} mih={76} />;
              const entries = on(date);
              const holiday = entries.some((e) => e.kind === 'HOLIDAY');
              const weekend = i % 7 >= 5;
              const isToday = date === today;
              return (
                <Paper
                  key={date}
                  withBorder
                  p={4}
                  mih={76}
                  radius="sm"
                  aria-label={formatDateRange(date, date)}
                  style={{
                    background: holiday
                      ? 'light-dark(var(--mantine-color-orange-0), rgba(253, 126, 20, 0.12))'
                      : weekend
                        ? 'light-dark(var(--mantine-color-gray-0), var(--mantine-color-dark-6))'
                        : undefined,
                    borderColor: isToday ? 'var(--mantine-primary-color-filled)' : undefined,
                    borderWidth: isToday ? 2 : undefined,
                    minWidth: 0,
                  }}
                >
                  <Stack gap={2}>
                    <Text size="xs" fw={isToday ? 700 : 500} c={isToday ? 'var(--mantine-primary-color-filled)' : 'dimmed'}>
                      {Number(date.slice(8))}
                    </Text>
                    {entries.map((e) => (
                      <Tooltip
                        key={e.id}
                        multiline
                        maw={260}
                        label={
                          e.kind === 'HOLIDAY'
                            ? `Holiday: ${e.employeeName}`
                            : `${e.employeeName} — ${e.leaveType ?? 'Leave'}${e.halfDayPart ? ` (${e.halfDayPart.toLowerCase()})` : ''}, ${formatDateRange(e.from, e.to)} · ${humanize(e.status)}`
                        }
                      >
                        <Badge
                          size="sm"
                          radius="sm"
                          variant={e.status === 'PENDING' ? 'outline' : 'light'}
                          color={e.kind === 'HOLIDAY' ? HOLIDAY_COLOR : (e.color ?? LEAVE_COLOR)}
                          fullWidth
                          tt="none"
                          style={{ cursor: 'default' }}
                        >
                          {e.kind === 'HOLIDAY' ? e.employeeName : e.employeeName.split(' ')[0]}
                        </Badge>
                      </Tooltip>
                    ))}
                  </Stack>
                </Paper>
              );
            })}
          </SimpleGrid>
        </Box>
      </Stack>
    </Paper>
  );
}
