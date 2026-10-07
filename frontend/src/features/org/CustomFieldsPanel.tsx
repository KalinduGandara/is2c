import { Badge } from '@mantine/core';
import { api } from '../../api/client';
import { useAuth } from '../../auth/AuthContext';
import { CrudPanel } from '../../components/CrudPanel';

export interface CustomField {
  id: string;
  key: string;
  label: string;
  type: 'TEXT' | 'NUMBER' | 'DATE' | 'BOOLEAN' | 'SELECT';
  options: string[];
  required: boolean;
  active: boolean;
  displayOrder: number;
  version: number;
}

const TYPES = ['TEXT', 'NUMBER', 'DATE', 'BOOLEAN', 'SELECT'].map((v) => ({ value: v, label: v }));

export function CustomFieldsPanel({ companyId }: { companyId: string }) {
  const { can } = useAuth();
  const editable = can('EMPLOYEE:CONFIGURE');
  return (
    <CrudPanel<CustomField>
      entityName="custom field"
      queryKey={['custom-fields', companyId]}
      load={() => api<CustomField[]>(`/companies/${companyId}/custom-fields`)}
      rowKey={(r) => r.id}
      columns={[
        { key: 'key', header: 'Key', render: (r) => r.key },
        { key: 'label', header: 'Label', render: (r) => r.label },
        { key: 'type', header: 'Type', render: (r) => r.type },
        { key: 'req', header: 'Required', render: (r) => (r.required ? 'Yes' : 'No') },
        { key: 'active', header: 'Status', render: (r) => <Badge color={r.active ? 'green' : 'gray'}>{r.active ? 'Active' : 'Inactive'}</Badge> },
      ]}
      fields={(row) => [
        ...(row ? [] : [{ name: 'key', label: 'Key (a-z, 0-9, _)', required: true }]),
        { name: 'label', label: 'Label', required: true },
        ...(row ? [] : [{ name: 'type', label: 'Type', type: 'select' as const, options: TYPES, required: true }]),
        {
          name: 'optionsText', label: 'Options (one per line)', type: 'textarea' as const,
          visible: (v: Record<string, unknown>) => (row?.type ?? v.type) === 'SELECT',
        },
        { name: 'required', label: 'Required', type: 'checkbox' as const },
        { name: 'active', label: 'Active', type: 'checkbox' as const },
        { name: 'displayOrder', label: 'Display order', type: 'number' as const },
      ]}
      emptyValues={{ key: '', label: '', type: 'TEXT', optionsText: '', required: false, active: true, displayOrder: 0 }}
      toValues={(r) => ({ ...r, optionsText: r.options.join('\n') })}
      create={editable ? (v) => api(`/companies/${companyId}/custom-fields`, { method: 'POST', body: withOptions(v) }) : undefined}
      update={editable ? (r, v) => api(`/custom-fields/${r.id}`, { method: 'PUT', body: { ...withOptions(v), type: r.type, version: r.version } }) : undefined}
    />
  );
}

function withOptions(values: Record<string, unknown>) {
  const text = typeof values.optionsText === 'string' ? values.optionsText : '';
  const { optionsText: _ignored, ...rest } = values;
  void _ignored;
  return { ...rest, options: text.split('\n').map((s) => s.trim()).filter(Boolean) };
}
