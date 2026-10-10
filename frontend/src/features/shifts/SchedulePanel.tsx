import { ActionIcon, Avatar, Box, Button, Group, Paper, ScrollArea, Select, Skeleton, Table, Text, Tooltip } from '@mantine/core';
import { DateInput } from '@mantine/dates';
import { IconCalendarWeek, IconChevronLeft, IconChevronRight } from '@tabler/icons-react';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useState, type CSSProperties } from 'react';
import { api } from '../../api/client';
import { EmptyState } from '../../components/EmptyState';
import { formatDate, humanize } from '../../components/format';
import { toOptions, useDepartments } from '../org/useOrgData';
import { dateRange, dayMonth, isWeekend, shiftTint, weekdayShort, type ScheduleRow } from './types';
import { useShifts } from './useShifts';

function mondayOf(date: Date) {
  const d = new Date(Date.UTC(date.getFullYear(), date.getMonth(), date.getDate()));
  d.setUTCDate(d.getUTCDate() - ((d.getUTCDay() + 6) % 7));
  return d.toISOString().slice(0, 10);
}

function addDays(iso: string, n: number) {
  const d = new Date(`${iso}T00:00:00Z`);
  d.setUTCDate(d.getUTCDate() + n);
  return d.toISOString().slice(0, 10);
}

const HEAD_BG = 'light-dark(var(--mantine-color-gray-0), var(--mantine-color-dark-6))';
const TODAY_BG = 'var(--mantine-primary-color-light)';
const STICKY: CSSProperties = { position: 'sticky', left: 0, zIndex: 2, background: 'var(--app-surface)', boxShadow: '1px 0 0 var(--app-border)' };
const OFF_STYLE = { background: 'light-dark(var(--mantine-color-gray-1), var(--mantine-color-dark-5))', color: 'var(--mantine-color-dimmed)' };
const HOLIDAY_STYLE = { background: 'var(--mantine-color-grape-light)', color: 'var(--mantine-color-grape-light-color)' };

/** FR-040 resolved schedules (roster > rotation > default) for a week. */
export function SchedulePanel({ companyId }: { companyId: string }) {
  const [from, setFrom] = useState(() => mondayOf(new Date()));
  const [departmentId, setDepartmentId] = useState<string | null>(null);
  const to = addDays(from, 6);
  const now = new Date();
  const today = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
  const { data: departments } = useDepartments(companyId);
  const { data: shifts = [] } = useShifts(companyId);
  const { data: rows = [], isLoading } = useQuery({
    queryKey: ['schedules', companyId, departmentId, from],
    queryFn: () => api<ScheduleRow[]>('/schedules', { query: { companyId, departmentId, from, to } }),
    placeholderData: keepPreviousData,
  });
  const dates = dateRange(from, to);

  return (
    <>
      <Paper withBorder p="md" mb="md">
        <Group justify="space-between" align="flex-end" wrap="wrap" gap="sm">
          <Group align="flex-end" wrap="wrap" gap="sm">
            <DateInput label="Week starting" valueFormat="D MMM YYYY" value={from} onChange={(v) => v && setFrom(String(v))} w={{ base: '100%', xs: 170 }} />
            <Select label="Department" placeholder="All departments" clearable searchable data={toOptions(departments)} value={departmentId} onChange={setDepartmentId} w={{ base: '100%', sm: 220 }} />
          </Group>
          <Group gap={4} wrap="nowrap">
            <Tooltip label="Previous week">
              <ActionIcon variant="default" size="lg" aria-label="Previous week" onClick={() => setFrom(addDays(from, -7))}><IconChevronLeft size={16} /></ActionIcon>
            </Tooltip>
            <Button variant="default" onClick={() => setFrom(mondayOf(new Date()))}>This week</Button>
            <Tooltip label="Next week">
              <ActionIcon variant="default" size="lg" aria-label="Next week" onClick={() => setFrom(addDays(from, 7))}><IconChevronRight size={16} /></ActionIcon>
            </Tooltip>
          </Group>
        </Group>
        <Text size="sm" c="dimmed" mt="xs">
          {formatDate(from)} – {formatDate(to)} · resolved from rosters, then rotations, then default shifts.
        </Text>
      </Paper>

      <Paper withBorder style={{ overflow: 'hidden' }}>
        {isLoading ? (
          <Box p="md">
            {Array.from({ length: 4 }, (_, i) => <Skeleton key={i} h={28} mb="sm" radius="sm" />)}
          </Box>
        ) : rows.length === 0 ? (
          <EmptyState compact icon={<IconCalendarWeek size={20} />} title="No schedules" description="No active employees match this department for the selected week." />
        ) : (
          <ScrollArea type="auto">
            <Table withColumnBorders miw={220 + dates.length * 96} verticalSpacing={6} style={{ borderCollapse: 'separate', borderSpacing: 0 }}>
              <Table.Thead>
                <Table.Tr>
                  <Table.Th style={{ ...STICKY, zIndex: 3, background: HEAD_BG }} miw={200}>
                    <Text size="xs" fw={600} c="dimmed" tt="uppercase">Employee</Text>
                  </Table.Th>
                  {dates.map((d) => (
                    <Table.Th key={d} ta="center" style={{ background: d === today ? TODAY_BG : HEAD_BG }}>
                      <Text size="xs" fw={700} c={d === today ? 'brand' : isWeekend(d) ? 'dimmed' : undefined}>{weekdayShort(d)}</Text>
                      <Text size="xs" c="dimmed" style={{ whiteSpace: 'nowrap' }}>{dayMonth(d)}</Text>
                    </Table.Th>
                  ))}
                </Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {rows.map((r) => (
                  <Table.Tr key={r.employeeId}>
                    <Table.Td style={STICKY}>
                      <Group gap="xs" wrap="nowrap">
                        <Avatar name={r.name} color="initials" size="sm" radius="xl" />
                        <div style={{ minWidth: 0 }}>
                          <Text size="sm" fw={500} truncate>{r.name}</Text>
                          <Text size="xs" c="dimmed">{r.employeeNumber}</Text>
                        </div>
                      </Group>
                    </Table.Td>
                    {r.days.map((d) => {
                      const holiday = d.dayType === 'HOLIDAY';
                      const off = d.dayType === 'WEEKLY_OFF';
                      const style = holiday ? HOLIDAY_STYLE : off ? OFF_STYLE : d.shiftId ? (shiftTint(shifts, d.shiftId) ?? OFF_STYLE) : undefined;
                      const label = off ? 'OFF' : (d.shiftCode ?? (holiday ? 'Holiday' : '—'));
                      return (
                        <Table.Td key={d.date} p={4} style={{ background: d.date === today ? TODAY_BG : undefined }}>
                          <Tooltip label={`${humanize(d.source)}${d.holidayName ? ` · ${d.holidayName}` : ''}`} withArrow>
                            <Box ta="center" py={4} px={6} style={{ borderRadius: 'var(--mantine-radius-sm)', ...style }}>
                              <Text size="xs" fw={style ? 600 : undefined} c={style ? undefined : 'dimmed'} truncate>{label}</Text>
                              {holiday && d.holidayName && <Text fz={10} truncate>{d.holidayName}</Text>}
                            </Box>
                          </Tooltip>
                        </Table.Td>
                      );
                    })}
                  </Table.Tr>
                ))}
              </Table.Tbody>
            </Table>
          </ScrollArea>
        )}
      </Paper>
    </>
  );
}
