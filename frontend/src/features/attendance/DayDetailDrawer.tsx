import { Alert, Badge, Button, Checkbox, Drawer, Group, Stack, Table, Text, TextInput, Title } from '@mantine/core';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { api } from '../../api/client';
import { useAuth } from '../../auth/AuthContext';
import { showError, showSuccess } from '../../components/notify';
import { STATUS_COLOR, time, type DayDetail } from './types';

interface Props {
  employeeId: string;
  date: string;
  onClose: () => void;
}

/** FR-050/051/055 day detail with raw events, adjustment history and a correction form. */
export function DayDetailDrawer({ employeeId, date, onClose }: Props) {
  const { can } = useAuth();
  const client = useQueryClient();
  const key = ['attendance-day', employeeId, date];
  const { data } = useQuery({ queryKey: key, queryFn: () => api<DayDetail>(`/attendance/daily/${employeeId}/${date}`) });
  const [inTime, setInTime] = useState('');
  const [outTime, setOutTime] = useState('');
  const [outNextDay, setOutNextDay] = useState(false);
  const [reason, setReason] = useState('');
  const [override, setOverride] = useState(false);
  const [busy, setBusy] = useState(false);

  const nextDay = () => {
    const d = new Date(`${date}T00:00:00Z`);
    d.setUTCDate(d.getUTCDate() + 1);
    return d.toISOString().slice(0, 10);
  };

  const submit = async () => {
    setBusy(true);
    try {
      const result = await api<{ status: string }>('/attendance/adjustments', {
        method: 'POST',
        body: {
          employeeId,
          date,
          in: inTime ? `${date}T${inTime}:00` : null,
          out: outTime ? `${outNextDay ? nextDay() : date}T${outTime}:00` : null,
          reason,
          overrideLock: override,
        },
      });
      showSuccess(result.status === 'PENDING' ? 'Correction submitted for approval' : 'Attendance corrected');
      setReason('');
      await client.invalidateQueries({ queryKey: key });
      await client.invalidateQueries({ queryKey: ['attendance-daily'] });
    } catch (e) {
      showError(e);
    } finally {
      setBusy(false);
    }
  };

  const d = data?.daily;
  return (
    <Drawer opened onClose={onClose} title={`Attendance ${date}`} position="right" size="lg">
      {data && (
        <Stack>
          {d ? (
            <>
              <Group>
                <Badge color={STATUS_COLOR[d.status]}>{d.status}</Badge>
                <Text size="sm">{d.employeeNumber} — {d.employeeName}</Text>
                {d.manual && <Badge variant="outline">Manual</Badge>}
              </Group>
              <Table withTableBorder>
                <Table.Tbody>
                  <Table.Tr><Table.Td>In / Out</Table.Td><Table.Td>{time(d.firstIn)} – {time(d.lastOut)}</Table.Td></Table.Tr>
                  <Table.Tr><Table.Td>Worked / Scheduled</Table.Td><Table.Td>{d.workedMinutes} / {d.scheduledMinutes} min</Table.Td></Table.Tr>
                  <Table.Tr><Table.Td>Late / Early / Short</Table.Td><Table.Td>{d.lateMinutes} / {d.earlyMinutes} / {d.shortMinutes} min</Table.Td></Table.Tr>
                  <Table.Tr><Table.Td>OT (workday / off / holiday)</Table.Td><Table.Td>{d.otWorkdayMinutes} / {d.otOffdayMinutes} / {d.otHolidayMinutes} min {d.otRequiresApproval && (d.otApproved ? '(approved)' : d.otRejected ? '(rejected)' : '(pending)')}</Table.Td></Table.Tr>
                  <Table.Tr><Table.Td>Exceptions</Table.Td><Table.Td>{d.exceptions.join(', ') || '—'}</Table.Td></Table.Tr>
                </Table.Tbody>
              </Table>
            </>
          ) : (
            <Alert>This day has not been processed yet.</Alert>
          )}
          <Title order={5}>Punches</Title>
          {data.events.length === 0 ? <Text size="sm" c="dimmed">No punches</Text> : (
            <Table withTableBorder>
              <Table.Tbody>
                {data.events.map((e) => (
                  <Table.Tr key={e.id}><Table.Td>{e.time.replace('T', ' ').slice(0, 16)}</Table.Td><Table.Td>{e.direction}</Table.Td><Table.Td>{e.deviceId}</Table.Td></Table.Tr>
                ))}
              </Table.Tbody>
            </Table>
          )}
          {data.adjustments.length > 0 && (
            <>
              <Title order={5}>Corrections</Title>
              <Table withTableBorder>
                <Table.Tbody>
                  {data.adjustments.map((a) => (
                    <Table.Tr key={a.id}>
                      <Table.Td>{a.type}</Table.Td>
                      <Table.Td>{time(a.beforeIn)}–{time(a.beforeOut)} → {time(a.newIn)}–{time(a.newOut)}</Table.Td>
                      <Table.Td>{a.reason}</Table.Td>
                      <Table.Td><Badge variant="light">{a.status}</Badge></Table.Td>
                    </Table.Tr>
                  ))}
                </Table.Tbody>
              </Table>
            </>
          )}
          {can('ATTENDANCE:ADD', 'ATTENDANCE:EDIT') && (
            <>
              <Title order={5}>Correct this day</Title>
              <Group grow>
                <TextInput label="IN (HH:mm)" placeholder="08:30" value={inTime} onChange={(e) => setInTime(e.currentTarget.value)} />
                <TextInput label="OUT (HH:mm)" placeholder="17:30" value={outTime} onChange={(e) => setOutTime(e.currentTarget.value)} />
              </Group>
              <Checkbox label="OUT is on the next day" checked={outNextDay} onChange={(e) => setOutNextDay(e.currentTarget.checked)} />
              <TextInput label="Reason" withAsterisk value={reason} onChange={(e) => setReason(e.currentTarget.value)} />
              {can('ATTENDANCE:CONFIGURE') && (
                <Checkbox label="Override payroll lock (audited)" checked={override} onChange={(e) => setOverride(e.currentTarget.checked)} />
              )}
              <Group justify="flex-end">
                <Button onClick={() => void submit()} loading={busy} disabled={!reason.trim() || (!inTime && !outTime)}>Submit correction</Button>
              </Group>
            </>
          )}
        </Stack>
      )}
    </Drawer>
  );
}
