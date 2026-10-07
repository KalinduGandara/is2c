import { useQuery } from '@tanstack/react-query';
import { api } from '../../api/client';
import type { OtPolicy, Shift } from './types';

export function useShifts(companyId: string | null) {
  return useQuery({
    queryKey: ['shifts', companyId],
    queryFn: () => api<Shift[]>(`/companies/${companyId}/shifts`),
    enabled: !!companyId,
  });
}

export function useOtPolicies(companyId: string | null) {
  return useQuery({
    queryKey: ['ot-policies', companyId],
    queryFn: () => api<OtPolicy[]>(`/companies/${companyId}/ot-policies`),
    enabled: !!companyId,
  });
}
