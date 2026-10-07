import { ActionIcon, Badge, Button, Checkbox, Group, Modal, NumberInput, Paper, Select, Stack, Table, Text, TextInput } from '@mantine/core';
import { IconPlus, IconTrash } from '@tabler/icons-react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { api } from '../../api/client';
import { useAuth } from '../../auth/AuthContext';
import { showError, showSuccess } from '../../components/notify';
import { money, type PayrollComponent, type SalaryStructure } from './types';

type LineDraft = { componentId: string | null; amount: number | string; rateOverride: number | string; multiplierOverride: number | string; excluded: boolean };

const num = (v: number | string) => (v === '' ? null : Number(v));

/** FR-082 effective-dated salary structures of one employee. */
export function SalaryStructurePanel({ employeeId, companyId }: { employeeId: string; companyId: string }) {
  const { can } = useAuth();
  const client = useQueryClient();
  const [editing, setEditing] = useState(false);
  const [effectiveFrom, setEffectiveFrom] = useState('');
  const [basic, setBasic] = useState<number | string>('');
  const [lines, setLines] = useState<LineDraft[]>([]);
  const { data: history = [] } = useQuery({
    queryKey: ['salary-structures', employeeId],
    queryFn: () => api<SalaryStructure[]>(`/employees/${employeeId}/salary-structures`),
  });
  const { data: components = [] } = useQuery({
    queryKey: ['payroll-components', companyId],
    queryFn: () => api<PayrollComponent[]>(`/companies/${companyId}/payroll-components`),
  });
  const selectable = components.filter((c) => c.active && !c.systemComponent && c.frequency === 'RECURRING');
  const byId = (id: string) => components.find((c) => c.id === id);
  const start = () => {
    const current = history.find((s) => s.effectiveTo === null);
    setEffectiveFrom('');
    setBasic(current?.basicSalary ?? '');
    setLines((current?.lines ?? []).map((l) => ({ componentId: l.componentId, amount: l.amount ?? '', rateOverride: l.rateOverride ?? '', multiplierOverride: l.multiplierOverride ?? '', excluded: l.excluded })));
    setEditing(true);
  };
  const setLine = (i: number, patch: Partial<LineDraft>) => setLines((ls) => ls.map((l, j) => (j === i ? { ...l, ...patch } : l)));
  const save = async () => {
    try {
      await api(`/employees/${employeeId}/salary-structures`, {
        method: 'POST',
        body: {
          effectiveFrom,
          basicSalary: Number(basic),
          lines: lines.filter((l) => l.componentId).map((l) => ({
            componentId: l.componentId, amount: num(l.amount), rateOverride: num(l.rateOverride), multiplierOverride: num(l.multiplierOverride), excluded: l.excluded,
          })),
        },
      });
      showSuccess('Salary structure saved');
      setEditing(false);
      await client.invalidateQueries({ queryKey: ['salary-structures', employeeId] });
    } catch (e) {
      showError(e);
    }
  };
  return (
    <Stack>
      {can('PAYROLL:EDIT') && (
        <Group justify="flex-end"><Button leftSection={<IconPlus size={16} />} onClick={start}>New structure</Button></Group>
      )}
      {history.length === 0 && <Text c="dimmed">No salary structure yet.</Text>}
      {history.map((s) => (
        <Paper key={s.id} withBorder p="md">
          <Group justify="space-between" mb="xs">
            <Text fw={600}>{s.effectiveFrom} → {s.effectiveTo ?? 'current'}</Text>
            {s.effectiveTo === null && <Badge color="green">Current</Badge>}
          </Group>
          <Table>
            <Table.Tbody>
              <Table.Tr><Table.Td>Basic salary</Table.Td><Table.Td ta="right">{money(s.basicSalary)}</Table.Td></Table.Tr>
              {s.lines.map((l) => (
                <Table.Tr key={l.componentId}>
                  <Table.Td>{byId(l.componentId)?.name ?? l.componentId}{l.excluded ? ' (excluded)' : ''}</Table.Td>
                  <Table.Td ta="right">
                    {[l.amount !== null ? money(l.amount) : null, l.rateOverride !== null ? `${l.rateOverride}%` : null, l.multiplierOverride !== null ? `×${l.multiplierOverride}` : null].filter(Boolean).join(' ')}
                  </Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        </Paper>
      ))}
      <Modal opened={editing} onClose={() => setEditing(false)} title="New salary structure" size="xl">
        <Stack>
          <Group grow>
            <TextInput label="Effective from" placeholder="YYYY-MM-DD" withAsterisk value={effectiveFrom} onChange={(e) => setEffectiveFrom(e.currentTarget.value)} />
            <NumberInput label="Basic salary" withAsterisk min={0} decimalScale={2} thousandSeparator="," value={basic} onChange={setBasic} />
          </Group>
          {lines.map((l, i) => {
            const c = l.componentId ? byId(l.componentId) : undefined;
            return (
              <Group key={i} align="flex-end" wrap="nowrap">
                <Select aria-label={`Component ${i + 1}`} label={i === 0 ? 'Component' : undefined} searchable style={{ flex: 2 }}
                  data={selectable.map((x) => ({ value: x.id, label: `${x.code} — ${x.name}` }))} value={l.componentId} onChange={(v) => setLine(i, { componentId: v })} />
                {(!c || c.calcType === 'FIXED') && (
                  <NumberInput aria-label={`Amount ${i + 1}`} label={i === 0 ? 'Amount' : undefined} min={0} decimalScale={2} style={{ flex: 1 }} value={l.amount} onChange={(v) => setLine(i, { amount: v })} />
                )}
                {c?.calcType === 'PERCENT_OF' && (
                  <NumberInput aria-label={`Rate ${i + 1}`} label="Rate % override" min={0} style={{ flex: 1 }} value={l.rateOverride} onChange={(v) => setLine(i, { rateOverride: v })} />
                )}
                {c?.calcType === 'PER_UNIT' && (
                  <NumberInput aria-label={`Multiplier ${i + 1}`} label="Multiplier override" min={0} style={{ flex: 1 }} value={l.multiplierOverride} onChange={(v) => setLine(i, { multiplierOverride: v })} />
                )}
                {c?.assignment === 'AUTOMATIC' && (
                  <Checkbox label="Exclude" checked={l.excluded} onChange={(e) => setLine(i, { excluded: e.currentTarget.checked })} />
                )}
                <ActionIcon variant="subtle" color="red" aria-label={`Remove component ${i + 1}`} onClick={() => setLines(lines.filter((_, j) => j !== i))}><IconTrash size={16} /></ActionIcon>
              </Group>
            );
          })}
          <Group justify="space-between">
            <Button variant="subtle" leftSection={<IconPlus size={16} />}
              onClick={() => setLines([...lines, { componentId: null, amount: '', rateOverride: '', multiplierOverride: '', excluded: false }])}>
              Add component
            </Button>
            <Button disabled={!effectiveFrom || basic === ''} onClick={() => void save()}>Save</Button>
          </Group>
        </Stack>
      </Modal>
    </Stack>
  );
}
