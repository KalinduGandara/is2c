import { NumberInput, Select, TextInput } from '@mantine/core';
import { useQuery } from '@tanstack/react-query';
import { api } from '../../api/client';
import { toOptions, useBranches, useDepartments, useLookups } from '../org/useOrgData';
import { periodLabel, type PayrollPeriod } from '../payroll/types';
import type { ReportParam } from './types';

interface Props {
  param: ReportParam;
  companyId: string;
  value: string;
  onChange: (value: string) => void;
}

type Option = { value: string; label: string };

function useOptions(type: string, companyId: string): Option[] | null {
  const branches = useBranches(type === 'branch' ? companyId : null);
  const departments = useDepartments(type === 'department' ? companyId : null);
  const designations = useLookups(type === 'designation' ? companyId : null, 'designations');
  const categories = useLookups(type === 'category' ? companyId : null, 'employment-categories');
  const leaveTypes = useQuery({
    queryKey: ['leave-types', companyId],
    queryFn: () => api<{ id: string; code: string; name: string }[]>(`/companies/${companyId}/leave-types`),
    enabled: type === 'leaveType',
  });
  const rosters = useQuery({
    queryKey: ['report-rosters', companyId],
    queryFn: () => api<{ id: string; name: string; startDate: string; endDate: string }[]>('/rosters', {
      query: { companyId, from: `${new Date().getFullYear() - 1}-01-01`, to: `${new Date().getFullYear() + 1}-12-31` },
    }),
    enabled: type === 'roster',
  });
  const periods = useQuery({
    queryKey: ['payroll-periods', companyId],
    queryFn: () => api<PayrollPeriod[]>('/payroll/periods', { query: { companyId } }),
    enabled: type === 'payrollPeriod',
  });
  switch (type) {
    case 'branch': return toOptions(branches.data);
    case 'department': return toOptions(departments.data);
    case 'designation': return toOptions(designations.data);
    case 'category': return toOptions(categories.data);
    case 'leaveType': return toOptions(leaveTypes.data);
    case 'roster': return (rosters.data ?? []).map((r) => ({ value: r.id, label: `${r.name} (${r.startDate} → ${r.endDate})` }));
    case 'payrollPeriod': return (periods.data ?? []).map((p) => ({ value: p.id, label: `${periodLabel(p)} — ${p.status}` }));
    case 'employeeStatus': return ['ACTIVE', 'INACTIVE', 'RESIGNED', 'TERMINATED'].map((s) => ({ value: s, label: s }));
    default: return null;
  }
}

/** Renders the input for one report parameter based on its type (the company parameter is implicit). */
export function ParamInput({ param, companyId, value, onChange }: Props) {
  const options = useOptions(param.type, companyId);
  const common = { label: param.label, withAsterisk: param.required };
  if (options) {
    return <Select {...common} data={options} value={value || null} onChange={(v) => onChange(v ?? '')} clearable={!param.required} searchable w={240} />;
  }
  if (param.type === 'year') {
    return <NumberInput {...common} value={value} onChange={(v) => onChange(String(v))} min={2000} max={2100} w={120} />;
  }
  return (
    <TextInput {...common} placeholder={param.type === 'date' ? 'YYYY-MM-DD' : undefined} value={value}
      onChange={(e) => onChange(e.currentTarget.value)} w={param.type === 'date' ? 160 : 220} />
  );
}
