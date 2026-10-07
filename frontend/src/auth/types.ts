export type ScopeType = 'ALL' | 'COMPANY' | 'BRANCH' | 'DEPARTMENT' | 'OWN';

export interface Me {
  id: string;
  username: string;
  displayName: string;
  employeeId: string | null;
  mustChangePassword: boolean;
  roles: string[];
  permissions: string[];
  scopeType: ScopeType;
  scopeCompanyIds: string[];
  scopeBranchIds: string[];
  scopeDepartmentIds: string[];
}
