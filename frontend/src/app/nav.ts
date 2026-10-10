import type { Me } from '../auth/types';

export interface NavItem {
  key: string;
  labelKey: string;
  path: string;
  /** Visible when the user holds any of these permissions; empty = any authenticated user. */
  anyOf: string[];
  /** Requires the user to be linked to an employee record. */
  requiresEmployee?: boolean;
  /** Sidebar section the item is listed under. */
  group: NavGroup;
}

export type NavGroup = 'overview' | 'people' | 'time' | 'pay' | 'insights';
export const NAV_GROUPS: NavGroup[] = ['overview', 'people', 'time', 'pay', 'insights'];

export const NAV_ITEMS: NavItem[] = [
  { key: 'dashboard', labelKey: 'nav.dashboard', path: '/', anyOf: [], group: 'overview' },
  { key: 'self', labelKey: 'nav.selfService', path: '/me', anyOf: [], requiresEmployee: true, group: 'overview' },
  { key: 'approvals', labelKey: 'nav.approvals', path: '/approvals', anyOf: [], group: 'overview' },
  { key: 'employees', labelKey: 'nav.employees', path: '/employees', anyOf: ['EMPLOYEE:VIEW'], group: 'people' },
  { key: 'organization', labelKey: 'nav.organization', path: '/organization', anyOf: ['ORG:VIEW'], group: 'people' },
  { key: 'attendance', labelKey: 'nav.attendance', path: '/attendance', anyOf: ['ATTENDANCE:VIEW'], group: 'time' },
  { key: 'shifts', labelKey: 'nav.shifts', path: '/shifts', anyOf: ['SHIFT:VIEW', 'ROSTER:VIEW'], group: 'time' },
  { key: 'leave', labelKey: 'nav.leave', path: '/leave', anyOf: ['LEAVE:VIEW'], group: 'time' },
  { key: 'payroll', labelKey: 'nav.payroll', path: '/payroll', anyOf: ['PAYROLL:VIEW'], group: 'pay' },
  { key: 'reports', labelKey: 'nav.reports', path: '/reports', anyOf: ['REPORT:VIEW', 'AUDIT:VIEW'], group: 'insights' },
  { key: 'administration', labelKey: 'nav.administration', path: '/admin', anyOf: ['ADMIN:VIEW', 'AUDIT:VIEW'], group: 'insights' },
];

export function visibleNav(user: Me | null): NavItem[] {
  if (!user) return [];
  return NAV_ITEMS.filter(
    (item) =>
      (item.anyOf.length === 0 || item.anyOf.some((p) => user.permissions.includes(p))) &&
      (!item.requiresEmployee || !!user.employeeId),
  );
}
