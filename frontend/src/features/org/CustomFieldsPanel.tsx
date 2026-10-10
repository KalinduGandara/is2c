import { Badge, Code, Group } from '@mantine/core';
import { IconAbc, IconCalendar, IconHash, IconList, IconToggleLeft, type Icon } from '@tabler/icons-react';
import { api } from '../../api/client';
import { useAuth } from '../../auth/AuthContext';
import { CrudPanel } from '../../components/CrudPanel';
import { StatusBadge } from '../../components/StatusBadge';
import { BoolIcon } from './BoolIcon';

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

const TYPE_META: Record<CustomField['type'], { label: string; icon: Icon }> = {
  TEXT: { label: 'Text', icon: IconAbc },
  NUMBER: { label: 'Number', icon: IconHash },
  DATE: { label: 'Date', icon: IconCalendar },
  BOOLEAN: { label: 'Yes / no', icon: IconToggleLeft },
  SELECT: { label: 'Dropdown list', icon: IconList },
};

const TYPES = (Object.keys(TYPE_META) as CustomField['type'][]).map((v) => ({ value: v, label: TYPE_META[v].label }));

export function CustomFieldsPanel({ companyId }: { companyId: string }) {
  const { can } = useAuth();
  const editable = can('EMPLOYEE:CONFIGURE');
  return (
    <CrudPanel<CustomField>
      entityName="custom field"
      description="Extra fields captured on every employee record of this company."
      queryKey={['custom-fields', companyId]}
      load={() => api<CustomField[]>(`/companies/${companyId}/custom-fields`)}
      rowKey={(r) => r.id}
      columns={[
        { key: 'label', header: 'Label', render: (r) => r.label },
        { key: 'key', header: 'Key', render: (r) => <Code>{r.key}</Code> },
        {
          key: 'type', header: 'Type',
          render: (r) => {
            const meta = TYPE_META[r.type];
            const TypeIcon = meta?.icon ?? IconAbc;
            return (
              <Group gap={6} wrap="nowrap">
                <Badge variant="light" color="gray" leftSection={<TypeIcon size={12} />}>{meta?.label ?? r.type}</Badge>
                {r.type === 'SELECT' && r.options.length > 0 && (
                  <Badge variant="outline" color="gray" size="sm">{r.options.length} options</Badge>
                )}
              </Group>
            );
          },
        },
        { key: 'req', header: 'Required', align: 'center', render: (r) => <BoolIcon value={r.required} yes="Required" no="Optional" /> },
        { key: 'order', header: 'Order', align: 'right', render: (r) => <span className="tabular">{r.displayOrder}</span> },
        { key: 'active', header: 'Status', render: (r) => <StatusBadge status={r.active ? 'ACTIVE' : 'INACTIVE'} /> },
      ]}
      fields={(row) => [
        ...(row ? [] : [{ name: 'key', label: 'Key', required: true, description: 'Lower-case letters, digits and underscores; cannot be changed later.', placeholder: 'blood_group' }]),
        { name: 'label', label: 'Label', required: true, placeholder: 'Blood group' },
        ...(row ? [] : [{ name: 'type', label: 'Type', type: 'select' as const, options: TYPES, required: true }]),
        {
          name: 'optionsText', label: 'Options (one per line)', type: 'textarea' as const,
          visible: (v: Record<string, unknown>) => (row?.type ?? v.type) === 'SELECT',
        },
        { name: 'required', label: 'Required', type: 'checkbox' as const, description: 'Employees cannot be saved without a value.' },
        { name: 'active', label: 'Active', type: 'checkbox' as const },
        { name: 'displayOrder', label: 'Display order', type: 'number' as const, description: 'Lower numbers are shown first.' },
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
