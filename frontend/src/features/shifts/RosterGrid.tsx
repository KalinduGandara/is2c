import {
  Alert,
  Avatar,
  Badge,
  Box,
  Button,
  Drawer,
  Group,
  Modal,
  MultiSelect,
  NativeSelect,
  Paper,
  ScrollArea,
  Select,
  SimpleGrid,
  Skeleton,
  Stack,
  Table,
  Text,
  TextInput,
  Title,
  Tooltip,
} from '@mantine/core';
import { DateInput } from '@mantine/dates';
import { modals } from '@mantine/modals';
import {
  IconArrowLeft,
  IconCopy,
  IconDeviceFloppy,
  IconHistory,
  IconLock,
  IconLockOpen,
  IconRestore,
  IconSend,
  IconShieldCheck,
  IconUsersGroup,
} from '@tabler/icons-react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useMemo, useState, type CSSProperties } from 'react';
import { api } from '../../api/client';
import { useAuth } from '../../auth/AuthContext';
import { DataTable } from '../../components/DataTable';
import { WEEKDAYS } from '../../components/EntityForm';
import { StatusBadge } from '../../components/StatusBadge';
import { formatDate, formatDateTime } from '../../components/format';
import { showError, showSuccess } from '../../components/notify';
import { dateRange, dayMonth, hhmm, isWeekend, shiftTint, weekdayShort, type Revision, type Roster, type RosterDetail } from './types';
import { useShifts } from './useShifts';

const OFF = 'OFF';
const EMPTY = '';
const ROSTER_COLORS = { DRAFT: 'blue', PUBLISHED: 'green', LOCKED: 'red' };

const WEEKEND_BG = 'light-dark(var(--mantine-color-gray-0), var(--mantine-color-dark-6))';
const HOLIDAY_BG = 'var(--mantine-color-grape-light)';
const OFF_TINT = { background: 'light-dark(var(--mantine-color-gray-1), var(--mantine-color-dark-5))', color: 'var(--mantine-color-dimmed)' };
const STICKY: CSSProperties = { position: 'sticky', left: 0, zIndex: 2, background: 'var(--app-surface)', boxShadow: '1px 0 0 var(--app-border)' };

interface Props {
  rosterId: string;
  otherRosters: Roster[];
  onClose: () => void;
}

