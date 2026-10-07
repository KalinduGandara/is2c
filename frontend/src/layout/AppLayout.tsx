import { AppShell, Burger, Group, Menu, NavLink, Select, Text, UnstyledButton } from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import { IconChevronDown, IconKey, IconLogout } from '@tabler/icons-react';
import { useTranslation } from 'react-i18next';
import { Navigate, Outlet, useLocation, useNavigate } from 'react-router';
import { useCompany } from '../app/CompanyContext';
import { visibleNav } from '../app/nav';
import { useAuth } from '../auth/AuthContext';

export function AppLayout() {
  const { t } = useTranslation();
  const { user, logout } = useAuth();
  const { companies, companyId, setCompanyId } = useCompany();
  const [opened, { toggle, close }] = useDisclosure();
  const navigate = useNavigate();
  const location = useLocation();

  if (user?.mustChangePassword) return <Navigate to="/change-password" replace />;

  const items = visibleNav(user);
  const isActive = (path: string) => (path === '/' ? location.pathname === '/' : location.pathname.startsWith(path));

  return (
    <AppShell header={{ height: 56 }} navbar={{ width: 240, breakpoint: 'sm', collapsed: { mobile: !opened } }} padding="md">
      <AppShell.Header>
        <Group h="100%" px="md" justify="space-between" wrap="nowrap">
          <Group gap="sm" wrap="nowrap">
            <Burger opened={opened} onClick={toggle} hiddenFrom="sm" size="sm" aria-label="Toggle navigation" />
            <Text fw={700}>{t('app.title')}</Text>
          </Group>
          <Group gap="sm" wrap="nowrap">
            {companies.length > 1 && (
              <Select
                aria-label={t('app.company')}
                size="xs"
                w={160}
                data={companies.map((c) => ({ value: c.id, label: c.code }))}
                value={companyId}
                onChange={(v) => v && setCompanyId(v)}
                allowDeselect={false}
              />
            )}
            <Menu position="bottom-end">
              <Menu.Target>
                <UnstyledButton aria-label="User menu">
                  <Group gap={4} wrap="nowrap">
                    <Text size="sm" visibleFrom="xs">
                      {user?.displayName}
                    </Text>
                    <IconChevronDown size={14} />
                  </Group>
                </UnstyledButton>
              </Menu.Target>
              <Menu.Dropdown>
                <Menu.Item leftSection={<IconKey size={14} />} onClick={() => navigate('/change-password')}>
                  {t('auth.changePassword')}
                </Menu.Item>
                <Menu.Item leftSection={<IconLogout size={14} />} onClick={() => void logout()}>
                  {t('auth.signOut')}
                </Menu.Item>
              </Menu.Dropdown>
            </Menu>
          </Group>
        </Group>
      </AppShell.Header>
      <AppShell.Navbar p="xs">
        {items.map((item) => (
          <NavLink
            key={item.key}
            label={t(item.labelKey)}
            active={isActive(item.path)}
            onClick={() => {
              navigate(item.path);
              close();
            }}
          />
        ))}
      </AppShell.Navbar>
      <AppShell.Main>
        <Outlet />
      </AppShell.Main>
    </AppShell>
  );
}
