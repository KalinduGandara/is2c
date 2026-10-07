import type { ScopeType } from '../../auth/types';

export interface User {
  id: string;
  username: string;
  email: string | null;
  displayName: string;
  employeeId: string | null;
  active: boolean;
  mustChangePassword: boolean;
  locked: boolean;
  roles: string[];
  scopeType: ScopeType;
  scopeCompanyIds: string[];
  scopeBranchIds: string[];
  scopeDepartmentIds: string[];
  lastLoginAt: string | null;
  version: number;
}

export interface Role {
  id: string;
  code: string;
  name: string;
  description: string | null;
  system: boolean;
  permissions: string[];
  version: number;
}

export interface PermissionCatalog {
  modules: string[];
  actions: string[];
}
