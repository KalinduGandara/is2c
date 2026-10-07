import { Badge, Button, Drawer, Group, Modal, MultiSelect, NativeSelect, ScrollArea, Select, Stack, Table, Text, TextInput, Title } from '@mantine/core';
import { DateInput } from '@mantine/dates';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import { api } from '../../api/client';
import { useAuth } from '../../auth/AuthContext';
import { WEEKDAYS } from '../../components/EntityForm';
import { DataTable } from '../../components/DataTable';
import { showError, showSuccess } from '../../components/notify';
import { dateRange, weekdayShort, type Revision, type Roster, type RosterDetail } from './types';
import { useShifts } from './useShifts';

const OFF = 'OFF';
const EMPTY = '';
const STATUS_COLORS = { DRAFT: 'gray', PUBLISHED: 'green', LOCKED: 'red' } as const;

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
  const { data: shifts = [] } = useShifts(data?.roster.companyId ?? null);
  const [pending, setPending] = useState<Record<string, string>>({});
  const [reasonOpen, setReasonOpen] = useState(false);
  const [reason, setReason] = useState('');
  const [bulkOpen, setBulkOpen] = useState(false);
  const [bulk, setBulk] = useState<{ employeeIds: string[]; from: string | null; to: string | null; weekdays: string[]; value: string }>({ employeeIds: [], from: null, to: null, weekdays: [], value: OFF });
  const [copySource, setCopySource] = useState<string | null>(null);
  const [revisionsOpen, setRevisionsOpen] = useState(false);
  const { data: revisions = [] } = useQuery({
    queryKey: ['roster-revisions', rosterId],
    queryFn: () => api<Revision[]>(`/rosters/${rosterId}/revisions`),
    enabled: revisionsOpen,
  });

  const saved = useMemo(() => {
    const map: Record<string, string> = {};
    for (const e of data?.entries ?? []) map[`${e.employeeId}|${e.date}`] = e.off ? OFF : (e.shiftId ?? OFF);
    return map;
  }, [data]);

  if (!data) return null;
  const { roster, employees } = data;
  const dates = dateRange(roster.startDate, roster.endDate);
  const editable = roster.status !== 'LOCKED' && can('ROSTER:EDIT');
  const options = [{ value: EMPTY, label: '' }, { value: OFF, label: 'OFF' }, ...shifts.filter((s) => s.active).map((s) => ({ value: s.id, label: s.code }))];
  const codeOf = (v: string | undefined) => (v === undefined || v === EMPTY ? '' : v === OFF ? 'OFF' : (shifts.find((s) => s.id === v)?.code ?? '?'));
  const refresh = async () => {
    await client.invalidateQueries({ queryKey: key });
    await client.invalidateQueries({ queryKey: ['rosters'] });
  };

  const save = async (why?: string) => {
    const entries = Object.entries(pending).map(([k, v]) => {
      const [employeeId, date] = k.split('|');
      return { employeeId, date, shiftId: v === OFF || v === EMPTY ? null : v, off: v === OFF, clear: v === EMPTY };
    });
    try {
      await api(`/rosters/${rosterId}/entries`, { method: 'PUT', body: { entries, reason: why ?? null } });
      setPending({});
      setReasonOpen(false);
      setReason('');
      showSuccess();
      await refresh();
    } catch (e) {
      showError(e);
    }
  };

  const action = async (path: string, body?: unknown, message?: string) => {
    try {
      await api(`/rosters/${rosterId}/${path}`, { method: 'POST', body });
      showSuccess(message);
      await refresh();
    } catch (e) {
      showError(e);
    }
  };

  const validate = async () => {
    try {
      const conflicts = await api<{ employeeNumber: string; date: string; message: string }[]>(`/rosters/${rosterId}/validate`, { method: 'POST' });
      if (conflicts.length === 0) showSuccess('No conflicts');
      else showError(new Error(conflicts.map((c) => `${c.employeeNumber} ${c.date}: ${c.message}`).join('\n')));
    } catch (e) {
      showError(e);
    }
  };

  return (
    <Stack>
      <Group justify="space-between" wrap="wrap">
        <Group>
          <Title order={3}>{roster.name}</Title>
          <Badge color={STATUS_COLORS[roster.status]}>{roster.status}</Badge>
          {roster.revision > 0 && <Text size="sm" c="dimmed">Revision {roster.revision}</Text>}
        </Group>
        <Group gap="xs" wrap="wrap">
          <Button variant="default" onClick={onClose}>Back</Button>
          {editable && <Button variant="light" onClick={() => setBulkOpen(true)}>Bulk assign</Button>}
          {editable && roster.status === 'DRAFT' && (
            <Group gap={4}>
              <Select placeholder="Copy from…" size="sm" w={180} data={otherRosters.filter((r) => r.id !== rosterId && r.periodType === roster.periodType).map((r) => ({ value: r.id, label: r.name }))} value={copySource} onChange={setCopySource} />
              <Button variant="light" disabled={!copySource} onClick={() => void action(`copy-from/${copySource}`, undefined, 'Copied')}>Copy</Button>
            </Group>
          )}
          <Button variant="light" onClick={() => void validate()}>Validate</Button>
          {roster.status === 'DRAFT' && can('ROSTER:APPROVE') && <Button onClick={() => void action('publish', undefined, 'Published')}>Publish</Button>}
          {roster.status === 'PUBLISHED' && can('ROSTER:APPROVE') && <Button color="red" variant="light" onClick={() => void action('lock', undefined, 'Locked')}>Lock</Button>}
          {roster.status === 'LOCKED' && can('ROSTER:CONFIGURE') && <Button variant="light" onClick={() => void action('unlock', { reason: window.prompt('Reason for unlocking') ?? '' }, 'Unlocked')}>Unlock</Button>}
          {roster.status !== 'DRAFT' && <Button variant="subtle" onClick={() => setRevisionsOpen(true)}>Revisions</Button>}
          {editable && (
            <Button disabled={Object.keys(pending).length === 0} onClick={() => (roster.status === 'PUBLISHED' ? setReasonOpen(true) : void save())}>
              Save ({Object.keys(pending).length})
            </Button>
          )}
        </Group>
      </Group>
      <ScrollArea type="auto">
        <Table withTableBorder withColumnBorders striped miw={200 + dates.length * 72}>
          <Table.Thead>
            <Table.Tr>
              <Table.Th miw={200}>Employee</Table.Th>
              {dates.map((d) => (
                <Table.Th key={d} ta="center" miw={72}>
                  <Text size="xs">{weekdayShort(d)}</Text>
                  <Text size="xs" c="dimmed">{d.slice(5)}</Text>
                </Table.Th>
              ))}
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {employees.map((e) => (
              <Table.Tr key={e.id}>
                <Table.Td><Text size="sm">{e.employeeNumber}</Text><Text size="xs" c="dimmed">{e.name}</Text></Table.Td>
                {dates.map((d) => {
                  const k = `${e.id}|${d}`;
                  const value = pending[k] ?? saved[k] ?? EMPTY;
                  const changed = k in pending;
                  return (
                    <Table.Td key={d} p={2} bg={changed ? 'yellow.1' : undefined}>
                      {editable ? (
                        <NativeSelect aria-label={`${e.employeeNumber} ${d}`} size="xs" data={options} value={value}
                          onChange={(ev) => {
                            const v = ev.currentTarget.value;
                            setPending((p) => {
                              const next = { ...p };
                              if ((saved[k] ?? EMPTY) === v) delete next[k];
                              else next[k] = v;
                              return next;
                            });
                          }} />
                      ) : (
                        <Text size="xs" ta="center">{codeOf(value)}</Text>
                      )}
                    </Table.Td>
                  );
                })}
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>
      </ScrollArea>
      <Modal opened={reasonOpen} onClose={() => setReasonOpen(false)} title="Reason for changing a published roster">
        <Stack>
          <TextInput label="Reason" value={reason} onChange={(e) => setReason(e.currentTarget.value)} />
          <Group justify="flex-end"><Button disabled={!reason.trim()} onClick={() => void save(reason)}>Save</Button></Group>
        </Stack>
      </Modal>
      <Modal opened={bulkOpen} onClose={() => setBulkOpen(false)} title="Bulk assign">
        <Stack>
          <MultiSelect label="Employees" searchable data={employees.map((e) => ({ value: e.id, label: `${e.employeeNumber} — ${e.name}` }))} value={bulk.employeeIds} onChange={(v) => setBulk({ ...bulk, employeeIds: v })} />
          <Group grow>
            <DateInput label="From" valueFormat="YYYY-MM-DD" minDate={roster.startDate} maxDate={roster.endDate} value={bulk.from} onChange={(v) => setBulk({ ...bulk, from: v ? String(v) : null })} />
            <DateInput label="To" valueFormat="YYYY-MM-DD" minDate={roster.startDate} maxDate={roster.endDate} value={bulk.to} onChange={(v) => setBulk({ ...bulk, to: v ? String(v) : null })} />
          </Group>
          <MultiSelect label="Only on weekdays (optional)" data={WEEKDAYS} value={bulk.weekdays} onChange={(v) => setBulk({ ...bulk, weekdays: v })} />
          <Select label="Shift" data={options.filter((o) => o.value !== EMPTY)} value={bulk.value} allowDeselect={false} onChange={(v) => setBulk({ ...bulk, value: v ?? OFF })} />
          {roster.status === 'PUBLISHED' && <TextInput label="Reason" value={reason} onChange={(e) => setReason(e.currentTarget.value)} />}
          <Group justify="flex-end">
            <Button disabled={!bulk.from || !bulk.to || bulk.employeeIds.length === 0} onClick={async () => {
              await action('bulk-assign', { employeeIds: bulk.employeeIds, from: bulk.from, to: bulk.to, weekdays: bulk.weekdays, shiftId: bulk.value === OFF ? null : bulk.value, off: bulk.value === OFF, reason: reason || null }, 'Assigned');
              setBulkOpen(false);
            }}>Assign</Button>
          </Group>
        </Stack>
      </Modal>
      <Drawer opened={revisionsOpen} onClose={() => setRevisionsOpen(false)} title="Revision history" position="right" size="lg">
        <DataTable
          rows={revisions}
          rowKey={(r) => r.id}
          columns={[
            { key: 'rev', header: 'Rev', render: (r) => r.revision },
            { key: 'emp', header: 'Employee', render: (r) => employees.find((e) => e.id === r.employeeId)?.employeeNumber ?? r.employeeId },
            { key: 'date', header: 'Date', render: (r) => r.date },
            { key: 'chg', header: 'Change', render: (r) => `${r.oldValue ?? '—'} → ${r.newValue ?? '—'}` },
            { key: 'reason', header: 'Reason', render: (r) => r.reason },
            { key: 'at', header: 'When', render: (r) => new Date(r.changedAt).toLocaleString() },
          ]}
        />
      </Drawer>
    </Stack>
  );
}
