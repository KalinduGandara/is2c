import { Tabs } from '@mantine/core';
import { IconHistory, IconSettings, IconShieldLock, IconUsers } from '@tabler/icons-react';
import { useTranslation } from 'react-i18next';
import { useAuth } from '../../auth/AuthContext';
import { PageHeader } from '../../components/PageHeader';
import { useUrlTab } from '../../components/useUrlTab';
import { AuditLogPanel } from './AuditLogPanel';
import { RolesPanel } from './RolesPanel';
import { UsersPanel } from './UsersPanel';

export function AdminPage() {
  const { t } = useTranslation();
  const { can } = useAuth();
  const admin = can('ADMIN:VIEW');
  const audit = can('AUDIT:VIEW');
  const allowed = [...(admin ? ['users', 'roles'] : []), ...(audit ? ['audit'] : [])];
  const [tab, setTab] = useUrlTab(admin ? 'users' : 'audit', allowed);
  return (
    <>
      <PageHeader title={t('nav.administration')} description={t('navHint.administration')} icon={<IconSettings size={22} />} />
      <Tabs value={tab} onChange={setTab} keepMounted={false}>
        <Tabs.List className="app-tabs-list" mb="lg">
          {admin && <Tabs.Tab value="users" leftSection={<IconUsers size={16} />}>Users</Tabs.Tab>}
          {admin && <Tabs.Tab value="roles" leftSection={<IconShieldLock size={16} />}>Roles & permissions</Tabs.Tab>}
          {audit && <Tabs.Tab value="audit" leftSection={<IconHistory size={16} />}>Audit log</Tabs.Tab>}
        </Tabs.List>
        {admin && <Tabs.Panel value="users"><UsersPanel /></Tabs.Panel>}
        {admin && <Tabs.Panel value="roles"><RolesPanel /></Tabs.Panel>}
        {audit && <Tabs.Panel value="audit"><AuditLogPanel /></Tabs.Panel>}
      </Tabs>
    </>
  );
}
