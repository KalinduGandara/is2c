import { api } from '../../api/client';
import { useAuth } from '../../auth/AuthContext';
import { CrudPanel } from '../../components/CrudPanel';
import type { OtPolicy } from './types';

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
      columns={[
        { key: 'name', header: 'Name', render: (r) => r.name },
        { key: 'min', header: 'Minimum', render: (r) => `${r.minOtMinutes} min` },
        { key: 'round', header: 'Rounding', render: (r) => `${r.roundingMinutes} min` },
        { key: 'max', header: 'Daily cap', render: (r) => (r.maxOtMinutes ? `${r.maxOtMinutes} min` : '—') },
        { key: 'pre', header: 'Pre-shift OT', render: (r) => (r.allowPreShiftOt ? 'Yes' : 'No') },
        { key: 'appr', header: 'Needs approval', render: (r) => (r.requiresApproval ? 'Yes' : 'No') },
      ]}
      fields={[
        { name: 'name', label: 'Name', required: true },
        { name: 'minOtMinutes', label: 'Minimum OT minutes', type: 'number', min: 0 },
        { name: 'roundingMinutes', label: 'Round down to (minutes)', type: 'number', min: 1, max: 120 },
        { name: 'maxOtMinutes', label: 'Maximum OT minutes per day', type: 'number', min: 1 },
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
