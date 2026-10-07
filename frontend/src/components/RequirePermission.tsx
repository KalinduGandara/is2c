import { Alert } from '@mantine/core';
import type { ReactNode } from 'react';
import { useAuth } from '../auth/AuthContext';

export function RequirePermission({ anyOf, children }: { anyOf: string[]; children: ReactNode }) {
  const { can } = useAuth();
  if (anyOf.length > 0 && !can(...anyOf)) {
    return <Alert color="red">You do not have permission to view this page.</Alert>;
  }
  return <>{children}</>;
}
