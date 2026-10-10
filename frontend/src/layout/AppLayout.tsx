import {
  ActionIcon,
  AppShell,
  Avatar,
  Badge,
  Box,
  Burger,
  Group,
  Menu,
  NavLink,
  ScrollArea,
  Select,
  Text,
  Tooltip,
  UnstyledButton,
  useComputedColorScheme,
  useMantineColorScheme,
} from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import { spotlight } from '@mantine/spotlight';
import { IconBuilding, IconChevronDown, IconKey, IconLogout, IconMoon, IconSearch, IconSun, IconUserCircle } from '@tabler/icons-react';
import { useQuery } from '@tanstack/react-query';
import { useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, Navigate, Outlet, ScrollRestoration, useLocation, useNavigate } from 'react-router';
import { api } from '../api/client';
import { useCompany } from '../app/CompanyContext';
import { NAV_GROUPS, visibleNav } from '../app/nav';
import { useAuth } from '../auth/AuthContext';
import { humanize } from '../components/format';
import { BrandMark } from './BrandMark';
import { CommandPalette } from './CommandPalette';
import { NAV_ICONS } from './navIcons';

/** Number of approvals waiting for the signed-in user (shares the inbox query so it refreshes after actions). */
function useInboxCount(enabled: boolean) {
  const { data } = useQuery({
    queryKey: ['approvals', '/approval/inbox'],
    queryFn: () => api<unknown[]>('/approval/inbox'),
    enabled,
    refetchInterval: 60_000,
  });
  return data?.length ?? 0;
}

