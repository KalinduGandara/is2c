import { ActionIcon, Badge, Button, Code, Drawer, Group, NumberInput, Paper, ScrollArea, SimpleGrid, Stack, Table, Text, TextInput, Tooltip } from '@mantine/core';
import { IconPencil, IconPlus, IconStairs, IconTrash } from '@tabler/icons-react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { api } from '../../api/client';
import { useAuth } from '../../auth/AuthContext';
import { DataTable } from '../../components/DataTable';
import { showError, showSuccess } from '../../components/notify';
import { money, type SlabRow, type SlabTable } from './types';

type Draft = { id?: string; code: string; name: string; statutory: boolean; rows: { lowerBound: number | string; upperBound: number | string; ratePercent: number | string }[]; version?: number };

const toDraft = (t: SlabTable | null): Draft =>
  t
    ? { id: t.id, code: t.code, name: t.name, statutory: t.statutory, version: t.version, rows: t.rows.map((r) => ({ ...r, upperBound: r.upperBound ?? '' })) }
    : { code: '', name: '', statutory: false, rows: [{ lowerBound: 0, upperBound: '', ratePercent: 0 }] };

const rateRange = (t: SlabTable) => {
  if (t.rows.length === 0) return '—';
  const rates = t.rows.map((r) => r.ratePercent);
  const lo = Math.min(...rates);
  const hi = Math.max(...rates);
  return lo === hi ? `${lo}%` : `${lo}% – ${hi}%`;
};

