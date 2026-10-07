import { Badge } from '@mantine/core';
import { useQuery } from '@tanstack/react-query';
import { api } from '../../api/client';
import { useAuth } from '../../auth/AuthContext';
import { CrudPanel } from '../../components/CrudPanel';
import type { FieldSpec } from '../../components/EntityForm';
import type { PayrollComponent, SlabTable } from './types';

const opts = (values: [string, string][]) => values.map(([value, label]) => ({ value, label }));

export const CALC_TYPES = opts([['FIXED', 'Fixed amount'], ['PERCENT_OF', 'Percentage of'], ['PER_UNIT', 'Per unit'], ['SLAB', 'Slab table']]);
const KINDS = opts([['EARNING', 'Earning'], ['DEDUCTION', 'Deduction'], ['EMPLOYER_CONTRIBUTION', 'Employer contribution']]);
const UNIT_METRICS = opts([
  ['OT_WORKDAY_HOURS', 'OT hours (workday)'], ['OT_OFFDAY_HOURS', 'OT hours (off day)'], ['OT_HOLIDAY_HOURS', 'OT hours (holiday)'],
  ['EXTRA_HOURS', 'Extra hours'], ['NO_PAY_DAYS', 'No-pay days'], ['LATE_HOURS', 'Late hours'], ['SHORT_HOURS', 'Short hours'],
  ['ABSENT_DAYS', 'Absent days'], ['WORKED_DAYS', 'Worked days'],
]);

/** FR-080 earnings, deductions and employer contributions with type-dependent calculation fields. */
export function ComponentsPanel({ companyId }: { companyId: string }) {
  const { can } = useAuth();
  const editable = can('PAYROLL:CONFIGURE');
  const { data: slabs = [] } = useQuery({ queryKey: ['slab-tables', companyId], queryFn: () => api<SlabTable[]>(`/companies/${companyId}/slab-tables`) });
  const is = (...types: string[]) => (v: Record<string, unknown>) => types.includes(String(v.calcType));
  const fields: FieldSpec[] = [
    { name: 'code', label: 'Code', required: true },
    { name: 'name', label: 'Name', required: true },
    { name: 'kind', label: 'Kind', type: 'select', required: true, options: KINDS },
    { name: 'frequency', label: 'Frequency', type: 'select', required: true, options: opts([['RECURRING', 'Recurring'], ['ONE_TIME', 'One-time']]) },
    { name: 'assignment', label: 'Assignment', type: 'select', required: true, options: opts([['PER_EMPLOYEE', 'Per employee (salary structure)'], ['AUTOMATIC', 'Automatic (everyone)']]) },
    { name: 'calcType', label: 'Calculation', type: 'select', required: true, options: CALC_TYPES },
    { name: 'sequence', label: 'Sequence (calculation order)', type: 'number', min: 0, required: true },
    { name: 'bases', label: 'Adds to bases', type: 'multiselect', options: opts([['STATUTORY', 'Statutory (EPF/ETF)'], ['TAXABLE', 'Taxable (APIT)']]) },
    { name: 'basisRef', label: 'Basis', description: 'Component code, GROSS or BASE:<name>', visible: (v) => is('PERCENT_OF', 'SLAB')(v) || (is('PER_UNIT')(v) && v.unitRateMode === 'BASIS') },
    { name: 'ratePercent', label: 'Rate %', type: 'number', min: 0, visible: is('PERCENT_OF') },
    { name: 'unitMetric', label: 'Unit', type: 'select', options: UNIT_METRICS, visible: is('PER_UNIT') },
    { name: 'unitRateMode', label: 'Unit rate', type: 'select', options: opts([['FIXED_AMOUNT', 'Fixed amount per unit'], ['BASIS', 'Basis ÷ divisor × multiplier']]), visible: is('PER_UNIT') },
    { name: 'unitFixedAmount', label: 'Amount per unit', type: 'number', min: 0, visible: (v) => is('PER_UNIT')(v) && v.unitRateMode === 'FIXED_AMOUNT' },
    { name: 'divisor', label: 'Divisor', description: 'Empty = company no-pay divisor', type: 'number', min: 0, visible: (v) => is('PER_UNIT')(v) && v.unitRateMode === 'BASIS' },
    { name: 'multiplier', label: 'Multiplier', type: 'number', min: 0, visible: (v) => is('PER_UNIT')(v) && v.unitRateMode === 'BASIS' },
    { name: 'slabTableId', label: 'Slab table', type: 'select', options: slabs.map((s) => ({ value: s.id, label: `${s.code} — ${s.name}` })), visible: is('SLAB') },
    { name: 'prorate', label: 'Prorate for joiners/leavers', type: 'checkbox', visible: is('FIXED') },
    { name: 'showOnPayslip', label: 'Show on payslip', type: 'checkbox' },
    { name: 'statutory', label: 'Statutory', type: 'checkbox' },
    { name: 'active', label: 'Active', type: 'checkbox' },
  ];
  return (
    <CrudPanel<PayrollComponent>
      entityName="component"
      queryKey={['payroll-components', companyId]}
      load={() => api<PayrollComponent[]>(`/companies/${companyId}/payroll-components`)}
      rowKey={(r) => r.id}
      columns={[
        { key: 'seq', header: '#', render: (r) => r.sequence, width: 50 },
        { key: 'code', header: 'Code', render: (r) => r.code },
        { key: 'name', header: 'Name', render: (r) => r.name },
        { key: 'kind', header: 'Kind', render: (r) => KINDS.find((k) => k.value === r.kind)?.label },
        { key: 'calc', header: 'Calculation', render: (r) => CALC_TYPES.find((k) => k.value === r.calcType)?.label },
        { key: 'freq', header: 'Frequency', render: (r) => (r.frequency === 'ONE_TIME' ? 'One-time' : 'Recurring') },
        { key: 'flags', header: '', render: (r) => (
          <>
            {r.statutory && <Badge size="xs" mr={4}>Statutory</Badge>}
            {!r.active && <Badge size="xs" color="gray">Inactive</Badge>}
          </>
        ) },
      ]}
      fields={fields}
      emptyValues={{ code: '', name: '', kind: 'EARNING', frequency: 'RECURRING', assignment: 'PER_EMPLOYEE', calcType: 'FIXED', sequence: 50, bases: [], basisRef: '', ratePercent: null, unitMetric: null, unitRateMode: null, unitFixedAmount: null, divisor: null, multiplier: null, slabTableId: null, prorate: true, showOnPayslip: true, statutory: false, active: true }}
      toValues={(r) => ({ ...r, basisRef: r.basisRef ?? '' })}
      create={editable ? (v) => api(`/companies/${companyId}/payroll-components`, { method: 'POST', body: v }) : undefined}
      update={editable ? (r, v) => api(`/payroll-components/${r.id}`, { method: 'PUT', body: { ...v, version: r.version } }) : undefined}
    />
  );
}
