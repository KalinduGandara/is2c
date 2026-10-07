import { ActionIcon, Badge, ColorSwatch, Group, Tooltip } from '@mantine/core';
import { IconPlayerPause, IconPlayerPlay } from '@tabler/icons-react';
import { useQueryClient } from '@tanstack/react-query';
import { api } from '../../api/client';
import { useAuth } from '../../auth/AuthContext';
import { CrudPanel } from '../../components/CrudPanel';
import type { FieldSpec, FormValues } from '../../components/EntityForm';
import { showError } from '../../components/notify';
import { hhmm, type Shift } from './types';
import { useOtPolicies } from './useShifts';

const TYPES = ['FIXED', 'FLEXIBLE', 'NIGHT', 'SPLIT', 'CUSTOM'].map((v) => ({ value: v, label: v }));
const isFlex = (v: FormValues) => v.type === 'FLEXIBLE';

function describe(s: Shift) {
  if (s.type === 'FLEXIBLE') return `${hhmm(s.flexEarliestStart)}–${hhmm(s.flexLatestEnd)} (${s.requiredMinutes} min)`;
  const first = `${hhmm(s.s1Start)}–${hhmm(s.s1End)}`;
  return s.type === 'SPLIT' ? `${first}, ${hhmm(s.s2Start)}–${hhmm(s.s2End)}` : first;
}

/** FR-030 shift templates. */
export function ShiftsPanel({ companyId }: { companyId: string }) {
  const { can } = useAuth();
  const client = useQueryClient();
  const { data: policies = [] } = useOtPolicies(companyId);
  const fields: FieldSpec[] = [
    { name: 'code', label: 'Code', required: true },
    { name: 'name', label: 'Name', required: true },
    { name: 'type', label: 'Type', type: 'select', options: TYPES, required: true },
    { name: 'color', label: 'Colour (#RRGGBB)' },
    { name: 's1Start', label: 'Start', type: 'time', visible: (v) => !isFlex(v) },
    { name: 's1End', label: 'End', type: 'time', visible: (v) => !isFlex(v), description: 'An end at or before the start runs into the next day' },
    { name: 's2Start', label: 'Second session start', type: 'time', visible: (v) => v.type === 'SPLIT' },
    { name: 's2End', label: 'Second session end', type: 'time', visible: (v) => v.type === 'SPLIT' },
    { name: 'flexEarliestStart', label: 'Earliest start', type: 'time', visible: isFlex },
    { name: 'flexLatestEnd', label: 'Latest end', type: 'time', visible: isFlex },
    { name: 'requiredMinutes', label: 'Required minutes', type: 'number', visible: isFlex },
    { name: 'lateGraceMinutes', label: 'Late grace (minutes)', type: 'number', min: 0 },
    { name: 'earlyGraceMinutes', label: 'Early-departure grace (minutes)', type: 'number', min: 0 },
    { name: 'breakMode', label: 'Break mode', type: 'select', options: [{ value: 'FIXED', label: 'Fixed deduction' }, { value: 'PUNCHED', label: 'Punched breaks' }] },
    { name: 'breakMinutes', label: 'Break minutes', type: 'number', min: 0 },
    { name: 'breakThresholdMinutes', label: 'Deduct break when worked ≥ (minutes)', type: 'number', min: 0 },
    { name: 'halfDayThresholdMinutes', label: 'Half day when worked < (minutes)', type: 'number', min: 0 },
    { name: 'otPolicyId', label: 'OT policy', type: 'select', options: policies.map((p) => ({ value: p.id, label: p.name })) },
    { name: 'windowBeforeMinutes', label: 'Punch window before (override)', type: 'number', min: 0, max: 720 },
    { name: 'windowAfterMinutes', label: 'Punch window after (override)', type: 'number', min: 0, max: 720 },
  ];
  const toggle = async (s: Shift) => {
    try {
      await api(`/shifts/${s.id}/${s.active ? 'deactivate' : 'activate'}`, { method: 'POST' });
      await client.invalidateQueries({ queryKey: ['shifts'] });
    } catch (e) {
      showError(e);
    }
  };
  return (
    <CrudPanel<Shift>
      entityName="shift"
      queryKey={['shifts', companyId]}
      load={() => api<Shift[]>(`/companies/${companyId}/shifts`)}
      rowKey={(r) => r.id}
      columns={[
        { key: 'code', header: 'Code', render: (r) => <Group gap={6}>{r.color && <ColorSwatch color={r.color} size={12} />}{r.code}</Group> },
        { key: 'name', header: 'Name', render: (r) => r.name },
        { key: 'type', header: 'Type', render: (r) => r.type },
        { key: 'time', header: 'Hours', render: (r) => describe(r) + (r.crossesMidnight ? ' (+1)' : '') },
        { key: 'mins', header: 'Scheduled', render: (r) => `${(r.scheduledMinutes / 60).toFixed(2)} h` },
        { key: 'active', header: 'Status', render: (r) => <Badge color={r.active ? 'green' : 'gray'}>{r.active ? 'Active' : 'Inactive'}</Badge> },
      ]}
      fields={fields}
      emptyValues={{ code: '', name: '', type: 'FIXED', breakMode: 'FIXED', lateGraceMinutes: 0, earlyGraceMinutes: 0, breakMinutes: 0, breakThresholdMinutes: 0 }}
      toValues={(r) => ({
        ...r,
        s1Start: hhmm(r.s1Start), s1End: hhmm(r.s1End), s2Start: hhmm(r.s2Start), s2End: hhmm(r.s2End),
        flexEarliestStart: hhmm(r.flexEarliestStart), flexLatestEnd: hhmm(r.flexLatestEnd),
      })}
      create={can('SHIFT:ADD') ? (v) => api(`/companies/${companyId}/shifts`, { method: 'POST', body: v }) : undefined}
      update={can('SHIFT:EDIT') ? (r, v) => api(`/shifts/${r.id}`, { method: 'PUT', body: { ...v, version: r.version } }) : undefined}
      extraActions={(r) =>
        can('SHIFT:EDIT') && (
          <Tooltip label={r.active ? 'Deactivate' : 'Activate'}>
            <ActionIcon variant="subtle" aria-label={r.active ? 'Deactivate' : 'Activate'} onClick={() => void toggle(r)}>
              {r.active ? <IconPlayerPause size={16} /> : <IconPlayerPlay size={16} />}
            </ActionIcon>
          </Tooltip>
        )
      }
    />
  );
}