export function AppLayout() {
  const { t } = useTranslation();
  const { user, logout } = useAuth();
  const { companies, companyId, company, setCompanyId } = useCompany();
  const [opened, { toggle, close }] = useDisclosure();
  const navigate = useNavigate();
  const location = useLocation();
  const { setColorScheme } = useMantineColorScheme();
  const scheme = useComputedColorScheme('light');
  const toggleScheme = () => setColorScheme(scheme === 'dark' ? 'light' : 'dark');
  const inboxCount = useInboxCount(!!user && !user.mustChangePassword);

  const items = visibleNav(user);
  const isActive = (path: string) => (path === '/' ? location.pathname === '/' : location.pathname.startsWith(path));
  const current = items.find((i) => isActive(i.path));

  useEffect(() => {
    document.title = current ? `${t(current.labelKey)} · ${t('app.title')}` : t('app.title');
  }, [current, t]);

  if (user?.mustChangePassword) return <Navigate to="/change-password" replace />;

  const roleLabel = user?.roles[0] ? humanize(user.roles[0]) : '';

  return (
    <AppShell
      header={{ height: 60 }}
      navbar={{ width: 264, breakpoint: 'sm', collapsed: { mobile: !opened } }}
      padding={{ base: 'md', sm: 'lg', lg: 'xl' }}
      layout="alt"
      styles={{
        header: { background: 'color-mix(in srgb, var(--app-bg) 85%, transparent)', backdropFilter: 'blur(8px)', borderColor: 'var(--app-border)' },
        navbar: { background: 'var(--app-surface)', borderColor: 'var(--app-border)' },
        main: { background: 'var(--app-bg)' },
      }}
    >
      <ScrollRestoration />
      <CommandPalette items={items} onToggleTheme={toggleScheme} />

      <AppShell.Header>
        <Group h="100%" px={{ base: 'sm', sm: 'lg' }} justify="space-between" wrap="nowrap" gap="sm">
          <Group gap="sm" wrap="nowrap" style={{ minWidth: 0 }}>
            <Burger opened={opened} onClick={toggle} hiddenFrom="sm" size="sm" aria-label={t('layout.toggleNav')} />
            <Box hiddenFrom="sm">
              <BrandMark />
            </Box>
            <UnstyledButton
              visibleFrom="md"
              onClick={() => spotlight.open()}
              aria-label={t('spotlight.open')}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 8,
                width: 300,
                height: 36,
                padding: '0 10px 0 12px',
                borderRadius: 'var(--mantine-radius-md)',
                border: '1px solid var(--app-border)',
                background: 'var(--app-surface)',
                color: 'var(--mantine-color-dimmed)',
              }}
            >
              <IconSearch size={16} />
              <Text size="sm" c="dimmed" style={{ flex: 1 }}>
                {t('spotlight.placeholder')}
              </Text>
              <span className="app-kbd-hint">⌘K</span>
            </UnstyledButton>
          </Group>

          <Group gap={6} wrap="nowrap">
            <Tooltip label={t('spotlight.open')}>
              <ActionIcon variant="default" size="lg" hiddenFrom="md" aria-label={t('spotlight.open')} onClick={() => spotlight.open()}>
                <IconSearch size={18} />
              </ActionIcon>
            </Tooltip>
            {companies.length > 1 ? (
              <Select
                aria-label={t('app.company')}
                leftSection={<IconBuilding size={16} />}
                w={{ base: 120, xs: 190 }}
                data={companies.map((c) => ({ value: c.id, label: c.code }))}
                renderOption={({ option }) => {
                  const c = companies.find((x) => x.id === option.value);
                  return (
                    <div>
                      <Text size="sm" fw={500}>{option.label}</Text>
                      <Text size="xs" c="dimmed" lineClamp={1}>{c?.legalName}</Text>
                    </div>
                  );
                }}
                comboboxProps={{ width: 260, position: 'bottom-end' }}
                value={companyId}
                onChange={(v) => v && setCompanyId(v)}
                allowDeselect={false}
              />
            ) : (
              company && (
                <Tooltip label={company.legalName}>
                  <Badge variant="default" size="lg" radius="md" leftSection={<IconBuilding size={14} />} visibleFrom="xs" tt="none">
                    {company.code}
                  </Badge>
                </Tooltip>
              )
            )}
            <Tooltip label={scheme === 'dark' ? t('layout.lightMode') : t('layout.darkMode')}>
              <ActionIcon variant="default" size="lg" onClick={toggleScheme} aria-label={t('spotlight.toggleTheme')}>
                {scheme === 'dark' ? <IconSun size={18} /> : <IconMoon size={18} />}
              </ActionIcon>
            </Tooltip>
            <Menu position="bottom-end" width={240}>
              <Menu.Target>
                <UnstyledButton aria-label="User menu" style={{ borderRadius: 'var(--mantine-radius-md)', padding: '4px 6px' }}>
                  <Group gap={8} wrap="nowrap">
                    <Avatar size={32} radius="xl" color="initials" name={user?.displayName} />
                    <Box visibleFrom="lg" style={{ lineHeight: 1.2 }}>
                      <Text size="sm" fw={600} lineClamp={1}>{user?.displayName}</Text>
                      <Text size="xs" c="dimmed" lineClamp={1}>{roleLabel}</Text>
                    </Box>
                    <IconChevronDown size={14} />
                  </Group>
                </UnstyledButton>
              </Menu.Target>
              <Menu.Dropdown>
                <Menu.Label>
                  <Text size="sm" fw={600} c="var(--mantine-color-text)">{user?.displayName}</Text>
                  <Text size="xs" c="dimmed">@{user?.username}{roleLabel ? ` · ${roleLabel}` : ''}</Text>
                </Menu.Label>
                <Menu.Divider />
                {user?.employeeId && (
                  <Menu.Item leftSection={<IconUserCircle size={16} />} onClick={() => navigate('/me')}>
                    {t('nav.selfService')}
                  </Menu.Item>
                )}
                <Menu.Item leftSection={<IconKey size={16} />} onClick={() => navigate('/change-password')}>
                  {t('auth.changePassword')}
                </Menu.Item>
                <Menu.Divider />
                <Menu.Item color="red" leftSection={<IconLogout size={16} />} onClick={() => void logout()}>
                  {t('auth.signOut')}
                </Menu.Item>
              </Menu.Dropdown>
            </Menu>
          </Group>
        </Group>
      </AppShell.Header>

      <AppShell.Navbar aria-label={t('layout.mainNav')}>
        <AppShell.Section px="md" h={60} style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <Link to="/" style={{ textDecoration: 'none', color: 'inherit' }} onClick={close}>
            <BrandMark />
          </Link>
          <Burger opened={opened} onClick={toggle} hiddenFrom="sm" size="sm" aria-label={t('layout.closeNav')} />
        </AppShell.Section>
        <AppShell.Section grow component={ScrollArea} px="sm" pb="md">
          {NAV_GROUPS.map((group) => {
            const groupItems = items.filter((i) => i.group === group);
            if (groupItems.length === 0) return null;
            return (
              <div key={group} role="group" aria-label={t(`navGroup.${group}`)}>
                <div className="app-nav-section">{t(`navGroup.${group}`)}</div>
                {groupItems.map((item) => {
                  const Icon = NAV_ICONS[item.key];
                  const count = item.key === 'approvals' ? inboxCount : 0;
                  return (
                    <NavLink
                      key={item.key}
                      component={Link}
                      to={item.path}
                      className="app-nav-link"
                      label={t(item.labelKey)}
                      leftSection={Icon ? <Icon size={19} stroke={1.7} /> : undefined}
                      rightSection={
                        count > 0 ? (
                          <Badge size="sm" variant="filled" circle={count < 10} aria-label={t('layout.pendingCount', { count })}>
                            {count > 99 ? '99+' : count}
                          </Badge>
                        ) : undefined
                      }
                      active={isActive(item.path)}
                      aria-current={isActive(item.path) ? 'page' : undefined}
                      onClick={close}
                    />
                  );
                })}
              </div>
            );
          })}
        </AppShell.Section>
        {company && (
          <AppShell.Section p="md" style={{ borderTop: '1px solid var(--app-border)' }}>
            <Group gap="sm" wrap="nowrap">
              <Avatar radius="md" size={36} color="brand" variant="light">
                <IconBuilding size={18} />
              </Avatar>
              <div style={{ minWidth: 0 }}>
                <Text size="sm" fw={600} lineClamp={1}>{company.legalName}</Text>
                <Text size="xs" c="dimmed">{company.code} · {company.currency} · {company.timeZone}</Text>
              </div>
            </Group>
          </AppShell.Section>
        )}
      </AppShell.Navbar>

      <AppShell.Main>
        <Box maw={1440} mx="auto">
          <Outlet />
        </Box>
      </AppShell.Main>
    </AppShell>
  );
}
