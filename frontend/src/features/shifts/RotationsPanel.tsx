import { ActionIcon, Badge, Button, Drawer, Group, Modal, MultiSelect, Paper, Select, SimpleGrid, Stack, Text, TextInput, Tooltip } from '@mantine/core';
import { DateInput } from '@mantine/dates';
import { IconPencil, IconPlus, IconRepeat, IconTrash, IconUsersPlus } from '@tabler/icons-react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { api, type Page } from '../../api/client';
import { useAuth } from '../../auth/AuthContext';
import { DataTable } from '../../components/DataTable';
import { showError, showSuccess } from '../../components/notify';
import { shiftTint, type RotationPattern } from './types';
import { useShifts } from './useShifts';

const OFF = '__OFF__';

/** FR-031 rotation patterns and assignment of employees. */
export function RotationsPanel({ companyId }: { companyId: string }) {
  const { can } = useAuth();
  const client = useQueryClient();
  const { data: shifts = [] } = useShifts(companyId);
  const { data: patterns = [], isLoading } = useQuery({
    queryKey: ['rotation-patterns', companyId],
    queryFn: () => api<RotationPattern[]>(`/companies/${companyId}/rotation-patterns`),
  });
  const { data: employees } = useQuery({
    queryKey: ['employee-options', companyId],
    queryFn: () => api<Page<{ id: string; employeeNumber: string; firstName: string; lastName: string }>>('/employees', { query: { companyId, status: 'ACTIVE', size: 200 } }),
    enabled: can('EMPLOYEE:VIEW'),
  });
  const [editing, setEditing] = useState<{ id?: string; name: string; days: string[]; version?: number } | null>(null);
  const [assigning, setAssigning] = useState<RotationPattern | null>(null);
  const [busy, setBusy] = useState(false);
  const [assignment, setAssignment] = useState<{ employeeIds: string[]; startDate: string | null; endDate: string | null }>({ employeeIds: [], startDate: null, endDate: null });

  const shiftOptions = [{ value: OFF, label: 'OFF' }, ...shifts.filter((s) => s.active).map((s) => ({ value: s.id, label: s.code }))];
  const codeOf = (id: string | null) => (id ? (shifts.find((s) => s.id === id)?.code ?? '?') : 'OFF');
  const chip = (id: string | null, key: number | string) => (
    <Badge key={key} radius="sm" variant="light" color="gray" style={id ? shiftTint(shifts, id) : undefined}>{codeOf(id)}</Badge>
  );
  const edit = (r: RotationPattern) => setEditing({ id: r.id, name: r.name, days: r.days.map((d) => d ?? OFF), version: r.version });

  const save = async () => {
    if (!editing) return;
    const body = { name: editing.name, days: editing.days.map((d) => (d === OFF ? null : d)), version: editing.version };
    setBusy(true);
    try {
      if (editing.id) await api(`/rotation-patterns/${editing.id}`, { method: 'PUT', body });
      else await api(`/companies/${companyId}/rotation-patterns`, { method: 'POST', body });
      showSuccess();
      setEditing(null);
      await client.invalidateQueries({ queryKey: ['rotation-patterns'] });
    } catch (e) {
      showError(e);
    } finally {
      setBusy(false);
    }
  };

  const assign = async () => {
    if (!assigning) return;
    setBusy(true);
    try {
      await api(`/rotation-patterns/${assigning.id}/assignments`, { method: 'POST', body: assignment });
      showSuccess('Rotation assigned');
      setAssigning(null);
      setAssignment({ employeeIds: [], startDate: null, endDate: null });
    } catch (e) {
      showError(e);
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
      <Group justify="space-between" mb="sm" gap="sm" wrap="wrap">
        <Text size="sm" c="dimmed">Repeating cycles of shifts and days off, assigned to employees from a start date.</Text>
        {can('SHIFT:ADD') && (
          <Button leftSection={<IconPlus size={16} />} onClick={() => setEditing({ name: '', days: [OFF] })}>Create pattern</Button>
        )}
      </Group>
      <DataTable
        rows={patterns}
        loading={isLoading}
        rowKey={(r) => r.id}
        onRowClick={can('SHIFT:EDIT') ? edit : undefined}
        rowLabel={(r) => `Edit pattern ${r.name}`}
        emptyTitle="No rotation patterns yet"
        emptyDescription="Create a pattern such as DAY, DAY, NIGHT, OFF to rotate employees automatically."
        emptyIcon={<IconRepeat size={20} />}
        columns={[
          { key: 'name', header: 'Name', render: (r) => <Text size="sm" fw={600}>{r.name}</Text> },
          { key: 'len', header: 'Cycle', render: (r) => <Badge variant="outline" color="gray">{r.days.length} {r.days.length === 1 ? 'day' : 'days'}</Badge> },
          { key: 'days', header: 'Days', render: (r) => <Group gap={4}>{r.days.map((d, i) => chip(d, i))}</Group> },
          {
            key: 'actions', header: '', align: 'right', width: 90,
            render: (r) => (
              <Group gap={4} wrap="nowrap" justify="flex-end" onClick={(e) => e.stopPropagation()}>
                {can('SHIFT:EDIT') && (
                  <Tooltip label="Edit">
                    <ActionIcon variant="subtle" color="gray" aria-label="Edit" onClick={() => edit(r)}><IconPencil size={16} /></ActionIcon>
                  </Tooltip>
                )}
                {can('ROSTER:EDIT') && (
                  <Tooltip label="Assign employees">
                    <ActionIcon variant="subtle" aria-label="Assign employees" onClick={() => setAssigning(r)}><IconUsersPlus size={16} /></ActionIcon>
                  </Tooltip>
                )}
              </Group>
            ),
          },
        ]}
      />
      <Drawer opened={editing !== null} onClose={() => setEditing(null)} title={<Text fw={600}>{editing?.id ? 'Edit pattern' : 'Create pattern'}</Text>} position="right" size="md">
        {editing && (
          <Stack>
            <TextInput label="Name" withAsterisk placeholder="e.g. 4-on 2-off" value={editing.name} onChange={(e) => setEditing({ ...editing, name: e.currentTarget.value })} />
            <Paper withBorder p="sm">
              <Group justify="space-between" mb="xs">
                <Text size="sm" fw={600}>Cycle days</Text>
                <Badge variant="outline" color="gray">{editing.days.length} {editing.days.length === 1 ? 'day' : 'days'}</Badge>
              </Group>
              <Group gap={4} mb="sm">{editing.days.map((d, i) => chip(d === OFF ? null : d, i))}</Group>
              <Stack gap={6}>
                {editing.days.map((d, i) => (
                  <Group key={i} gap="xs" wrap="nowrap">
                    <Text w={56} size="sm" c="dimmed">Day {i + 1}</Text>
                    <Select aria-label={`Day ${i + 1}`} data={shiftOptions} value={d} allowDeselect={false} onChange={(v) => setEditing({ ...editing, days: editing.days.map((x, j) => (j === i ? (v ?? OFF) : x)) })} style={{ flex: 1 }} />
                    <Tooltip label="Remove day">
                      <ActionIcon variant="subtle" color="red" aria-label="Remove day" disabled={editing.days.length === 1} onClick={() => setEditing({ ...editing, days: editing.days.filter((_, j) => j !== i) })}><IconTrash size={14} /></ActionIcon>
                    </Tooltip>
                  </Group>
                ))}
              </Stack>
              <Button mt="sm" variant="light" size="xs" leftSection={<IconPlus size={14} />} disabled={editing.days.length >= 62} onClick={() => setEditing({ ...editing, days: [...editing.days, OFF] })}>Add day</Button>
            </Paper>
            <Group justify="flex-end">
              <Button variant="default" onClick={() => setEditing(null)}>Cancel</Button>
              <Button loading={busy} disabled={!editing.name.trim()} onClick={() => void save()}>Save</Button>
            </Group>
          </Stack>
        )}
      </Drawer>
      <Modal opened={assigning !== null} onClose={() => setAssigning(null)} title={<Text fw={600}>Assign “{assigning?.name ?? ''}”</Text>}>
        <Stack>
          {assigning && <Group gap={4}>{assigning.days.map((d, i) => chip(d, i))}</Group>}
          <MultiSelect label="Employees" placeholder="Choose employees" searchable data={(employees?.content ?? []).map((e) => ({ value: e.id, label: `${e.employeeNumber} — ${e.firstName} ${e.lastName}` }))}
            value={assignment.employeeIds} onChange={(v) => setAssignment({ ...assignment, employeeIds: v })} />
          <SimpleGrid cols={{ base: 1, xs: 2 }}>
            <DateInput label="Start date (cycle day 1)" withAsterisk valueFormat="D MMM YYYY" value={assignment.startDate} onChange={(v) => setAssignment({ ...assignment, startDate: v ? String(v) : null })} />
            <DateInput label="End date (optional)" valueFormat="D MMM YYYY" clearable minDate={assignment.startDate ?? undefined} value={assignment.endDate} onChange={(v) => setAssignment({ ...assignment, endDate: v ? String(v) : null })} />
          </SimpleGrid>
          <Group justify="flex-end">
            <Button variant="default" onClick={() => setAssigning(null)}>Cancel</Button>
            <Button leftSection={<IconUsersPlus size={16} />} loading={busy} onClick={() => void assign()} disabled={!assignment.startDate || assignment.employeeIds.length === 0}>Assign</Button>
          </Group>
        </Stack>
      </Modal>
    </>
  );
}
