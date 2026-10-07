import { Tabs } from '@mantine/core';
import { useTranslation } from 'react-i18next';
import { useAuth } from '../../auth/AuthContext';
import { PageHeader } from '../../components/PageHeader';
import { AuditLogPanel } from './AuditLogPanel';
import { RolesPanel } from './RolesPanel';
import { UsersPanel } from './UsersPanel';

export function AdminPage() {
  const { t } = useTranslation();
  const { can } = useAuth();
  const first = can('ADMIN:VIEW') ? 'users' : 'audit';
  return (
    <>
      <PageHeader title={t('nav.administration')} />
      <Tabs defaultValue={first} keepMounted={false}>
        <Tabs.List mb="md">
          {can('ADMIN:VIEW') && <Tabs.Tab value="users">Users</Tabs.Tab>}
          {can('ADMIN:VIEW') && <Tabs.Tab value="roles">Roles & permissions</Tabs.Tab>}
          {can('AUDIT:VIEW') && <Tabs.Tab value="audit">Audit log</Tabs.Tab>}
        </Tabs.List>
        <Tabs.Panel value="users"><UsersPanel /></Tabs.Panel>
        <Tabs.Panel value="roles"><RolesPanel /></Tabs.Panel>
        <Tabs.Panel value="audit"><AuditLogPanel /></Tabs.Panel>
      </Tabs>
    </>
  );
}
