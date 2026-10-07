import { departmentTreeRows } from './useOrgData';
import type { Department } from './types';

const dep = (id: string, code: string, parentId: string | null): Department => ({
  id, code, name: code, parentId, branchId: null, companyId: 'c', costCenterCode: null, managerEmployeeId: null, version: 0, children: [],
});

describe('departmentTreeRows', () => {
  it('FR-012 orders departments depth-first with depth for indentation', () => {
    const rows = departmentTreeRows([dep('3', 'SHIFT', '2'), dep('1', 'OPS', null), dep('2', 'WH', '1'), dep('4', 'FIN', null)]);
    expect(rows.map((r) => `${r.code}:${r.depth}`)).toEqual(['OPS:0', 'WH:1', 'SHIFT:2', 'FIN:0']);
  });

  it('shows departments whose parent is not visible at the root', () => {
    expect(departmentTreeRows([dep('2', 'WH', 'hidden')]).map((r) => r.depth)).toEqual([0]);
  });
});
