import type { Me } from '../auth/types';

export interface NavItem {
  key: string;
  labelKey: string;
  path: string;
  /** Visible when the user holds any of these permissions; empty = any authenticated user. */
  anyOf: string[];
  /** Requires the user to be linked to an employee record. */
  requiresEmployee?: boolean;
}

export const NAV_ITEMS: NavItem[] = [
  { key: 'dashboard', labelKey: 'nav.dashboard', path: '/', anyOf: [] },
  { key: 'self', labelKey: 'nav.selfService', path: '/me', anyOf: [], requiresEmployee: true },
  { key: 'employees', labelKey: 'nav.employees', path: '/employees', anyOf: ['EMPLOYEE:VIEW'] },
  { key: 'organization', labelKey: 'nav.organization', path: '/organization', anyOf: ['ORG:VIEW'] },
  { key: 'attendance', labelKey: 'nav.attendance', path: '/attendance', anyOf: ['ATTENDANCE:VIEW'] },
  { key: 'shifts', labelKey: 'nav.shifts', path: '/shifts', anyOf: ['SHIFT:VIEW', 'ROSTER:VIEW'] },
  { key: 'leave', labelKey: 'nav.leave', path: '/leave', anyOf: ['LEAVE:VIEW'] },
  { key: 'payroll', labelKey: 'nav.payroll', path: '/payroll', anyOf: ['PAYROLL:VIEW'] },
  { key: 'approvals', labelKey: 'nav.approvals', path: '/approvals', anyOf: [] },
  { key: 'reports', labelKey: 'nav.reports', path: '/reports', anyOf: ['REPORT:VIEW', 'AUDIT:VIEW'] },
  { key: 'administration', labelKey: 'nav.administration', path: '/admin', anyOf: ['ADMIN:VIEW', 'AUDIT:VIEW'] },
];

export function visibleNav(user: Me | null): NavItem[] {
  if (!user) return [];
  return NAV_ITEMS.filter(
    (item) =>
      (item.anyOf.length === 0 || item.anyOf.some((p) => user.permissions.includes(p))) &&
      (!item.requiresEmployee || !!user.employeeId),
  );
}
