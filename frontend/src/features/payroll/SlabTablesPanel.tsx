import { ActionIcon, Badge, Button, Drawer, Group, NumberInput, Stack, Table, Text, TextInput } from '@mantine/core';
import { IconPencil, IconPlus, IconTrash } from '@tabler/icons-react';
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

/** FR-080/081 progressive slab tables (e.g. APIT): contiguous bands from 0, last band open-ended. */
export function SlabTablesPanel({ companyId }: { companyId: string }) {
  const { can } = useAuth();
  const client = useQueryClient();
  const editable = can('PAYROLL:CONFIGURE');
  const [draft, setDraft] = useState<Draft | null>(null);
  const { data = [], isLoading } = useQuery({ queryKey: ['slab-tables', companyId], queryFn: () => api<SlabTable[]>(`/companies/${companyId}/slab-tables`) });
  const setRow = (i: number, key: keyof Draft['rows'][number], value: number | string) =>
    setDraft((d) => d && { ...d, rows: d.rows.map((r, j) => (j === i ? { ...r, [key]: value } : r)) });
  const save = async () => {
    if (!draft) return;
    const rows: SlabRow[] = draft.rows.map((r) => ({ lowerBound: Number(r.lowerBound), upperBound: r.upperBound === '' ? null : Number(r.upperBound), ratePercent: Number(r.ratePercent) }));
    const body = { code: draft.code, name: draft.name, statutory: draft.statutory, rows, version: draft.version };
    try {
      if (draft.id) await api(`/slab-tables/${draft.id}`, { method: 'PUT', body });
      else await api(`/companies/${companyId}/slab-tables`, { method: 'POST', body });
      showSuccess();
      setDraft(null);
      await client.invalidateQueries({ queryKey: ['slab-tables'] });
    } catch (e) {
      showError(e);
    }
  };
  return (
    <>
      {editable && (
        <Group justify="flex-end" mb="sm"><Button leftSection={<IconPlus size={16} />} onClick={() => setDraft(toDraft(null))}>Create slab table</Button></Group>
      )}
      <DataTable
        rows={data}
        loading={isLoading}
        rowKey={(r) => r.id}
        columns={[
          { key: 'code', header: 'Code', render: (r) => r.code },
          { key: 'name', header: 'Name', render: (r) => r.name },
          { key: 'bands', header: 'Bands', render: (r) => r.rows.length },
          { key: 'stat', header: '', render: (r) => r.statutory && <Badge size="xs">Statutory</Badge> },
          { key: 'edit', header: '', render: (r) => editable && <ActionIcon variant="subtle" aria-label="Edit" onClick={() => setDraft(toDraft(r))}><IconPencil size={16} /></ActionIcon> },
        ]}
      />
      <Drawer opened={draft !== null} onClose={() => setDraft(null)} title="Slab table" position="right" size="lg">
        {draft && (
          <Stack>
            <TextInput label="Code" withAsterisk value={draft.code} onChange={(e) => setDraft({ ...draft, code: e.currentTarget.value })} />
            <TextInput label="Name" withAsterisk value={draft.name} onChange={(e) => setDraft({ ...draft, name: e.currentTarget.value })} />
            <Table>
              <Table.Thead><Table.Tr><Table.Th>From</Table.Th><Table.Th>To (empty = and above)</Table.Th><Table.Th>Rate %</Table.Th><Table.Th /></Table.Tr></Table.Thead>
              <Table.Tbody>
                {draft.rows.map((r, i) => (
                  <Table.Tr key={i}>
                    <Table.Td><NumberInput aria-label={`From ${i + 1}`} min={0} value={r.lowerBound} onChange={(v) => setRow(i, 'lowerBound', v)} /></Table.Td>
                    <Table.Td><NumberInput aria-label={`To ${i + 1}`} min={0} value={r.upperBound} onChange={(v) => setRow(i, 'upperBound', v)} /></Table.Td>
                    <Table.Td><NumberInput aria-label={`Rate ${i + 1}`} min={0} max={100} decimalScale={2} value={r.ratePercent} onChange={(v) => setRow(i, 'ratePercent', v)} /></Table.Td>
                    <Table.Td>
                      <ActionIcon variant="subtle" color="red" aria-label="Remove band" onClick={() => setDraft({ ...draft, rows: draft.rows.filter((_, j) => j !== i) })}><IconTrash size={16} /></ActionIcon>
                    </Table.Td>
                  </Table.Tr>
                ))}
              </Table.Tbody>
            </Table>
            <Group justify="space-between">
              <Button variant="subtle" leftSection={<IconPlus size={16} />}
                onClick={() => setDraft({ ...draft, rows: [...draft.rows, { lowerBound: draft.rows.at(-1)?.upperBound ?? 0, upperBound: '', ratePercent: 0 }] })}>
                Add band
              </Button>
              <Text size="xs" c="dimmed">Bands must be contiguous from {money(0)}.</Text>
            </Group>
            <Group justify="flex-end"><Button onClick={() => void save()}>Save</Button></Group>
          </Stack>
        )}
      </Drawer>
    </>
  );
}