/** FR-036..039 roster grid (employees × dates) with pending edits saved in one request. */
export function RosterGrid({ rosterId, otherRosters, onClose }: Props) {
  const { can } = useAuth();
  const client = useQueryClient();
  const key = ['roster', rosterId];
  const { data } = useQuery({ queryKey: key, queryFn: () => api<RosterDetail>(`/rosters/${rosterId}`) });
  const companyId = data?.roster.companyId ?? null;
  const { data: shifts = [] } = useShifts(companyId);
  const years = data ? [...new Set([data.roster.startDate.slice(0, 4), data.roster.endDate.slice(0, 4)])] : [];
  const { data: holidays = [] } = useQuery({
    queryKey: ['holidays', companyId, 'roster', years.join(',')],
    queryFn: async () =>
      (await Promise.all(years.map((year) => api<{ date: string; name: string }[]>(`/companies/${companyId}/holidays`, { query: { year } })))).flat(),
    enabled: !!companyId,
  });
  const [pending, setPending] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState<string | null>(null);
  const [reasonOpen, setReasonOpen] = useState(false);
  const [reason, setReason] = useState('');
  const [unlockOpen, setUnlockOpen] = useState(false);
  const [unlockReason, setUnlockReason] = useState('');
  const [bulkOpen, setBulkOpen] = useState(false);
  const [bulk, setBulk] = useState<{ employeeIds: string[]; from: string | null; to: string | null; weekdays: string[]; value: string }>({ employeeIds: [], from: null, to: null, weekdays: [], value: OFF });
  const [copySource, setCopySource] = useState<string | null>(null);
  const [revisionsOpen, setRevisionsOpen] = useState(false);
  const { data: revisions = [], isLoading: revisionsLoading } = useQuery({
    queryKey: ['roster-revisions', rosterId],
    queryFn: () => api<Revision[]>(`/rosters/${rosterId}/revisions`),
    enabled: revisionsOpen,
  });

  const saved = useMemo(() => {
    const map: Record<string, string> = {};
    for (const e of data?.entries ?? []) map[`${e.employeeId}|${e.date}`] = e.off ? OFF : (e.shiftId ?? OFF);
    return map;
  }, [data]);

  if (!data)
    return (
      <Stack>
        <Skeleton h={72} radius="md" />
        <Skeleton h={320} radius="md" />
      </Stack>
    );
  const { roster, employees } = data;
  const dates = dateRange(roster.startDate, roster.endDate);
  const holidayOn = new Map(holidays.map((h) => [h.date, h.name]));
  const editable = roster.status !== 'LOCKED' && can('ROSTER:EDIT');
  const activeShifts = shifts.filter((s) => s.active);
  const options = [{ value: EMPTY, label: '' }, { value: OFF, label: 'OFF' }, ...activeShifts.map((s) => ({ value: s.id, label: s.code }))];
  const codeOf = (v: string | undefined) => (v === undefined || v === EMPTY ? '' : v === OFF ? 'OFF' : (shifts.find((s) => s.id === v)?.code ?? '?'));
  const tintOf = (v: string) => (v === OFF ? OFF_TINT : v === EMPTY ? undefined : shiftTint(shifts, v));
  const pendingCount = Object.keys(pending).length;
  const copyOptions = otherRosters.filter((r) => r.id !== rosterId && r.periodType === roster.periodType).map((r) => ({ value: r.id, label: r.name }));

  const refresh = async () => {
    await client.invalidateQueries({ queryKey: key });
    await client.invalidateQueries({ queryKey: ['rosters'] });
  };

  const save = async (why?: string) => {
    const entries = Object.entries(pending).map(([k, v]) => {
      const [employeeId, date] = k.split('|');
      return { employeeId, date, shiftId: v === OFF || v === EMPTY ? null : v, off: v === OFF, clear: v === EMPTY };
    });
    setBusy('save');
    try {
      await api(`/rosters/${rosterId}/entries`, { method: 'PUT', body: { entries, reason: why ?? null } });
      setPending({});
      setReasonOpen(false);
      setReason('');
      showSuccess();
      await refresh();
    } catch (e) {
      showError(e);
    } finally {
      setBusy(null);
    }
  };

  const action = async (path: string, body?: unknown, message?: string) => {
    setBusy(path);
    try {
      await api(`/rosters/${rosterId}/${path}`, { method: 'POST', body });
      showSuccess(message);
      await refresh();
      return true;
    } catch (e) {
      showError(e);
      return false;
    } finally {
      setBusy(null);
    }
  };

  const validate = async () => {
    setBusy('validate');
    try {
      const conflicts = await api<{ employeeNumber: string; date: string; message: string }[]>(`/rosters/${rosterId}/validate`, { method: 'POST' });
      if (conflicts.length === 0) showSuccess('No conflicts');
      else showError(new Error(conflicts.map((c) => `${c.employeeNumber} ${c.date}: ${c.message}`).join('\n')));
    } catch (e) {
      showError(e);
    } finally {
      setBusy(null);
    }
  };

  const confirmLock = () =>
    modals.openConfirmModal({
      title: 'Lock roster?',
      children: <Text size="sm">A locked roster can no longer be edited. Unlocking later requires a reason and is audited.</Text>,
      labels: { confirm: 'Lock roster', cancel: 'Cancel' },
      confirmProps: { color: 'red' },
      onConfirm: () => void action('lock', undefined, 'Roster locked'),
    });

  const confirmCopy = () =>
    modals.openConfirmModal({
      title: 'Copy assignments?',
      children: <Text size="sm">Shift assignments from “{copyOptions.find((o) => o.value === copySource)?.label}” will be copied into this roster.</Text>,
      labels: { confirm: 'Copy', cancel: 'Cancel' },
      onConfirm: () => void action(`copy-from/${copySource}`, undefined, 'Copied'),
    });

  const setCell = (k: string, v: string) =>
    setPending((p) => {
      const next = { ...p };
      if ((saved[k] ?? EMPTY) === v) delete next[k];
      else next[k] = v;
      return next;
    });

  return (
    <Stack gap="md">
      <Paper withBorder p="md">
        <Group justify="space-between" wrap="wrap" gap="sm" align="flex-start">
          <Group gap="sm" wrap="nowrap" align="flex-start" style={{ minWidth: 0 }}>
            <Button variant="default" onClick={onClose} leftSection={<IconArrowLeft size={16} />} style={{ flexShrink: 0 }}>Back</Button>
            <div style={{ minWidth: 0 }}>
              <Group gap="xs" wrap="wrap">
                <Title order={3}>{roster.name}</Title>
                <StatusBadge status={roster.status} colors={ROSTER_COLORS} />
                {roster.revision > 0 && <Badge variant="outline" color="gray">Revision {roster.revision}</Badge>}
              </Group>
              <Text size="sm" c="dimmed">
                {roster.periodType === 'WEEKLY' ? 'Weekly' : 'Monthly'} · {formatDate(roster.startDate)} – {formatDate(roster.endDate)} · {employees.length} employees
              </Text>
            </div>
          </Group>
          <Group gap="xs" wrap="wrap">
            {editable && (
              <Button variant="light" leftSection={<IconUsersGroup size={16} />} onClick={() => setBulkOpen(true)}>Bulk assign</Button>
            )}
            <Button variant="default" leftSection={<IconShieldCheck size={16} />} loading={busy === 'validate'} onClick={() => void validate()}>Validate</Button>
            {roster.status !== 'DRAFT' && (
              <Button variant="default" leftSection={<IconHistory size={16} />} onClick={() => setRevisionsOpen(true)}>Revisions</Button>
            )}
            {roster.status === 'PUBLISHED' && can('ROSTER:APPROVE') && (
              <Button color="red" variant="light" leftSection={<IconLock size={16} />} loading={busy === 'lock'} onClick={confirmLock}>Lock</Button>
            )}
            {roster.status === 'LOCKED' && can('ROSTER:CONFIGURE') && (
              <Button variant="light" leftSection={<IconLockOpen size={16} />} onClick={() => setUnlockOpen(true)}>Unlock</Button>
            )}
            {roster.status === 'DRAFT' && can('ROSTER:APPROVE') && (
              <Tooltip label={pendingCount ? 'Save your changes first' : 'Make this roster visible to employees'}>
                <Button leftSection={<IconSend size={16} />} disabled={pendingCount > 0} loading={busy === 'publish'} onClick={() => void action('publish', undefined, 'Roster published')}>Publish</Button>
              </Tooltip>
            )}
          </Group>
        </Group>
        {editable && roster.status === 'DRAFT' && copyOptions.length > 0 && (
          <Group gap="xs" mt="sm" wrap="wrap">
            <Select aria-label="Copy from roster" placeholder="Copy from another roster…" size="sm" w={{ base: '100%', xs: 240 }} leftSection={<IconCopy size={14} />} data={copyOptions} value={copySource} onChange={setCopySource} clearable />
            <Button variant="default" size="sm" disabled={!copySource} loading={busy?.startsWith('copy-from')} onClick={confirmCopy}>Copy</Button>
          </Group>
        )}
      </Paper>

      {roster.status === 'LOCKED' && (
        <Alert color="red" variant="light" icon={<IconLock size={18} />}>This roster is locked and read-only.</Alert>
      )}

      {editable && (
        <Paper
          withBorder
          p="xs"
          px="md"
          style={pendingCount ? { borderColor: 'var(--mantine-color-yellow-filled)', background: 'var(--mantine-color-yellow-light)' } : undefined}
        >
          <Group justify="space-between" wrap="wrap" gap="xs">
            <Text size="sm" c={pendingCount ? undefined : 'dimmed'} fw={pendingCount ? 600 : undefined}>
              {pendingCount
                ? `${pendingCount} unsaved ${pendingCount === 1 ? 'change' : 'changes'}${roster.status === 'PUBLISHED' ? ' — a reason is required to save' : ''}`
                : 'All changes saved. Pick a shift in any cell to edit.'}
            </Text>
            <Group gap="xs">
              {pendingCount > 0 && (
                <Button size="xs" variant="subtle" color="gray" leftSection={<IconRestore size={14} />} onClick={() => setPending({})}>Discard</Button>
              )}
              <Button size="xs" leftSection={<IconDeviceFloppy size={14} />} disabled={pendingCount === 0} loading={busy === 'save' && !reasonOpen}
                onClick={() => (roster.status === 'PUBLISHED' ? setReasonOpen(true) : void save())}>
                Save ({pendingCount})
              </Button>
            </Group>
          </Group>
        </Paper>
      )}

      <Paper withBorder style={{ overflow: 'hidden' }}>
        <ScrollArea type="auto">
          <Table withColumnBorders miw={180 + dates.length * 78} verticalSpacing={6} style={{ borderCollapse: 'separate', borderSpacing: 0 }}>
            <Table.Thead>
              <Table.Tr>
                <Table.Th style={{ ...STICKY, zIndex: 3, background: WEEKEND_BG }} miw={160}>
                  <Text size="xs" fw={600} c="dimmed" tt="uppercase">Employee</Text>
                </Table.Th>
                {dates.map((d) => {
                  const holiday = holidayOn.get(d);
                  const head = (
                    <Table.Th key={d} ta="center" miw={78} style={{ background: holiday ? HOLIDAY_BG : WEEKEND_BG }}>
                      <Text size="xs" fw={700} c={holiday ? 'grape' : isWeekend(d) ? 'dimmed' : undefined}>{weekdayShort(d)}</Text>
                      <Text size="xs" c="dimmed" style={{ whiteSpace: 'nowrap' }}>{dayMonth(d)}</Text>
                    </Table.Th>
                  );
                  return holiday ? <Tooltip key={d} label={`Holiday: ${holiday}`}>{head}</Tooltip> : head;
                })}
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {employees.length === 0 && (
                <Table.Tr>
                  <Table.Td colSpan={dates.length + 1}>
                    <Text size="sm" c="dimmed" ta="center" py="md">No employees in scope for this roster.</Text>
                  </Table.Td>
                </Table.Tr>
              )}
              {employees.map((e) => (
                <Table.Tr key={e.id}>
                  <Table.Td style={STICKY}>
                    <Group gap="xs" wrap="nowrap">
                      <Avatar name={e.name} color="initials" size="sm" radius="xl" />
                      <div style={{ minWidth: 0 }}>
                        <Text size="sm" fw={500} truncate>{e.name}</Text>
                        <Text size="xs" c="dimmed">{e.employeeNumber}</Text>
                      </div>
                    </Group>
                  </Table.Td>
                  {dates.map((d) => {
                    const k = `${e.id}|${d}`;
                    const value = pending[k] ?? saved[k] ?? EMPTY;
                    const changed = k in pending;
                    const tint = tintOf(value);
                    const bg = holidayOn.has(d) ? HOLIDAY_BG : isWeekend(d) ? WEEKEND_BG : undefined;
                    return (
                      <Table.Td key={d} p={4} style={{ background: bg }}>
                        {editable ? (
                          <NativeSelect
                            aria-label={`${e.employeeNumber} ${d}`}
                            size="xs"
                            data={options}
                            value={value}
                            onChange={(ev) => setCell(k, ev.currentTarget.value)}
                            styles={{
                              input: {
                                fontWeight: 600,
                                textAlign: 'center',
                                paddingInline: 6,
                                background: tint?.background,
                                color: tint?.color,
                                borderColor: changed ? 'var(--mantine-color-yellow-filled)' : undefined,
                                boxShadow: changed ? '0 0 0 1px var(--mantine-color-yellow-filled)' : undefined,
                              },
                            }}
                          />
                        ) : value ? (
                          <Box ta="center" py={2} style={{ borderRadius: 'var(--mantine-radius-sm)', ...tint }}>
                            <Text size="xs" fw={600}>{codeOf(value)}</Text>
                          </Box>
                        ) : (
                          <Text size="xs" ta="center" c="dimmed">·</Text>
                        )}
                      </Table.Td>
                    );
                  })}
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        </ScrollArea>
      </Paper>

      {activeShifts.length > 0 && (
        <Group gap="xs" wrap="wrap">
          <Text size="xs" c="dimmed" fw={600} tt="uppercase">Legend</Text>
          {activeShifts.map((s) => (
            <Badge key={s.id} variant="light" radius="sm" style={{ ...shiftTint(shifts, s.id), textTransform: 'none' }}>
              {s.code} · {s.type === 'FLEXIBLE' ? 'Flexible' : `${hhmm(s.s1Start)}–${hhmm(s.s1End)}`}
            </Badge>
          ))}
          <Badge variant="light" radius="sm" style={OFF_TINT}>OFF · Day off</Badge>
          {holidays.some((h) => dates.includes(h.date)) && <Badge variant="light" radius="sm" color="grape">Holiday</Badge>}
        </Group>
      )}

      <Modal opened={reasonOpen} onClose={() => setReasonOpen(false)} title="Reason for changing a published roster">
        <Stack>
          <Text size="sm" c="dimmed">The change is recorded as a new revision and employees see the updated schedule.</Text>
          <TextInput label="Reason" placeholder="e.g. Sick cover" data-autofocus value={reason} onChange={(e) => setReason(e.currentTarget.value)} />
          <Group justify="flex-end">
            <Button variant="default" onClick={() => setReasonOpen(false)}>Cancel</Button>
            <Button disabled={!reason.trim()} loading={busy === 'save'} onClick={() => void save(reason)}>Save</Button>
          </Group>
        </Stack>
      </Modal>

      <Modal opened={unlockOpen} onClose={() => setUnlockOpen(false)} title="Unlock roster?">
        <Stack>
          <Text size="sm" c="dimmed">Unlocking allows edits again. The reason is recorded in the audit log.</Text>
          <TextInput label="Reason for unlocking" withAsterisk data-autofocus value={unlockReason} onChange={(e) => setUnlockReason(e.currentTarget.value)} />
          <Group justify="flex-end">
            <Button variant="default" onClick={() => setUnlockOpen(false)}>Cancel</Button>
            <Button color="red" leftSection={<IconLockOpen size={16} />} disabled={!unlockReason.trim()} loading={busy === 'unlock'}
              onClick={async () => {
                if (await action('unlock', { reason: unlockReason }, 'Roster unlocked')) {
                  setUnlockOpen(false);
                  setUnlockReason('');
                }
              }}>
              Unlock
            </Button>
          </Group>
        </Stack>
      </Modal>

      <Modal opened={bulkOpen} onClose={() => setBulkOpen(false)} title="Bulk assign" size="lg">
        <Stack>
          <MultiSelect label="Employees" placeholder="Choose employees" searchable data={employees.map((e) => ({ value: e.id, label: `${e.employeeNumber} — ${e.name}` }))} value={bulk.employeeIds} onChange={(v) => setBulk({ ...bulk, employeeIds: v })} />
          <Button variant="subtle" size="compact-xs" w="fit-content" onClick={() => setBulk({ ...bulk, employeeIds: employees.map((e) => e.id) })}>Select all {employees.length}</Button>
          <SimpleGrid cols={{ base: 1, xs: 2 }}>
            <DateInput label="From" valueFormat="D MMM YYYY" minDate={roster.startDate} maxDate={roster.endDate} value={bulk.from} onChange={(v) => setBulk({ ...bulk, from: v ? String(v) : null })} />
            <DateInput label="To" valueFormat="D MMM YYYY" minDate={bulk.from ?? roster.startDate} maxDate={roster.endDate} value={bulk.to} onChange={(v) => setBulk({ ...bulk, to: v ? String(v) : null })} />
          </SimpleGrid>
          <MultiSelect label="Only on weekdays" description="Leave empty to assign every day in the range" placeholder="Every day" data={WEEKDAYS} value={bulk.weekdays} onChange={(v) => setBulk({ ...bulk, weekdays: v })} />
          <Select label="Shift" data={options.filter((o) => o.value !== EMPTY).map((o) => (o.value === OFF ? { value: OFF, label: 'OFF (day off)' } : o))} value={bulk.value} allowDeselect={false} onChange={(v) => setBulk({ ...bulk, value: v ?? OFF })} />
          {roster.status === 'PUBLISHED' && <TextInput label="Reason" withAsterisk value={reason} onChange={(e) => setReason(e.currentTarget.value)} />}
          <Group justify="flex-end">
            <Button variant="default" onClick={() => setBulkOpen(false)}>Cancel</Button>
            <Button
              leftSection={<IconUsersGroup size={16} />}
              loading={busy === 'bulk-assign'}
              disabled={!bulk.from || !bulk.to || bulk.employeeIds.length === 0 || (roster.status === 'PUBLISHED' && !reason.trim())}
              onClick={async () => {
                const ok = await action('bulk-assign', { employeeIds: bulk.employeeIds, from: bulk.from, to: bulk.to, weekdays: bulk.weekdays, shiftId: bulk.value === OFF ? null : bulk.value, off: bulk.value === OFF, reason: reason || null }, 'Assigned');
                if (ok) setBulkOpen(false);
              }}>
              Assign
            </Button>
          </Group>
        </Stack>
      </Modal>

      <Drawer opened={revisionsOpen} onClose={() => setRevisionsOpen(false)} title={<Text fw={600}>Revision history</Text>} position="right" size="xl">
        <DataTable
          bare
          minWidth={560}
          rows={revisions}
          loading={revisionsLoading}
          rowKey={(r) => r.id}
          emptyTitle="No revisions yet"
          emptyDescription="Changes made after publishing are listed here."
          emptyIcon={<IconHistory size={20} />}
          columns={[
            { key: 'rev', header: 'Rev', render: (r) => <Badge variant="outline" color="gray">{r.revision}</Badge> },
            { key: 'emp', header: 'Employee', render: (r) => employees.find((e) => e.id === r.employeeId)?.employeeNumber ?? r.employeeId },
            { key: 'date', header: 'Date', render: (r) => formatDate(r.date) },
            { key: 'chg', header: 'Change', render: (r) => <Text size="sm" style={{ whiteSpace: 'nowrap' }}>{r.oldValue ?? '—'} → <b>{r.newValue ?? '—'}</b></Text> },
            { key: 'reason', header: 'Reason', render: (r) => r.reason },
            { key: 'at', header: 'When', render: (r) => <Text size="sm" c="dimmed" style={{ whiteSpace: 'nowrap' }}>{formatDateTime(r.changedAt)}</Text> },
          ]}
        />
      </Drawer>
    </Stack>
  );
}
