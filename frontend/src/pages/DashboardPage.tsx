import { Paper, SimpleGrid, Stack, Text, ThemeIcon, Title } from '@mantine/core';
import {
  IconBeach,
  IconBuildingSkyscraper,
  IconCalendarTime,
  IconChartBar,
  IconChecklist,
  IconClockHour4,
  IconReportMoney,
  IconUserCircle,
  IconUserPlus,
  type Icon,
} from '@tabler/icons-react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router';
import { useCompany } from '../app/CompanyContext';
import { useAuth } from '../auth/AuthContext';
import { EmptyState } from '../components/EmptyState';
import { PageHeader } from '../components/PageHeader';
import { DashboardView } from '../features/reports/DashboardView';

interface QuickAction {
  key: string;
  label: string;
  hint: string;
  to: string;
  icon: Icon;
  color: string;
  show: boolean;
}

function greetingKey(hour: number) {
  if (hour < 12) return 'dashboard.greeting.morning';
  if (hour < 18) return 'dashboard.greeting.afternoon';
  return 'dashboard.greeting.evening';
}

const TODAY = new Intl.DateTimeFormat('en-GB', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });

export function DashboardPage() {
  const { t } = useTranslation();
  const { user, can } = useAuth();
  const { companyId, company } = useCompany();
  const firstName = user?.displayName.split(' ')[0] ?? '';

  const actions: QuickAction[] = [
    { key: 'approvals', label: t('dashboard.reviewApprovals'), hint: t('navHint.approvals'), to: '/approvals', icon: IconChecklist, color: 'grape', show: true },
    { key: 'self', label: t('dashboard.applyLeave'), hint: t('navHint.self'), to: '/me?tab=leave', icon: IconUserCircle, color: 'cyan', show: !!user?.employeeId },
    { key: 'add', label: t('dashboard.addEmployee'), hint: t('navHint.employees'), to: '/employees/new', icon: IconUserPlus, color: 'brand', show: can('EMPLOYEE:ADD') },
    { key: 'attendance', label: t('dashboard.viewAttendance'), hint: t('navHint.attendance'), to: '/attendance', icon: IconClockHour4, color: 'teal', show: can('ATTENDANCE:VIEW') },
    { key: 'shifts', label: t('nav.shifts'), hint: t('navHint.shifts'), to: '/shifts', icon: IconCalendarTime, color: 'indigo', show: can('SHIFT:VIEW', 'ROSTER:VIEW') },
    { key: 'leave', label: t('nav.leave'), hint: t('navHint.leave'), to: '/leave', icon: IconBeach, color: 'blue', show: can('LEAVE:VIEW') },
    { key: 'payroll', label: t('dashboard.openPayroll'), hint: t('navHint.payroll'), to: '/payroll', icon: IconReportMoney, color: 'green', show: can('PAYROLL:VIEW') },
    { key: 'reports', label: t('dashboard.viewReports'), hint: t('navHint.reports'), to: '/reports', icon: IconChartBar, color: 'orange', show: can('REPORT:VIEW') },
  ].filter((a) => a.show);

  return (
    <>
      <PageHeader
        title={t(greetingKey(new Date().getHours()), { name: firstName })}
        description={
          <>
            {company ? `${t('dashboard.subtitle', { company: company.legalName })} ` : ''}
            {TODAY.format(new Date())}
          </>
        }
      />
      <Stack gap="xl">
        {companyId ? (
          <DashboardView companyId={companyId} />
        ) : (
          can('ORG:VIEW') && (
            <Paper withBorder>
              <EmptyState
                icon={<IconBuildingSkyscraper size={28} stroke={1.5} />}
                title={t('app.selectCompanyFirst')}
                description={t('app.selectCompanyHint')}
              />
            </Paper>
          )
        )}
        {actions.length > 0 && (
          <section aria-labelledby="quick-actions">
            <Title order={4} id="quick-actions" mb="sm">{t('dashboard.quickActions')}</Title>
            <SimpleGrid cols={{ base: 1, xs: 2, md: 3, xl: 4 }} spacing={{ base: 'sm', md: 'md' }}>
              {actions.map(({ key, label, hint, to, icon: ActionIcon, color }) => (
                <Paper key={key} withBorder p="md" component={Link} to={to} className="app-hover-card">
                  <Stack gap="sm">
                    <ThemeIcon variant="light" color={color} size={38} radius="md">
                      <ActionIcon size={20} />
                    </ThemeIcon>
                    <div>
                      <Text fw={600}>{label}</Text>
                      <Text size="sm" c="dimmed" lineClamp={2}>{hint}</Text>
                    </div>
                  </Stack>
                </Paper>
              ))}
            </SimpleGrid>
          </section>
        )}
      </Stack>
    </>
  );
}
