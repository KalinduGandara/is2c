import { Badge, Button, Group, Modal, Select, Stack, TextInput } from '@mantine/core';
import { DateInput, MonthPickerInput } from '@mantine/dates';
import { IconPlus } from '@tabler/icons-react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { api } from '../../api/client';
import { useAuth } from '../../auth/AuthContext';
import { DataTable } from '../../components/DataTable';
import { showError } from '../../components/notify';
import { toOptions, useDepartments } from '../org/useOrgData';
import { RosterGrid } from './RosterGrid';
import type { Roster } from './types';

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
  const [form, setForm] = useState<{ name: string; periodType: string; startDate: string | null; departmentId: string | null }>({ name: '', periodType: 'WEEKLY', startDate: null, departmentId: null });
  const { data: departments } = useDepartments(companyId);
  const { from, to } = monthBounds(month);
  const { data: rosters = [], isLoading } = useQuery({
    queryKey: ['rosters', companyId, month],
    queryFn: () => api<Roster[]>('/rosters', { query: { companyId, from, to } }),
  });

  if (openId) return <RosterGrid rosterId={openId} otherRosters={rosters} onClose={() => setOpenId(null)} />;

  const create = async () => {
    try {
      const r = await api<Roster>('/rosters', { method: 'POST', body: { ...form, companyId } });
      setCreating(false);
      await client.invalidateQueries({ queryKey: ['rosters'] });
      setOpenId(r.id);
    } catch (e) {
      showError(e);
    }
  };

  return (
    <>
      <Group justify="space-between" mb="sm">
        <MonthPickerInput label="Month" value={`${month}-01`} onChange={(v) => v && setMonth(String(v).slice(0, 7))} w={180} />
        {can('ROSTER:ADD') && <Button mt={24} leftSection={<IconPlus size={16} />} onClick={() => setCreating(true)}>Create roster</Button>}
      </Group>
      <DataTable
        rows={rosters}
        loading={isLoading}
        rowKey={(r) => r.id}
        onRowClick={(r) => setOpenId(r.id)}
        columns={[
          { key: 'name', header: 'Name', render: (r) => r.name },
          { key: 'type', header: 'Period', render: (r) => `${r.periodType} ${r.startDate} → ${r.endDate}` },
          { key: 'dept', header: 'Department', render: (r) => (departments ?? []).find((d) => d.id === r.departmentId)?.name ?? 'All' },
          { key: 'status', header: 'Status', render: (r) => <Badge>{r.status}</Badge> },
          { key: 'rev', header: 'Revision', render: (r) => r.revision || '—' },
        ]}
      />
      <Modal opened={creating} onClose={() => setCreating(false)} title="Create roster">
        <Stack>
          <TextInput label="Name" withAsterisk value={form.name} onChange={(e) => setForm({ ...form, name: e.currentTarget.value })} />
          <Select label="Period" data={[{ value: 'WEEKLY', label: 'Weekly' }, { value: 'MONTHLY', label: 'Monthly' }]} value={form.periodType} allowDeselect={false} onChange={(v) => setForm({ ...form, periodType: v ?? 'WEEKLY' })} />
          <DateInput label="Start date" description="Weekly: the company's week-start day. Monthly: the 1st." valueFormat="YYYY-MM-DD" value={form.startDate} onChange={(v) => setForm({ ...form, startDate: v ? String(v) : null })} />
          <Select label="Department (optional)" clearable data={toOptions(departments)} value={form.departmentId} onChange={(v) => setForm({ ...form, departmentId: v })} />
          <Group justify="flex-end"><Button disabled={!form.name || !form.startDate} onClick={() => void create()}>Create</Button></Group>
        </Stack>
      </Modal>
    </>
  );
}
