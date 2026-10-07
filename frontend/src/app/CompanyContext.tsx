import { createContext, useContext, useMemo, useState, type ReactNode } from 'react';
import { useQuery } from '@tanstack/react-query';
import { api } from '../api/client';
import { useAuth } from '../auth/AuthContext';

export interface Company {
  id: string;
  code: string;
  legalName: string;
  currency: string;
  timeZone: string;
  statutoryPack: string;
  version: number;
}

interface CompanyValue {
  companies: Company[];
  companyId: string | null;
  company: Company | null;
  setCompanyId: (id: string) => void;
}

const CompanyContext = createContext<CompanyValue | null>(null);
const KEY = 'hrm.companyId';

export function CompanyProvider({ children }: { children: ReactNode }) {
  const { can, status } = useAuth();
  const enabled = status === 'authenticated' && can('ORG:VIEW');
  const { data: companies = [] } = useQuery({
    queryKey: ['companies'],
    queryFn: () => api<Company[]>('/companies'),
    enabled,
  });
  const [companyId, setCompanyIdState] = useState<string | null>(() => {
    try {
      return localStorage.getItem(KEY);
    } catch {
      return null;
    }
  });

  // Fall back to the first accessible company when the remembered one is not available.
  const effectiveId = companies.some((c) => c.id === companyId) ? companyId : (companies[0]?.id ?? null);

  const value = useMemo<CompanyValue>(() => {
    const setCompanyId = (id: string) => {
      setCompanyIdState(id);
      try {
        localStorage.setItem(KEY, id);
      } catch {
        // per-viewer convenience only
      }
    };
    return {
      companies,
      companyId: effectiveId,
      company: companies.find((c) => c.id === effectiveId) ?? null,
      setCompanyId,
    };
  }, [companies, effectiveId]);

  return <CompanyContext.Provider value={value}>{children}</CompanyContext.Provider>;
}

export function useCompany(): CompanyValue {
  const ctx = useContext(CompanyContext);
  if (!ctx) throw new Error('useCompany must be used inside CompanyProvider');
  return ctx;
}
