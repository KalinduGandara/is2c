import { Alert, Avatar, Badge, Button, Checkbox, Drawer, Group, Paper, SimpleGrid, Skeleton, Stack, Text, TextInput, Timeline, Title } from '@mantine/core';
import { IconArrowRight, IconInfoCircle, IconLogin2, IconLogout2, IconPencil, IconPoint, IconSend } from '@tabler/icons-react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { api } from '../../api/client';
import { useAuth } from '../../auth/AuthContext';
import { EmptyState } from '../../components/EmptyState';
import { StatusBadge } from '../../components/StatusBadge';
import { formatDate, formatDateTime, humanize } from '../../components/format';
import { showError, showSuccess } from '../../components/notify';
import { EXCEPTION_COLOR, STATUS_COLOR, duration, time, totalOt, type DayDetail } from './types';

interface Props {
  employeeId: string;
  date: string;
  onClose: () => void;
}

function Metric({ label, value, color }: { label: string; value: string; color?: string }) {
  return (
    <div>
      <Text size="xs" c="dimmed" tt="uppercase" fw={600} lts={0.4}>{label}</Text>
      <Text fw={600} c={color} className="tabular">{value}</Text>
    </div>
  );
}

function DirectionIcon({ direction }: { direction: string }) {
  if (direction === 'IN') return <IconLogin2 size={12} />;
  if (direction === 'OUT') return <IconLogout2 size={12} />;
  return <IconPoint size={12} />;
}

