import { MantineProvider, createTheme } from '@mantine/core';
import { ModalsProvider } from '@mantine/modals';
import { Notifications } from '@mantine/notifications';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { createBrowserRouter, RouterProvider } from 'react-router';
import { CompanyProvider } from './app/CompanyContext';
import { routes } from './app/router';
import { AuthProvider } from './auth/AuthContext';
import './i18n';

const theme = createTheme({ primaryColor: 'indigo', defaultRadius: 'sm' });
const queryClient = new QueryClient({ defaultOptions: { queries: { retry: 1, refetchOnWindowFocus: false } } });
const router = createBrowserRouter(routes);

export function App() {
  return (
    <MantineProvider theme={theme}>
      <Notifications position="top-right" />
      <ModalsProvider>
        <QueryClientProvider client={queryClient}>
          <AuthProvider>
            <CompanyProvider>
              <RouterProvider router={router} />
            </CompanyProvider>
          </AuthProvider>
        </QueryClientProvider>
      </ModalsProvider>
    </MantineProvider>
  );
}
