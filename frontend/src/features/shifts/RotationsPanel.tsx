import { ActionIcon, Badge, Button, Drawer, Group, Modal, MultiSelect, Select, Stack, Text, TextInput } from '@mantine/core';
import { DateInput } from '@mantine/dates';
import { IconPlus, IconTrash, IconUsersPlus } from '@tabler/icons-react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { api, type Page } from '../../api/client';
import { useAuth } from '../../auth/AuthContext';
import { DataTable } from '../../components/DataTable';
import { showError, showSuccess } from '../../components/notify';
import type { RotationPattern } from './types';
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
  const [assignment, setAssignment] = useState<{ employeeIds: string[]; startDate: string | null; endDate: string | null }>({ employeeIds: [], startDate: null, endDate: null });

  const shiftOptions = [{ value: OFF, label: 'OFF' }, ...shifts.filter((s) => s.active).map((s) => ({ value: s.id, label: s.code }))];
  const codeOf = (id: string | null) => (id ? (shifts.find((s) => s.id === id)?.code ?? '?') : 'OFF');

  const save = async () => {
    if (!editing) return;
    const body = { name: editing.name, days: editing.days.map((d) => (d === OFF ? null : d)), version: editing.version };
    try {
      if (editing.id) await api(`/rotation-patterns/${editing.id}`, { method: 'PUT', body });
      else await api(`/companies/${companyId}/rotation-patterns`, { method: 'POST', body });
      showSuccess();
      setEditing(null);
      await client.invalidateQueries({ queryKey: ['rotation-patterns'] });
    } catch (e) {
      showError(e);
    }
  };

  const assign = async () => {
    if (!assigning) return;
    try {
      await api(`/rotation-patterns/${assigning.id}/assignments`, { method: 'POST', body: assignment });
      showSuccess('Rotation assigned');
      setAssigning(null);
    } catch (e) {
      showError(e);
    }
  };

  return (
    <>
      {can('SHIFT:ADD') && (
        <Group justify="flex-end" mb="sm">
          <Button leftSection={<IconPlus size={16} />} onClick={() => setEditing({ name: '', days: [OFF] })}>Create pattern</Button>
        </Group>
      )}
      <DataTable
        rows={patterns}
        loading={isLoading}
        rowKey={(r) => r.id}
        columns={[
          { key: 'name', header: 'Name', render: (r) => r.name },
          { key: 'len', header: 'Cycle', render: (r) => `${r.days.length} days` },
          { key: 'days', header: 'Days', render: (r) => <Group gap={4}>{r.days.map((d, i) => <Badge key={i} variant={d ? 'light' : 'outline'} color={d ? 'indigo' : 'gray'}>{codeOf(d)}</Badge>)}</Group> },
          {
            key: 'actions', header: '',
            render: (r) => (
              <Group gap={4} wrap="nowrap">
                {can('SHIFT:EDIT') && <Button size="xs" variant="subtle" onClick={() => setEditing({ id: r.id, name: r.name, days: r.days.map((d) => d ?? OFF), version: r.version })}>Edit</Button>}
                {can('ROSTER:EDIT') && <ActionIcon variant="subtle" aria-label="Assign employees" onClick={() => setAssigning(r)}><IconUsersPlus size={16} /></ActionIcon>}
              </Group>
            ),
          },
        ]}
      />
      <Drawer opened={editing !== null} onClose={() => setEditing(null)} title={editing?.id ? 'Edit pattern' : 'Create pattern'} position="right">
        {editing && (
          <Stack>
            <TextInput label="Name" withAsterisk value={editing.name} onChange={(e) => setEditing({ ...editing, name: e.currentTarget.value })} />
            <Text size="sm" fw={500}>Cycle days</Text>
            {editing.days.map((d, i) => (
              <Group key={i} gap="xs">
                <Text w={60} size="sm">Day {i + 1}</Text>
                <Select aria-label={`Day ${i + 1}`} data={shiftOptions} value={d} allowDeselect={false} onChange={(v) => setEditing({ ...editing, days: editing.days.map((x, j) => (j === i ? (v ?? OFF) : x)) })} w={160} />
                <ActionIcon variant="subtle" color="red" aria-label="Remove day" disabled={editing.days.length === 1} onClick={() => setEditing({ ...editing, days: editing.days.filter((_, j) => j !== i) })}><IconTrash size={14} /></ActionIcon>
              </Group>
            ))}
            <Button variant="light" disabled={editing.days.length >= 62} onClick={() => setEditing({ ...editing, days: [...editing.days, OFF] })}>Add day</Button>
            <Group justify="flex-end"><Button onClick={() => void save()}>Save</Button></Group>
          </Stack>
        )}
      </Drawer>
      <Modal opened={assigning !== null} onClose={() => setAssigning(null)} title={`Assign ${assigning?.name ?? ''}`}>
        <Stack>
          <MultiSelect label="Employees" searchable data={(employees?.content ?? []).map((e) => ({ value: e.id, label: `${e.employeeNumber} — ${e.firstName} ${e.lastName}` }))}
            value={assignment.employeeIds} onChange={(v) => setAssignment({ ...assignment, employeeIds: v })} />
          <DateInput label="Start date (cycle day 1)" valueFormat="YYYY-MM-DD" value={assignment.startDate} onChange={(v) => setAssignment({ ...assignment, startDate: v ? String(v) : null })} />
          <DateInput label="End date (optional)" valueFormat="YYYY-MM-DD" clearable value={assignment.endDate} onChange={(v) => setAssignment({ ...assignment, endDate: v ? String(v) : null })} />
          <Group justify="flex-end"><Button onClick={() => void assign()} disabled={!assignment.startDate || assignment.employeeIds.length === 0}>Assign</Button></Group>
        </Stack>
      </Modal>
    </>
  );
}
