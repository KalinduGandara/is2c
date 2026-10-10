import { ActionIcon, Badge, Button, Group, NumberInput, Popover, Stack, Text, Tooltip } from '@mantine/core';
import { IconChevronLeft, IconChevronRight, IconCopy } from '@tabler/icons-react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { api } from '../../api/client';
import { useAuth } from '../../auth/AuthContext';
import { CrudPanel } from '../../components/CrudPanel';
import { formatDate } from '../../components/format';
import { showError, showSuccess } from '../../components/notify';
import { toOptions, useBranches } from './useOrgData';

export interface Holiday {
  id: string;
  date: string;
  name: string;
  type: 'PUBLIC' | 'MERCANTILE' | 'COMPANY' | 'OTHER';
  allBranches: boolean;
  branchIds: string[];
  version: number;
}

const TYPE_COLORS: Record<Holiday['type'], string> = { PUBLIC: 'grape', MERCANTILE: 'blue', COMPANY: 'teal', OTHER: 'gray' };
const TYPES = [
  { value: 'PUBLIC', label: 'Public' },
  { value: 'MERCANTILE', label: 'Mercantile' },
  { value: 'COMPANY', label: 'Company' },
  { value: 'OTHER', label: 'Other' },
];
const TYPE_LABEL = Object.fromEntries(TYPES.map((t) => [t.value, t.label]));
const WEEKDAY = new Intl.DateTimeFormat('en-GB', { weekday: 'long', timeZone: 'UTC' });

const weekday = (iso: string) => {
  const d = new Date(`${iso.slice(0, 10)}T00:00:00Z`);
  return Number.isNaN(d.getTime()) ? '' : WEEKDAY.format(d);
};

function todayIso() {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

/** FR-025 holiday calendar per company and year. */
export function HolidaysPanel({ companyId }: { companyId: string }) {
  const { can } = useAuth();
  const client = useQueryClient();
  const editable = can('ORG:CONFIGURE');
  const [year, setYear] = useState(new Date().getFullYear());
  const [copyTo, setCopyTo] = useState<number | string>(year + 1);
  const [copying, setCopying] = useState(false);
  const { data: branches } = useBranches(companyId);
  const queryKey = ['holidays', companyId, year];
  const load = () => api<Holiday[]>(`/companies/${companyId}/holidays`, { query: { year } });
  // Shares the cache entry with the list below; used to highlight the next upcoming holiday.
  const { data: holidays = [] } = useQuery({ queryKey, queryFn: load });
  const today = todayIso();
  const next = [...holidays].filter((h) => h.date >= today).sort((a, b) => a.date.localeCompare(b.date))[0];

  const copy = async () => {
    setCopying(true);
    try {
      const result = await api<{ copied: number; skipped: number }>(`/companies/${companyId}/holidays/copy`, {
        method: 'POST',
        body: { fromYear: year, toYear: Number(copyTo) },
      });
      showSuccess(`Copied ${result.copied} holidays (${result.skipped} skipped)`);
      await client.invalidateQueries({ queryKey: ['holidays'] });
    } catch (e) {
      showError(e);
    } finally {
      setCopying(false);
    }
  };

  return (
    <Stack gap="sm">
      <Group justify="space-between" wrap="wrap" gap="sm" align="flex-end">
        <Group gap={6} align="flex-end" wrap="nowrap">
          <Tooltip label="Previous year">
            <ActionIcon variant="default" size="lg" aria-label="Previous year" onClick={() => setYear((y) => y - 1)} disabled={year <= 2000}>
              <IconChevronLeft size={16} />
            </ActionIcon>
          </Tooltip>
          <NumberInput label="Year" value={year} onChange={(v) => setYear(Number(v) || year)} w={110} min={2000} max={2100} />
          <Tooltip label="Next year">
            <ActionIcon variant="default" size="lg" aria-label="Next year" onClick={() => setYear((y) => y + 1)} disabled={year >= 2100}>
              <IconChevronRight size={16} />
            </ActionIcon>
          </Tooltip>
          <Text size="sm" c="dimmed" ml="xs" mb={6}>
            <Text span fw={600} c="var(--mantine-color-text)">{holidays.length}</Text> holidays
          </Text>
        </Group>
        {editable && (
          <Popover position="bottom-end" withArrow shadow="md">
            <Popover.Target>
              <Button variant="default" leftSection={<IconCopy size={16} />}>Copy year…</Button>
            </Popover.Target>
            <Popover.Dropdown>
              <Stack gap="xs">
                <Text size="sm" c="dimmed">Copy all {year} holidays to another year. Existing dates are skipped.</Text>
                <Group align="flex-end" wrap="nowrap">
                  <NumberInput label="Copy to year" value={copyTo} onChange={setCopyTo} w={120} min={2000} max={2100} />
                  <Button onClick={() => void copy()} loading={copying} disabled={!copyTo || Number(copyTo) === year}>Copy</Button>
                </Group>
              </Stack>
            </Popover.Dropdown>
          </Popover>
        )}
      </Group>
      <CrudPanel<Holiday>
        entityName="holiday"
        description="Public, mercantile and company holidays used by attendance, leave and payroll."
        queryKey={queryKey}
        load={load}
        rowKey={(r) => r.id}
        columns={[
          {
            key: 'date', header: 'Date', width: 200,
            render: (r) => {
              const past = r.date < today;
              return (
                <Group gap="xs" wrap="nowrap">
                  <div>
                    <Text size="sm" fw={500} c={past ? 'dimmed' : undefined}>{formatDate(r.date)}</Text>
                    <Text size="xs" c="dimmed">{weekday(r.date)}</Text>
                  </div>
                  {next?.id === r.id && <Badge size="sm" color="brand" variant="filled">Next</Badge>}
                </Group>
              );
            },
          },
          { key: 'name', header: 'Name', render: (r) => <Text size="sm" c={r.date < today ? 'dimmed' : undefined}>{r.name}</Text> },
          { key: 'type', header: 'Type', render: (r) => <Badge variant="light" color={TYPE_COLORS[r.type] ?? 'gray'}>{TYPE_LABEL[r.type] ?? r.type}</Badge> },
          {
            key: 'branches', header: 'Applies to',
            render: (r) =>
              r.allBranches ? (
                <Text size="sm" c="dimmed">All branches</Text>
              ) : (
                <Group gap={4}>
                  {(branches ?? []).filter((b) => r.branchIds.includes(b.id)).map((b) => (
                    <Badge key={b.id} variant="outline" color="gray" size="sm">{b.code}</Badge>
                  ))}
                </Group>
              ),
          },
        ]}
        fields={[
          { name: 'date', label: 'Date', type: 'date', required: true },
          { name: 'name', label: 'Name', required: true, placeholder: 'Independence Day' },
          { name: 'type', label: 'Type', type: 'select', options: TYPES, required: true },
          { name: 'allBranches', label: 'All branches', type: 'checkbox' },
          { name: 'branchIds', label: 'Branches', type: 'multiselect', options: toOptions(branches), visible: (v) => !v.allBranches },
        ]}
        emptyValues={{ date: null, name: '', type: 'PUBLIC', allBranches: true, branchIds: [] }}
        toValues={(r) => ({ ...r })}
        create={editable ? (v) => api(`/companies/${companyId}/holidays`, { method: 'POST', body: v }) : undefined}
        update={editable ? (r, v) => api(`/holidays/${r.id}`, { method: 'PUT', body: { ...v, version: r.version } }) : undefined}
        remove={editable ? (r) => api(`/holidays/${r.id}`, { method: 'DELETE' }) : undefined}
      />
    </Stack>
  );
}
