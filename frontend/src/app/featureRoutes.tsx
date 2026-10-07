import type { RouteObject } from 'react-router';
import { RequirePermission } from '../components/RequirePermission';
import { AdminPage } from '../features/admin/AdminPage';
import { OrganizationPage } from '../features/org/OrganizationPage';

/** Feature modules register their pages here. */
export const featureRoutes: RouteObject[] = [
  { path: 'organization', element: <RequirePermission anyOf={['ORG:VIEW']}><OrganizationPage /></RequirePermission> },
  { path: 'admin', element: <RequirePermission anyOf={['ADMIN:VIEW', 'AUDIT:VIEW']}><AdminPage /></RequirePermission> },
];
