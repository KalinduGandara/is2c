import { Badge, Button, Group, Modal, Select, SimpleGrid, Stack, Text, TextInput } from '@mantine/core';
import { DateInput, MonthPickerInput } from '@mantine/dates';
import { IconCalendar, IconLayoutGrid, IconPlus } from '@tabler/icons-react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { api } from '../../api/client';
import { useAuth } from '../../auth/AuthContext';
import { DataTable } from '../../components/DataTable';
import { StatusBadge } from '../../components/StatusBadge';
import { formatDate } from '../../components/format';
import { showError } from '../../components/notify';
import { toOptions, useDepartments } from '../org/useOrgData';
import { RosterGrid } from './RosterGrid';
import type { Roster } from './types';

const ROSTER_COLORS = { DRAFT: 'blue', PUBLISHED: 'green', LOCKED: 'red' };

function monthBounds(month: string) {
  const [y, m] = month.split('-').map(Number);
  const last = new Date(Date.UTC(y, m, 0)).getUTCDate();
  return { from: `${month}-01`, to: `${month}-${String(last).padStart(2, '0')}` };
}

/** FR-035 roster list for a month; opens the grid editor. */
export function RostersPanel({ companyId }: { companyId: string }) {
  const { can } = useAuth();
  const client = useQueryClient();
  const [month, setMonth] = useState(() => new Date().toISOString().slice(0, 7));
  const [openId, setOpenId] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form, setForm] = useState<{ name: string; periodType: string; startDate: string | null; departmentId: string | null }>({ name: '', periodType: 'WEEKLY', startDate: null, departmentId: null });
  const { data: departments } = useDepartments(companyId);
  const { from, to } = monthBounds(month);
  const { data: rosters = [], isLoading } = useQuery({
    queryKey: ['rosters', companyId, month],
    queryFn: () => api<Roster[]>('/rosters', { query: { companyId, from, to } }),
  });

  if (openId) return <RosterGrid rosterId={openId} otherRosters={rosters} onClose={() => setOpenId(null)} />;

  const create = async () => {
    setSaving(true);
    try {
      const r = await api<Roster>('/rosters', { method: 'POST', body: { ...form, companyId } });
      setCreating(false);
      await client.invalidateQueries({ queryKey: ['rosters'] });
      setOpenId(r.id);
    } catch (e) {
      showError(e);
    } finally {
      setSaving(false);
    }
  };

  const createButton = can('ROSTER:ADD') && <Button leftSection={<IconPlus size={16} />} onClick={() => setCreating(true)}>Create roster</Button>;

  return (
    <>
      <Group justify="space-between" align="flex-end" mb="sm" wrap="wrap" gap="sm">
        <MonthPickerInput label="Month" leftSection={<IconCalendar size={16} />} valueFormat="MMMM YYYY" value={`${month}-01`} onChange={(v) => v && setMonth(String(v).slice(0, 7))} w={{ base: '100%', xs: 200 }} />
        {createButton}
      </Group>
      <DataTable
        rows={rosters}
        loading={isLoading}
        rowKey={(r) => r.id}
        onRowClick={(r) => setOpenId(r.id)}
        rowLabel={(r) => `Open roster ${r.name}`}
        emptyTitle="No rosters this month"
        emptyDescription={can('ROSTER:ADD') ? "Rosters assign shifts to employees for a week or a month. Use “Create roster” to start one." : "Rosters assign shifts to employees for a week or a month."}
        emptyIcon={<IconLayoutGrid size={20} />}
        columns={[
          { key: 'name', header: 'Name', render: (r) => <Text size="sm" fw={600}>{r.name}</Text> },
          {
            key: 'type', header: 'Period',
            render: (r) => (
              <Group gap="xs" wrap="nowrap">
                <Badge variant="light" color="gray">{r.periodType === 'WEEKLY' ? 'Weekly' : 'Monthly'}</Badge>
                <Text size="sm" style={{ whiteSpace: 'nowrap' }}>{formatDate(r.startDate)} – {formatDate(r.endDate)}</Text>
              </Group>
            ),
          },
          { key: 'dept', header: 'Department', render: (r) => (departments ?? []).find((d) => d.id === r.departmentId)?.name ?? <Text size="sm" c="dimmed">All departments</Text> },
          { key: 'status', header: 'Status', render: (r) => <StatusBadge status={r.status} colors={ROSTER_COLORS} /> },
          { key: 'rev', header: 'Revision', align: 'right', render: (r) => (r.revision ? <Text size="sm" className="tabular">{r.revision}</Text> : <Text size="sm" c="dimmed">—</Text>) },
        ]}
      />
      <Modal opened={creating} onClose={() => setCreating(false)} title="Create roster">
        <Stack>
          <TextInput label="Name" withAsterisk placeholder="e.g. Week of 16 March" data-autofocus value={form.name} onChange={(e) => setForm({ ...form, name: e.currentTarget.value })} />
          <SimpleGrid cols={{ base: 1, xs: 2 }}>
            <Select label="Period" data={[{ value: 'WEEKLY', label: 'Weekly' }, { value: 'MONTHLY', label: 'Monthly' }]} value={form.periodType} allowDeselect={false} onChange={(v) => setForm({ ...form, periodType: v ?? 'WEEKLY' })} />
            <DateInput label="Start date" withAsterisk description={form.periodType === 'WEEKLY' ? "The company's week-start day" : 'The 1st of the month'} valueFormat="YYYY-MM-DD" value={form.startDate} onChange={(v) => setForm({ ...form, startDate: v ? String(v) : null })} />
          </SimpleGrid>
          <Select label="Department (optional)" placeholder="All departments" clearable data={toOptions(departments)} value={form.departmentId} onChange={(v) => setForm({ ...form, departmentId: v })} />
          <Group justify="flex-end" mt="xs">
            <Button variant="default" onClick={() => setCreating(false)}>Cancel</Button>
            <Button leftSection={<IconPlus size={16} />} loading={saving} disabled={!form.name || !form.startDate} onClick={() => void create()}>Create</Button>
          </Group>
        </Stack>
      </Modal>
    </>
  );
}
