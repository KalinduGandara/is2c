import { useQuery } from '@tanstack/react-query';
import { api } from '../../api/client';
import type { Branch, Department, Lookup } from './types';

export function useBranches(companyId: string | null) {
  return useQuery({
    queryKey: ['branches', companyId],
    queryFn: () => api<Branch[]>(`/companies/${companyId}/branches`),
    enabled: !!companyId,
  });
}

export function useDepartments(companyId: string | null) {
  return useQuery({
    queryKey: ['departments', companyId],
    queryFn: () => api<Department[]>(`/companies/${companyId}/departments`),
    enabled: !!companyId,
  });
}

export function useLookups(companyId: string | null, kind: 'designations' | 'grades' | 'employment-categories') {
  return useQuery({
    queryKey: [kind, companyId],
    queryFn: () => api<Lookup[]>(`/companies/${companyId}/${kind}`),
    enabled: !!companyId,
  });
}

export const toOptions = <T extends { id: string; code: string; name: string }>(rows: T[] | undefined) =>
  (rows ?? []).map((r) => ({ value: r.id, label: `${r.code} — ${r.name}` }));

/** Flattens a department list into tree order with depth for indentation. */
export function departmentTreeRows(departments: Department[]): (Department & { depth: number })[] {
  const byParent = new Map<string | null, Department[]>();
  for (const d of departments) {
    const key = d.parentId ?? null;
    byParent.set(key, [...(byParent.get(key) ?? []), d]);
  }
  const ids = new Set(departments.map((d) => d.id));
  const result: (Department & { depth: number })[] = [];
  const walk = (parent: string | null, depth: number) => {
    for (const d of byParent.get(parent) ?? []) {
      result.push({ ...d, depth });
      walk(d.id, depth + 1);
    }
  };
  walk(null, 0);
  // Orphans (parent not visible) are shown at the root.
  for (const d of departments) {
    if (d.parentId && !ids.has(d.parentId) && !result.some((r) => r.id === d.id)) {
      result.push({ ...d, depth: 0 });
    }
  }
  return result;
}
