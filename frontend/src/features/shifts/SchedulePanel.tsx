import { Badge, Group, ScrollArea, Select, Table, Text, Tooltip } from '@mantine/core';
import { DateInput } from '@mantine/dates';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { api } from '../../api/client';
import { toOptions, useDepartments } from '../org/useOrgData';
import { dateRange, weekdayShort, type ScheduleRow } from './types';

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

/** FR-040 resolved schedules (roster > rotation > default) for a week. */
export function SchedulePanel({ companyId }: { companyId: string }) {
  const [from, setFrom] = useState(() => mondayOf(new Date()));
  const [departmentId, setDepartmentId] = useState<string | null>(null);
  const to = addDays(from, 6);
  const { data: departments } = useDepartments(companyId);
  const { data: rows = [] } = useQuery({
    queryKey: ['schedules', companyId, departmentId, from],
    queryFn: () => api<ScheduleRow[]>('/schedules', { query: { companyId, departmentId, from, to } }),
  });
  const dates = dateRange(from, to);
  return (
    <>
      <Group mb="sm">
        <DateInput label="Week starting" valueFormat="YYYY-MM-DD" value={from} onChange={(v) => v && setFrom(String(v))} w={160} />
        <Select label="Department" clearable data={toOptions(departments)} value={departmentId} onChange={setDepartmentId} w={220} />
      </Group>
      <ScrollArea type="auto">
        <Table withTableBorder withColumnBorders miw={800}>
          <Table.Thead>
            <Table.Tr>
              <Table.Th>Employee</Table.Th>
              {dates.map((d) => <Table.Th key={d} ta="center">{weekdayShort(d)} {d.slice(5)}</Table.Th>)}
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {rows.map((r) => (
              <Table.Tr key={r.employeeId}>
                <Table.Td><Text size="sm">{r.employeeNumber}</Text><Text size="xs" c="dimmed">{r.name}</Text></Table.Td>
                {r.days.map((d) => (
                  <Table.Td key={d.date} ta="center">
                    <Tooltip label={`${d.source}${d.holidayName ? ` · ${d.holidayName}` : ''}`}>
                      <Badge variant={d.dayType === 'WORKDAY' ? 'light' : 'outline'} color={d.dayType === 'HOLIDAY' ? 'orange' : d.dayType === 'WEEKLY_OFF' ? 'gray' : 'indigo'}>
                        {d.dayType === 'WEEKLY_OFF' ? 'OFF' : (d.shiftCode ?? (d.dayType === 'HOLIDAY' ? 'HOL' : '—'))}
                      </Badge>
                    </Tooltip>
                  </Table.Td>
                ))}
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>
      </ScrollArea>
    </>
  );
}
