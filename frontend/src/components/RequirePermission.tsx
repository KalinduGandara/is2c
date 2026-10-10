import { Button, Paper } from '@mantine/core';
import { IconLock } from '@tabler/icons-react';
import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router';
import { useAuth } from '../auth/AuthContext';
import { EmptyState } from './EmptyState';

export function RequirePermission({ anyOf, children }: { anyOf: string[]; children: ReactNode }) {
  const { can } = useAuth();
  const { t } = useTranslation();
  if (anyOf.length > 0 && !can(...anyOf)) {
    return (
      <Paper withBorder role="alert">
        <EmptyState
          icon={<IconLock size={28} stroke={1.5} />}
          title={t('app.noPermission')}
          description={t('app.noPermissionHint')}
          action={<Button component={Link} to="/" variant="light">{t('app.goHome')}</Button>}
        />
      </Paper>
    );
  }
  return <>{children}</>;
}