/** FR-050/051/055 day detail with raw events, adjustment history and a correction form. */
export function DayDetailDrawer({ employeeId, date, onClose }: Props) {
  const { can } = useAuth();
  const client = useQueryClient();
  const key = ['attendance-day', employeeId, date];
  const { data, isLoading } = useQuery({ queryKey: key, queryFn: () => api<DayDetail>(`/attendance/daily/${employeeId}/${date}`) });
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
  const ot = d ? totalOt(d) : 0;
  const otNote = d?.otRequiresApproval ? (d.otApproved ? 'approved' : d.otRejected ? 'rejected' : 'pending approval') : null;
  return (
    <Drawer opened onClose={onClose} title={<Text fw={600}>Attendance · {formatDate(date)}</Text>} position="right" size="lg">
      {isLoading && (
        <Stack>
          <Skeleton h={48} />
          <Skeleton h={120} />
          <Skeleton h={160} />
        </Stack>
      )}
      {data && (
        <Stack gap="lg">
          {d ? (
            <Paper withBorder p="md">
              <Group justify="space-between" wrap="wrap" gap="sm" mb="md">
                <Group gap="sm" wrap="nowrap">
                  <Avatar name={d.employeeName ?? d.employeeNumber ?? '?'} color="initials" radius="xl" />
                  <div>
                    <Text fw={600}>{d.employeeName}</Text>
                    <Text size="xs" c="dimmed">{d.employeeNumber} · {humanize(d.dayType)}</Text>
                  </div>
                </Group>
                <Group gap={6}>
                  <StatusBadge status={d.status} colors={STATUS_COLOR} size="lg" />
                  {d.manual && <Badge variant="outline" color="gray">Manual</Badge>}
                </Group>
              </Group>
              <SimpleGrid cols={{ base: 2, xs: 4 }} spacing="md" verticalSpacing="sm">
                <Metric label="In" value={time(d.firstIn)} />
                <Metric label="Out" value={time(d.lastOut)} />
                <Metric label="Worked" value={duration(d.workedMinutes)} />
                <Metric label="Scheduled" value={duration(d.scheduledMinutes)} />
                <Metric label="Late" value={duration(d.lateMinutes)} color={d.lateMinutes ? 'orange' : undefined} />
                <Metric label="Early leave" value={duration(d.earlyMinutes)} color={d.earlyMinutes ? 'orange' : undefined} />
                <Metric label="Short" value={duration(d.shortMinutes)} color={d.shortMinutes ? 'orange' : undefined} />
                <Metric label="Overtime" value={duration(ot)} color={ot ? 'teal' : undefined} />
              </SimpleGrid>
              {ot > 0 && (
                <Text size="xs" c="dimmed" mt="sm">
                  Overtime: workday {duration(d.otWorkdayMinutes)} · off day {duration(d.otOffdayMinutes)} · holiday {duration(d.otHolidayMinutes)}
                  {otNote && ` (${otNote})`}
                </Text>
              )}
              {d.exceptions.length > 0 && (
                <Group gap={6} mt="sm">
                  {d.exceptions.map((x) => <Badge key={x} variant="dot" color={EXCEPTION_COLOR[x] ?? 'gray'}>{humanize(x)}</Badge>)}
                </Group>
              )}
            </Paper>
          ) : (
            <Alert icon={<IconInfoCircle size={18} />} color="blue" variant="light">This day has not been processed yet.</Alert>
          )}

          <div>
            <Title order={5} mb="sm">Punches</Title>
            {data.events.length === 0 ? (
              <Paper withBorder><EmptyState compact title="No punches" description="No clock-in or clock-out events were recorded for this day." /></Paper>
            ) : (
              <Timeline bulletSize={22} lineWidth={2} active={data.events.length - 1}>
                {data.events.map((e) => (
                  <Timeline.Item
                    key={e.id}
                    bullet={<DirectionIcon direction={e.direction} />}
                    color={e.direction === 'IN' ? 'teal' : e.direction === 'OUT' ? 'brand' : 'gray'}
                    title={
                      <Group gap="xs">
                        <Text fw={600} className="tabular">{e.time.slice(11, 16)}</Text>
                        <Badge size="xs" variant="light" color={e.direction === 'IN' ? 'teal' : e.direction === 'OUT' ? 'brand' : 'gray'}>{humanize(e.direction)}</Badge>
                      </Group>
                    }
                  >
                    <Text size="xs" c="dimmed">
                      {e.time.slice(0, 10) !== date ? `${formatDate(e.time)} · ` : ''}
                      {e.deviceId ? `Device ${e.deviceId}` : 'No device'}
                    </Text>
                  </Timeline.Item>
                ))}
              </Timeline>
            )}
          </div>

          {data.adjustments.length > 0 && (
            <div>
              <Title order={5} mb="sm">Corrections</Title>
              <Stack gap="xs">
                {data.adjustments.map((a) => (
                  <Paper key={a.id} withBorder p="sm">
                    <Group justify="space-between" wrap="wrap" gap="xs">
                      <Group gap="xs" wrap="nowrap">
                        <Badge variant="outline" color="gray" size="sm">{humanize(a.type)}</Badge>
                        <Group gap={6} wrap="nowrap" className="tabular">
                          <Text size="sm" c="dimmed">{time(a.beforeIn)}–{time(a.beforeOut)}</Text>
                          <IconArrowRight size={14} />
                          <Text size="sm" fw={600}>{time(a.newIn)}–{time(a.newOut)}</Text>
                        </Group>
                      </Group>
                      <StatusBadge status={a.status} />
                    </Group>
                    <Text size="sm" mt={6}>{a.reason}</Text>
                    <Text size="xs" c="dimmed" mt={2}>
                      {formatDateTime(a.requestedAt)}
                      {a.overrideLock && ' · Payroll lock overridden'}
                    </Text>
                  </Paper>
                ))}
              </Stack>
            </div>
          )}

          {can('ATTENDANCE:ADD', 'ATTENDANCE:EDIT') && (
            <Paper withBorder p="md">
              <Group gap="xs" mb="xs">
                <IconPencil size={18} />
                <Title order={5}>Correct this day</Title>
              </Group>
              <Text size="sm" c="dimmed" mb="md">Enter the corrected clock-in and/or clock-out time (24-hour). Corrections may need approval.</Text>
              <Stack gap="sm">
                <SimpleGrid cols={{ base: 1, xs: 2 }} spacing="sm">
                  <TextInput label="IN (HH:mm)" placeholder="08:30" value={inTime} onChange={(e) => setInTime(e.currentTarget.value)} />
                  <TextInput label="OUT (HH:mm)" placeholder="17:30" value={outTime} onChange={(e) => setOutTime(e.currentTarget.value)} />
                </SimpleGrid>
                <Checkbox label="OUT is on the next day" description="For shifts that end after midnight" checked={outNextDay} onChange={(e) => setOutNextDay(e.currentTarget.checked)} />
                <TextInput label="Reason" withAsterisk placeholder="e.g. Forgot to punch out" value={reason} onChange={(e) => setReason(e.currentTarget.value)} />
                {can('ATTENDANCE:CONFIGURE') && (
                  <Checkbox color="red" label="Override payroll lock (audited)" checked={override} onChange={(e) => setOverride(e.currentTarget.checked)} />
                )}
                <Group justify="flex-end" mt="xs">
                  <Button variant="default" onClick={onClose}>Close</Button>
                  <Button leftSection={<IconSend size={16} />} onClick={() => void submit()} loading={busy} disabled={!reason.trim() || (!inTime && !outTime)}>Submit correction</Button>
                </Group>
              </Stack>
            </Paper>
          )}
        </Stack>
      )}
    </Drawer>
  );
}
