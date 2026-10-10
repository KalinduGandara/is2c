import { ActionIcon, Badge, Button, Checkbox, Grid, Group, Modal, NumberInput, Paper, Select, SimpleGrid, Skeleton, Stack, Table, Text, Title, Tooltip } from '@mantine/core';
import { DateInput } from '@mantine/dates';
import { IconCash, IconPlus, IconTrash } from '@tabler/icons-react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { api } from '../../api/client';
import { useAuth } from '../../auth/AuthContext';
import { EmptyState } from '../../components/EmptyState';
import { formatDate } from '../../components/format';
import { showError, showSuccess } from '../../components/notify';
import { KIND_COLOR, KIND_LABEL, money, type PayrollComponent, type SalaryStructure } from './types';

type LineDraft = { componentId: string | null; amount: number | string; rateOverride: number | string; multiplierOverride: number | string; excluded: boolean };

const num = (v: number | string) => (v === '' ? null : Number(v));

/** FR-082 effective-dated salary structures of one employee. */
export function SalaryStructurePanel({ employeeId, companyId }: { employeeId: string; companyId: string }) {
  const { can } = useAuth();
  const client = useQueryClient();
  const [editing, setEditing] = useState(false);
  const [saving, setSaving] = useState(false);
  const [effectiveFrom, setEffectiveFrom] = useState('');
  const [basic, setBasic] = useState<number | string>('');
  const [lines, setLines] = useState<LineDraft[]>([]);
  const { data: history = [], isLoading } = useQuery({
    queryKey: ['salary-structures', employeeId],
    queryFn: () => api<SalaryStructure[]>(`/employees/${employeeId}/salary-structures`),
  });
  const { data: components = [] } = useQuery({
    queryKey: ['payroll-components', companyId],
    queryFn: () => api<PayrollComponent[]>(`/companies/${companyId}/payroll-components`),
  });
  const selectable = components.filter((c) => c.active && !c.systemComponent && c.frequency === 'RECURRING');
  const byId = (id: string) => components.find((c) => c.id === id);
  const sorted = [...history].sort((a, b) => b.effectiveFrom.localeCompare(a.effectiveFrom));
  const start = () => {
    const current = history.find((s) => s.effectiveTo === null);
    setEffectiveFrom('');
    setBasic(current?.basicSalary ?? '');
    setLines((current?.lines ?? []).map((l) => ({ componentId: l.componentId, amount: l.amount ?? '', rateOverride: l.rateOverride ?? '', multiplierOverride: l.multiplierOverride ?? '', excluded: l.excluded })));
    setEditing(true);
  };
  const setLine = (i: number, patch: Partial<LineDraft>) => setLines((ls) => ls.map((l, j) => (j === i ? { ...l, ...patch } : l)));
  const save = async () => {
    setSaving(true);
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
    } finally {
      setSaving(false);
    }
  };
  return (
    <Stack>
      <Group justify="space-between" wrap="wrap" gap="xs">
        <div>
          <Title order={4}>Salary structure</Title>
          <Text size="sm" c="dimmed">Basic salary and recurring components, effective from a date. A new structure replaces the current one from its start date.</Text>
        </div>
        {can('PAYROLL:EDIT') && <Button leftSection={<IconPlus size={16} />} onClick={start}>New structure</Button>}
      </Group>
      {isLoading && <Skeleton h={160} radius="md" />}
      {!isLoading && history.length === 0 && (
        <Paper withBorder>
          <EmptyState icon={<IconCash size={28} stroke={1.5} />} title="No salary structure yet"
            description="Add a salary structure so this employee is included when payroll is processed." />
        </Paper>
      )}
      {sorted.map((s) => {
        const current = s.effectiveTo === null;
        return (
          <Paper key={s.id} withBorder p="lg" style={current ? { borderColor: 'var(--mantine-primary-color-filled)', borderWidth: 2 } : undefined}>
            <Group justify="space-between" mb="sm" wrap="wrap" gap="xs">
              <div>
                <Text size="xs" c="dimmed" tt="uppercase" fw={600}>Effective</Text>
                <Text fw={600}>{formatDate(s.effectiveFrom)} → {s.effectiveTo ? formatDate(s.effectiveTo) : 'present'}</Text>
              </div>
              {current ? <Badge color="green" size="lg">Current</Badge> : <Badge color="gray" variant="light">Past</Badge>}
            </Group>
            <Table className="app-table" verticalSpacing="xs">
              <Table.Tbody>
                <Table.Tr>
                  <Table.Td><Text fw={600} size="sm">Basic salary</Text></Table.Td>
                  <Table.Td />
                  <Table.Td ta="right"><Text fw={700} className="tabular">{money(s.basicSalary)}</Text></Table.Td>
                </Table.Tr>
                {s.lines.map((l) => {
                  const c = byId(l.componentId);
                  return (
                    <Table.Tr key={l.componentId} style={l.excluded ? { opacity: 0.6 } : undefined}>
                      <Table.Td>
                        <Text size="sm">{c?.name ?? l.componentId}</Text>
                        {l.excluded && <Text size="xs" c="dimmed">Excluded for this employee</Text>}
                      </Table.Td>
                      <Table.Td>{c && <Badge size="sm" variant="light" color={KIND_COLOR[c.kind]}>{KIND_LABEL[c.kind]}</Badge>}</Table.Td>
                      <Table.Td ta="right" className="tabular">
                        {[l.amount !== null ? money(l.amount) : null, l.rateOverride !== null ? `${l.rateOverride}%` : null, l.multiplierOverride !== null ? `×${l.multiplierOverride}` : null].filter(Boolean).join(' ') || '—'}
                      </Table.Td>
                    </Table.Tr>
                  );
                })}
              </Table.Tbody>
            </Table>
          </Paper>
        );
      })}
      <Modal opened={editing} onClose={() => setEditing(false)} title="New salary structure" size="xl">
        <Stack>
          <SimpleGrid cols={{ base: 1, sm: 2 }}>
            <DateInput label="Effective from" placeholder="YYYY-MM-DD" valueFormat="YYYY-MM-DD" withAsterisk value={effectiveFrom || null}
              onChange={(v) => setEffectiveFrom(v ? String(v) : '')} description="First day the new pay applies" />
            <NumberInput label="Basic salary" withAsterisk min={0} decimalScale={2} thousandSeparator="," value={basic} onChange={setBasic} description="Monthly amount" />
          </SimpleGrid>
          <div>
            <Text fw={600}>Components</Text>
            <Text size="sm" c="dimmed">Recurring allowances and deductions specific to this employee.</Text>
          </div>
          {lines.length === 0 && <Text size="sm" c="dimmed">No components — only the basic salary and automatic components apply.</Text>}
          {lines.map((l, i) => {
            const c = l.componentId ? byId(l.componentId) : undefined;
            return (
              <Grid key={i} align="flex-end" gap="xs">
                <Grid.Col span={{ base: 12, sm: 6 }}>
                  <Select aria-label={`Component ${i + 1}`} label={i === 0 ? 'Component' : undefined} searchable placeholder="Select component"
                    data={selectable.map((x) => ({ value: x.id, label: `${x.code} — ${x.name}` }))} value={l.componentId} onChange={(v) => setLine(i, { componentId: v })} />
                </Grid.Col>
                <Grid.Col span={{ base: 10, sm: 5 }}>
                  <Group gap="xs" wrap="nowrap" align="flex-end">
                    {(!c || c.calcType === 'FIXED') && (
                      <NumberInput aria-label={`Amount ${i + 1}`} label={i === 0 ? 'Amount' : undefined} min={0} decimalScale={2} thousandSeparator="," style={{ flex: 1 }}
                        value={l.amount} onChange={(v) => setLine(i, { amount: v })} />
                    )}
                    {c?.calcType === 'PERCENT_OF' && (
                      <NumberInput aria-label={`Rate ${i + 1}`} label="Rate % override" min={0} suffix="%" style={{ flex: 1 }} value={l.rateOverride} onChange={(v) => setLine(i, { rateOverride: v })} />
                    )}
                    {c?.calcType === 'PER_UNIT' && (
                      <NumberInput aria-label={`Multiplier ${i + 1}`} label="Multiplier override" min={0} style={{ flex: 1 }} value={l.multiplierOverride} onChange={(v) => setLine(i, { multiplierOverride: v })} />
                    )}
                    {c?.calcType === 'SLAB' && <Text size="sm" c="dimmed" style={{ flex: 1 }}>Calculated from slab table</Text>}
                    {c?.assignment === 'AUTOMATIC' && (
                      <Checkbox label="Exclude" mb={8} checked={l.excluded} onChange={(e) => setLine(i, { excluded: e.currentTarget.checked })} />
                    )}
                  </Group>
                </Grid.Col>
                <Grid.Col span={{ base: 2, sm: 1 }}>
                  <Tooltip label="Remove component">
                    <ActionIcon variant="subtle" color="red" size="lg" mb={2} aria-label={`Remove component ${i + 1}`} onClick={() => setLines(lines.filter((_, j) => j !== i))}>
                      <IconTrash size={16} />
                    </ActionIcon>
                  </Tooltip>
                </Grid.Col>
              </Grid>
            );
          })}
          <Group justify="space-between" mt="sm" wrap="wrap">
            <Button variant="light" leftSection={<IconPlus size={16} />}
              onClick={() => setLines([...lines, { componentId: null, amount: '', rateOverride: '', multiplierOverride: '', excluded: false }])}>
              Add component
            </Button>
            <Group gap="xs">
              <Button variant="default" onClick={() => setEditing(false)}>Cancel</Button>
              <Button loading={saving} disabled={!effectiveFrom || basic === ''} onClick={() => void save()}>Save</Button>
            </Group>
          </Group>
        </Stack>
      </Modal>
    </Stack>
  );
}
