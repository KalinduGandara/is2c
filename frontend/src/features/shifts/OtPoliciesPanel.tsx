import { Badge, Group, Text } from '@mantine/core';
import { IconCheck, IconMinus } from '@tabler/icons-react';
import { api } from '../../api/client';
import { useAuth } from '../../auth/AuthContext';
import { CrudPanel } from '../../components/CrudPanel';
import type { OtPolicy } from './types';

const mins = (m: number) => (m >= 60 && m % 60 === 0 ? `${m / 60}h` : m > 60 ? `${Math.floor(m / 60)}h ${m % 60}m` : `${m} min`);

function YesNo({ value, yes, no }: { value: boolean; yes: string; no: string }) {
  return value ? (
    <Badge variant="light" color="teal" leftSection={<IconCheck size={12} />}>{yes}</Badge>
  ) : (
    <Badge variant="light" color="gray" leftSection={<IconMinus size={12} />}>{no}</Badge>
  );
}

/** FR-032 overtime policies. */
export function OtPoliciesPanel({ companyId }: { companyId: string }) {
  const { can } = useAuth();
  const editable = can('SHIFT:CONFIGURE');
  return (
    <CrudPanel<OtPolicy>
      entityName="OT policy"
      queryKey={['ot-policies', companyId]}
      load={() => api<OtPolicy[]>(`/companies/${companyId}/ot-policies`)}
      rowKey={(r) => r.id}
      description="Rules that turn time worked beyond the shift into overtime."
      columns={[
        { key: 'name', header: 'Name', render: (r) => <Text size="sm" fw={600}>{r.name}</Text> },
        {
          key: 'rule', header: 'Rule',
          render: (r) => (
            <Text size="sm">
              Counts after <b>{mins(r.minOtMinutes)}</b>, rounded down to <b>{mins(r.roundingMinutes)}</b>
              {r.maxOtMinutes ? <>, capped at <b>{mins(r.maxOtMinutes)}</b> a day</> : <Text span c="dimmed">, no daily cap</Text>}
            </Text>
          ),
        },
        {
          key: 'flags', header: 'Options',
          render: (r) => (
            <Group gap={4} wrap="nowrap">
              <YesNo value={r.allowPreShiftOt} yes="Pre-shift OT" no="No pre-shift OT" />
              <YesNo value={r.requiresApproval} yes="Needs approval" no="Auto-approved" />
            </Group>
          ),
        },
      ]}
      fields={[
        { name: 'name', label: 'Name', required: true },
        { name: 'minOtMinutes', label: 'Minimum OT minutes', type: 'number', min: 0, description: 'Shorter overtime is ignored' },
        { name: 'roundingMinutes', label: 'Round down to (minutes)', type: 'number', min: 1, max: 120 },
        { name: 'maxOtMinutes', label: 'Maximum OT minutes per day', type: 'number', min: 1, description: 'Leave empty for no daily cap' },
        { name: 'allowPreShiftOt', label: 'Count overtime before shift start', type: 'checkbox' },
        { name: 'requiresApproval', label: 'Overtime requires approval', type: 'checkbox' },
      ]}
      emptyValues={{ name: '', minOtMinutes: 30, roundingMinutes: 15, maxOtMinutes: null, allowPreShiftOt: false, requiresApproval: false }}
      toValues={(r) => ({ ...r })}
      create={editable ? (v) => api(`/companies/${companyId}/ot-policies`, { method: 'POST', body: v }) : undefined}
      update={editable ? (r, v) => api(`/ot-policies/${r.id}`, { method: 'PUT', body: { ...v, version: r.version } }) : undefined}
      remove={editable ? (r) => api(`/ot-policies/${r.id}`, { method: 'DELETE' }) : undefined}
    />
  );
}