/** FR-080/081 progressive slab tables (e.g. APIT): contiguous bands from 0, last band open-ended. */
export function SlabTablesPanel({ companyId }: { companyId: string }) {
  const { can } = useAuth();
  const client = useQueryClient();
  const editable = can('PAYROLL:CONFIGURE');
  const [draft, setDraft] = useState<Draft | null>(null);
  const [saving, setSaving] = useState(false);
  const { data = [], isLoading } = useQuery({ queryKey: ['slab-tables', companyId], queryFn: () => api<SlabTable[]>(`/companies/${companyId}/slab-tables`) });
  const setRow = (i: number, key: keyof Draft['rows'][number], value: number | string) =>
    setDraft((d) => d && { ...d, rows: d.rows.map((r, j) => (j === i ? { ...r, [key]: value } : r)) });
  const save = async () => {
    if (!draft) return;
    const rows: SlabRow[] = draft.rows.map((r) => ({ lowerBound: Number(r.lowerBound), upperBound: r.upperBound === '' ? null : Number(r.upperBound), ratePercent: Number(r.ratePercent) }));
    const body = { code: draft.code, name: draft.name, statutory: draft.statutory, rows, version: draft.version };
    setSaving(true);
    try {
      if (draft.id) await api(`/slab-tables/${draft.id}`, { method: 'PUT', body });
      else await api(`/companies/${companyId}/slab-tables`, { method: 'POST', body });
      showSuccess();
      setDraft(null);
      await client.invalidateQueries({ queryKey: ['slab-tables'] });
    } catch (e) {
      showError(e);
    } finally {
      setSaving(false);
    }
  };
  return (
    <>
      <Group justify="space-between" mb="sm" wrap="wrap" gap="xs">
        <Text size="sm" c="dimmed">Progressive rate bands (e.g. income tax) used by slab-type components.</Text>
        {editable && <Button leftSection={<IconPlus size={16} />} onClick={() => setDraft(toDraft(null))}>Create slab table</Button>}
      </Group>
      <DataTable
        rows={data}
        loading={isLoading}
        rowKey={(r) => r.id}
        onRowClick={editable ? (r) => setDraft(toDraft(r)) : undefined}
        rowLabel={(r) => `Edit ${r.name}`}
        emptyTitle="No slab tables yet"
        emptyDescription="Slab tables hold banded rates such as income tax."
        emptyIcon={<IconStairs size={22} stroke={1.5} />}
        columns={[
          { key: 'name', header: 'Table', render: (r) => (
            <Stack gap={0}>
              <Text size="sm" fw={600}>{r.name}</Text>
              <Code fz="xs" w="fit-content">{r.code}</Code>
            </Stack>
          ) },
          { key: 'bands', header: 'Bands', align: 'right', render: (r) => <Text size="sm" fw={600}>{r.rows.length}</Text> },
          { key: 'rates', header: 'Rates', render: (r) => <Text size="sm" className="tabular">{rateRange(r)}</Text> },
          { key: 'stat', header: '', render: (r) => r.statutory && <Badge size="sm" variant="outline">Statutory</Badge> },
          { key: 'edit', header: '', width: 50, render: (r) => editable && (
            <Tooltip label="Edit">
              <ActionIcon variant="subtle" aria-label="Edit" onClick={(e) => { e.stopPropagation(); setDraft(toDraft(r)); }}><IconPencil size={16} /></ActionIcon>
            </Tooltip>
          ) },
        ]}
      />
      <Drawer opened={draft !== null} onClose={() => setDraft(null)} title={draft?.id ? 'Edit slab table' : 'New slab table'} position="right" size="lg">
        {draft && (
          <Stack>
            <SimpleGrid cols={{ base: 1, sm: 2 }}>
              <TextInput label="Code" withAsterisk placeholder="e.g. APIT" value={draft.code} onChange={(e) => setDraft({ ...draft, code: e.currentTarget.value })} />
              <TextInput label="Name" withAsterisk placeholder="e.g. Income tax" value={draft.name} onChange={(e) => setDraft({ ...draft, name: e.currentTarget.value })} />
            </SimpleGrid>
            {draft.statutory && <Badge variant="outline" w="fit-content">Statutory table</Badge>}
            <div>
              <Text fw={600}>Bands</Text>
              <Text size="sm" c="dimmed">Bands must be contiguous from {money(0)}; leave the last “To” empty for “and above”.</Text>
            </div>
            <Paper withBorder style={{ overflow: 'hidden' }}>
              <ScrollArea type="auto">
                <Table className="app-table" miw={420}>
                  <Table.Thead><Table.Tr><Table.Th>From</Table.Th><Table.Th>To</Table.Th><Table.Th>Rate %</Table.Th><Table.Th w={44} /></Table.Tr></Table.Thead>
                  <Table.Tbody>
                    {draft.rows.map((r, i) => (
                      <Table.Tr key={i}>
                        <Table.Td><NumberInput aria-label={`From ${i + 1}`} min={0} thousandSeparator="," value={r.lowerBound} onChange={(v) => setRow(i, 'lowerBound', v)} /></Table.Td>
                        <Table.Td><NumberInput aria-label={`To ${i + 1}`} min={0} thousandSeparator="," placeholder="and above" value={r.upperBound} onChange={(v) => setRow(i, 'upperBound', v)} /></Table.Td>
                        <Table.Td><NumberInput aria-label={`Rate ${i + 1}`} min={0} max={100} decimalScale={2} suffix="%" value={r.ratePercent} onChange={(v) => setRow(i, 'ratePercent', v)} /></Table.Td>
                        <Table.Td>
                          <Tooltip label="Remove band">
                            <ActionIcon variant="subtle" color="red" aria-label="Remove band" onClick={() => setDraft({ ...draft, rows: draft.rows.filter((_, j) => j !== i) })}><IconTrash size={16} /></ActionIcon>
                          </Tooltip>
                        </Table.Td>
                      </Table.Tr>
                    ))}
                  </Table.Tbody>
                </Table>
              </ScrollArea>
            </Paper>
            <Group justify="space-between" wrap="wrap">
              <Button variant="light" leftSection={<IconPlus size={16} />}
                onClick={() => setDraft({ ...draft, rows: [...draft.rows, { lowerBound: draft.rows.at(-1)?.upperBound ?? 0, upperBound: '', ratePercent: 0 }] })}>
                Add band
              </Button>
              <Group gap="xs">
                <Button variant="default" onClick={() => setDraft(null)}>Cancel</Button>
                <Button loading={saving} disabled={!draft.code.trim() || !draft.name.trim() || draft.rows.length === 0} onClick={() => void save()}>Save</Button>
              </Group>
            </Group>
          </Stack>
        )}
      </Drawer>
    </>
  );
}
