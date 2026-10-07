import { Badge, Button, Group, NumberInput, Popover, Stack } from '@mantine/core';
import { useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { api } from '../../api/client';
import { useAuth } from '../../auth/AuthContext';
import { CrudPanel } from '../../components/CrudPanel';
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

const TYPES = ['PUBLIC', 'MERCANTILE', 'COMPANY', 'OTHER'].map((v) => ({ value: v, label: v }));

/** FR-025 holiday calendar per company and year. */
export function HolidaysPanel({ companyId }: { companyId: string }) {
  const { can } = useAuth();
  const client = useQueryClient();
  const editable = can('ORG:CONFIGURE');
  const [year, setYear] = useState(new Date().getFullYear());
  const [copyTo, setCopyTo] = useState<number | string>(year + 1);
  const { data: branches } = useBranches(companyId);

  const copy = async () => {
    try {
      const result = await api<{ copied: number; skipped: number }>(`/companies/${companyId}/holidays/copy`, {
        method: 'POST',
        body: { fromYear: year, toYear: Number(copyTo) },
      });
      showSuccess(`Copied ${result.copied} holidays (${result.skipped} skipped)`);
      await client.invalidateQueries({ queryKey: ['holidays'] });
    } catch (e) {
      showError(e);
    }
  };

  return (
    <Stack>
      <Group>
        <NumberInput label="Year" value={year} onChange={(v) => setYear(Number(v) || year)} w={120} min={2000} max={2100} />
        {editable && (
          <Popover position="bottom-start">
            <Popover.Target>
              <Button variant="light" mt={24}>Copy year…</Button>
            </Popover.Target>
            <Popover.Dropdown>
              <Group align="flex-end">
                <NumberInput label="Copy to year" value={copyTo} onChange={setCopyTo} w={120} />
                <Button onClick={() => void copy()}>Copy</Button>
              </Group>
            </Popover.Dropdown>
          </Popover>
        )}
      </Group>
      <CrudPanel<Holiday>
        entityName="holiday"
        queryKey={['holidays', companyId, year]}
        load={() => api<Holiday[]>(`/companies/${companyId}/holidays`, { query: { year } })}
        rowKey={(r) => r.id}
        columns={[
          { key: 'date', header: 'Date', render: (r) => r.date },
          { key: 'name', header: 'Name', render: (r) => r.name },
          { key: 'type', header: 'Type', render: (r) => <Badge variant="light">{r.type}</Badge> },
          {
            key: 'branches', header: 'Applies to',
            render: (r) => (r.allBranches ? 'All branches' : (branches ?? []).filter((b) => r.branchIds.includes(b.id)).map((b) => b.code).join(', ')),
          },
        ]}
        fields={[
          { name: 'date', label: 'Date', type: 'date', required: true },
          { name: 'name', label: 'Name', required: true },
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
