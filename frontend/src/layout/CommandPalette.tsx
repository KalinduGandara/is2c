import { Avatar, Group, Text } from '@mantine/core';
import { useDebouncedValue } from '@mantine/hooks';
import { isActionsGroup, Spotlight, type SpotlightActionData, type SpotlightActionGroupData } from '@mantine/spotlight';
import { IconKey, IconLogout, IconMoon, IconSearch, IconUserPlus } from '@tabler/icons-react';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router';
import { api, type Page } from '../api/client';
import { useCompany } from '../app/CompanyContext';
import type { NavItem } from '../app/nav';
import { useAuth } from '../auth/AuthContext';
import type { EmployeeListItem } from '../features/employees/types';
import { NAV_ICONS } from './navIcons';

const EMPLOYEE_GROUP = 'employees';

type SpotlightActions = SpotlightActionData | SpotlightActionGroupData;
const isGroup = (a: SpotlightActions): a is SpotlightActionGroupData => isActionsGroup(a);

function matchesQuery(action: SpotlightActionData, q: string) {
  const text = [action.label, action.description, ...(Array.isArray(action.keywords) ? action.keywords : [action.keywords])]
    .filter(Boolean)
    .join(' ')
    .toLowerCase();
  return q.split(/\s+/).every((word) => text.includes(word));
}

/** Ctrl/⌘+K command palette: jump to any page, run account actions and look up employees. */
export function CommandPalette({ items, onToggleTheme }: { items: NavItem[]; onToggleTheme: () => void }) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { can, logout } = useAuth();
  const { companyId } = useCompany();
  const [query, setQuery] = useState('');
  const [debounced] = useDebouncedValue(query.trim(), 250);
  const searchEmployees = can('EMPLOYEE:VIEW') && !!companyId && debounced.length >= 2;

  const { data: employees } = useQuery({
    queryKey: ['spotlight-employees', companyId, debounced],
    queryFn: () => api<Page<EmployeeListItem>>('/employees', { query: { companyId, q: debounced, size: 6, sort: 'employeeNumber,asc' } }),
    enabled: searchEmployees,
    placeholderData: keepPreviousData,
  });

  const pages: SpotlightActionGroupData = {
    group: t('spotlight.pages'),
    actions: items.map((item) => {
      const Icon = NAV_ICONS[item.key];
      return {
        id: `nav-${item.key}`,
        label: t(item.labelKey),
        description: t(`navHint.${item.key}`),
        leftSection: Icon ? <Icon size={20} stroke={1.6} /> : undefined,
        onClick: () => navigate(item.path),
      };
    }),
  };

  const commands: SpotlightActionData[] = [
    ...(can('EMPLOYEE:ADD')
      ? [{ id: 'cmd-new-employee', label: t('spotlight.newEmployee'), leftSection: <IconUserPlus size={20} stroke={1.6} />, onClick: () => navigate('/employees/new') }]
      : []),
    { id: 'cmd-theme', label: t('spotlight.toggleTheme'), keywords: ['dark', 'light', 'mode'], leftSection: <IconMoon size={20} stroke={1.6} />, onClick: onToggleTheme },
    { id: 'cmd-password', label: t('auth.changePassword'), leftSection: <IconKey size={20} stroke={1.6} />, onClick: () => navigate('/change-password') },
    { id: 'cmd-logout', label: t('auth.signOut'), leftSection: <IconLogout size={20} stroke={1.6} />, onClick: () => void logout() },
  ];

  const employeeGroup: SpotlightActionGroupData | null =
    searchEmployees && employees && employees.content.length > 0
      ? {
          group: EMPLOYEE_GROUP,
          actions: employees.content.map((e) => ({
            id: `emp-${e.id}`,
            label: `${e.firstName} ${e.lastName}`,
            description: [e.employeeNumber, e.designationName, e.departmentName].filter(Boolean).join(' · '),
            leftSection: (
              <Avatar size={30} radius="xl" color="initials" name={`${e.firstName} ${e.lastName}`} />
            ),
            onClick: () => navigate(`/employees/${e.id}`),
          })),
        }
      : null;

  const actions: SpotlightActions[] = [
    ...(employeeGroup ? [{ ...employeeGroup, group: t('spotlight.employees') }] : []),
    pages,
    { group: t('spotlight.actions'), actions: commands },
  ];

  // Employee results come pre-filtered from the server; navigation and commands are filtered locally.
  const filter = (q: string, list: SpotlightActions[]): SpotlightActions[] => {
    const needle = q.trim().toLowerCase();
    if (!needle) return list.filter((a) => !(isGroup(a) && a.group === t('spotlight.employees')));
    return list
      .map((a) => {
        if (!isGroup(a)) return matchesQuery(a, needle) ? a : null;
        if (a.group === t('spotlight.employees')) return a;
        const kept = a.actions.filter((x) => matchesQuery(x, needle));
        return kept.length ? { ...a, actions: kept } : null;
      })
      .filter((a): a is SpotlightActions => a !== null);
  };

  return (
    <Spotlight
      actions={actions}
      filter={filter}
      query={query}
      onQueryChange={setQuery}
      highlightQuery
      scrollable
      maxHeight={440}
      shortcut={['mod + K', '/']}
      nothingFound={
        <Group justify="center" gap={6} py="sm">
          <Text size="sm" c="dimmed">{t('spotlight.nothingFound')}</Text>
        </Group>
      }
      searchProps={{
        leftSection: <IconSearch size={18} stroke={1.6} />,
        placeholder: can('EMPLOYEE:VIEW') ? t('spotlight.placeholderWithEmployees') : t('spotlight.placeholder'),
        'aria-label': t('spotlight.placeholder'),
      }}
      radius="lg"
    />
  );
}
