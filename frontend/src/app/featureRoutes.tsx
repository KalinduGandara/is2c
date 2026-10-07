import type { RouteObject } from 'react-router';
import { RequirePermission } from '../components/RequirePermission';
import { AdminPage } from '../features/admin/AdminPage';
import { ApprovalsPage } from '../features/approvals/ApprovalsPage';
import { AttendancePage } from '../features/attendance/AttendancePage';
import { EmployeeDetailPage } from '../features/employees/EmployeeDetailPage';
import { EmployeesPage } from '../features/employees/EmployeesPage';
import { LeavePage } from '../features/leave/LeavePage';
import { OrganizationPage } from '../features/org/OrganizationPage';
import { ShiftsPage } from '../features/shifts/ShiftsPage';

/** Feature modules register their pages here. */
export const featureRoutes: RouteObject[] = [
  { path: 'employees', element: <RequirePermission anyOf={['EMPLOYEE:VIEW']}><EmployeesPage /></RequirePermission> },
  { path: 'employees/:id', element: <RequirePermission anyOf={['EMPLOYEE:VIEW']}><EmployeeDetailPage /></RequirePermission> },
  { path: 'organization', element: <RequirePermission anyOf={['ORG:VIEW']}><OrganizationPage /></RequirePermission> },
  { path: 'shifts', element: <RequirePermission anyOf={['SHIFT:VIEW', 'ROSTER:VIEW']}><ShiftsPage /></RequirePermission> },
  { path: 'attendance', element: <RequirePermission anyOf={['ATTENDANCE:VIEW']}><AttendancePage /></RequirePermission> },
  { path: 'leave', element: <RequirePermission anyOf={['LEAVE:VIEW']}><LeavePage /></RequirePermission> },
  { path: 'approvals', element: <ApprovalsPage /> },
  { path: 'admin', element: <RequirePermission anyOf={['ADMIN:VIEW', 'AUDIT:VIEW']}><AdminPage /></RequirePermission> },
];
