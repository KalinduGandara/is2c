import { visibleNav } from './nav';
import { adminMe } from '../test/utils';
import type { Me } from '../auth/types';

describe('visibleNav', () => {
  it('FR-005 shows modules only for held permissions', () => {
    const employee: Me = { ...adminMe, permissions: [], roles: ['EMPLOYEE'], employeeId: 'e1' } as Me;
    expect(visibleNav(employee).map((i) => i.key)).toEqual(['dashboard', 'self', 'approvals']);
  });

  it('hides self-service when the user has no linked employee', () => {
    const keys = visibleNav(adminMe as Me).map((i) => i.key);
    expect(keys).not.toContain('self');
    expect(keys).toEqual(expect.arrayContaining(['employees', 'organization', 'administration', 'reports']));
    expect(keys).not.toContain('payroll');
  });

  it('returns nothing when signed out', () => {
    expect(visibleNav(null)).toEqual([]);
  });
});
