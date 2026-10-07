import { Center, Loader } from '@mantine/core';
import type { ReactNode } from 'react';
import { Navigate, type RouteObject } from 'react-router';
import { useAuth } from '../auth/AuthContext';
import { AppLayout } from '../layout/AppLayout';
import { ChangePasswordPage } from '../pages/ChangePasswordPage';
import { DashboardPage } from '../pages/DashboardPage';
import { LoginPage } from '../pages/LoginPage';
import { NotFoundPage } from '../pages/NotFoundPage';
import { featureRoutes } from './featureRoutes';

function RequireAuth({ children }: { children: ReactNode }) {
  const { status } = useAuth();
  if (status === 'loading') {
    return (
      <Center mih="100vh">
        <Loader />
      </Center>
    );
  }
  if (status === 'anonymous') return <Navigate to="/login" replace />;
  return <>{children}</>;
}

export const routes: RouteObject[] = [
  { path: '/login', element: <LoginPage /> },
  {
    path: '/change-password',
    element: (
      <RequireAuth>
        <ChangePasswordPage />
      </RequireAuth>
    ),
  },
  {
    path: '/',
    element: (
      <RequireAuth>
        <AppLayout />
      </RequireAuth>
    ),
    children: [{ index: true, element: <DashboardPage /> }, ...featureRoutes, { path: '*', element: <NotFoundPage /> }],
  },
];
