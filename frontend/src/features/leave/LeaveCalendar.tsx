import { Badge, Group, Paper, SimpleGrid, Stack, Switch, Text, Tooltip } from '@mantine/core';
import { MonthPickerInput } from '@mantine/dates';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { api } from '../../api/client';
import type { CalendarEntry } from './types';

const WEEK = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];

function monthDays(month: string) {
  const [y, m] = month.split('-').map(Number);
  const first = new Date(Date.UTC(y, m - 1, 1));
  const last = new Date(Date.UTC(y, m, 0)).getUTCDate();
  const lead = (first.getUTCDay() + 6) % 7;
  const cells: (string | null)[] = Array(lead).fill(null);
  for (let d = 1; d <= last; d++) cells.push(`${month}-${String(d).padStart(2, '0')}`);
  return cells;
}

/** FR-065 month calendar of holidays and leave (scope-filtered by the API). */
export function LeaveCalendar({ companyId }: { companyId: string }) {
  const [month, setMonth] = useState(() => new Date().toISOString().slice(0, 7));
  const [pending, setPending] = useState(true);
  const cells = monthDays(month);
  const from = `${month}-01`;
  const to = cells[cells.length - 1]!;
  const { data = [] } = useQuery({
    queryKey: ['leave-calendar', companyId, month, pending],
    queryFn: () => api<CalendarEntry[]>('/leave/calendar', { query: { companyId, from, to, includePending: pending } }),
  });
  const on = (date: string) => data.filter((e) => e.from <= date && e.to >= date);
  return (
    <Stack>
      <Group>
        <MonthPickerInput label="Month" value={`${month}-01`} onChange={(v) => v && setMonth(String(v).slice(0, 7))} w={180} />
        <Switch mt={24} label="Include pending" checked={pending} onChange={(e) => setPending(e.currentTarget.checked)} />
      </Group>
      <SimpleGrid cols={7} spacing={4}>
        {WEEK.map((d) => <Text key={d} size="xs" fw={600} ta="center">{d}</Text>)}
        {cells.map((date, i) => (
          <Paper key={date ?? `blank-${i}`} withBorder p={4} mih={72} bg={date && on(date).some((e) => e.kind === 'HOLIDAY') ? 'orange.0' : undefined}>
            {date && (
              <Stack gap={2}>
                <Text size="xs" c="dimmed">{Number(date.slice(8))}</Text>
                {on(date).map((e) => (
                  <Tooltip key={e.id} label={e.kind === 'HOLIDAY' ? e.employeeName : `${e.employeeName} — ${e.leaveType}${e.halfDayPart ? ` (${e.halfDayPart.toLowerCase()})` : ''} [${e.status}]`}>
                    <Badge size="xs" variant={e.status === 'PENDING' ? 'outline' : 'light'} color={e.kind === 'HOLIDAY' ? 'orange' : (e.color ?? 'indigo')} fullWidth>
                      {e.kind === 'HOLIDAY' ? e.employeeName : e.employeeName.split(' ')[0]}
                    </Badge>
                  </Tooltip>
                ))}
              </Stack>
            )}
          </Paper>
        ))}
      </SimpleGrid>
    </Stack>
  );
}
