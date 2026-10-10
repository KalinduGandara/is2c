import { Anchor, Badge, Group, Paper, Progress, RingProgress, SimpleGrid, Skeleton, Stack, Text, Title } from '@mantine/core';
import { IconBeach, IconChecklist, IconClockExclamation, IconUserCheck, IconUsers, IconUserX } from '@tabler/icons-react';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router';
import { api } from '../../api/client';
import { StatCard } from '../../components/StatCard';
import { StatusBadge } from '../../components/StatusBadge';
import { money, periodLabel } from '../payroll/types';
import type { Dashboard } from './types';

const pct = (n: number, d: number) => (d > 0 ? Math.round((n / d) * 100) : 0);

function AttendanceRing({ data }: { data: Dashboard }) {
  const { t } = useTranslation();
  const total = data.activeEmployees;
  const unmarked = Math.max(0, total - data.presentToday - data.absentToday - data.onLeaveToday);
  const parts = [
    { key: 'present', label: t('dashboard.presentToday'), value: data.presentToday, color: 'teal' },
    { key: 'leave', label: t('dashboard.onLeaveToday'), value: data.onLeaveToday, color: 'blue' },
    { key: 'absent', label: t('dashboard.absentToday'), value: data.absentToday, color: 'red' },
    { key: 'unmarked', label: t('dashboard.notMarked'), value: unmarked, color: 'gray.4' },
  ];
  return (
    <Paper withBorder p="lg" h="100%">
      <Title order={4}>{t('dashboard.attendanceRate')}</Title>
      <Group mt="md" gap="xl" wrap="nowrap" align="center">
        <RingProgress
          size={148}
          thickness={14}
          roundCaps
          sections={total > 0 ? parts.filter((p) => p.value > 0).map((p) => ({ value: (p.value / total) * 100, color: p.color, tooltip: `${p.label}: ${p.value}` })) : []}
          label={
            <Stack gap={0} align="center">
              <Text fw={700} fz={26} lh={1} className="tabular">{pct(data.presentToday, total)}%</Text>
              <Text size="xs" c="dimmed">{t('dashboard.presentToday').toLowerCase()}</Text>
            </Stack>
          }
        />
        <Stack gap={8} style={{ flex: 1 }}>
          {parts.map((p) => (
            <Group key={p.key} justify="space-between" gap="xs" wrap="nowrap">
              <Group gap={8} wrap="nowrap">
                <span aria-hidden style={{ width: 10, height: 10, borderRadius: 3, background: `var(--mantine-color-${p.color.replace('.', '-')}${p.color.includes('.') ? '' : '-filled'})` }} />
                <Text size="sm">{p.label}</Text>
              </Group>
              <Text size="sm" fw={600} className="tabular">{p.value}</Text>
            </Group>
          ))}
        </Stack>
      </Group>
    </Paper>
  );
}

function Headcount({ data }: { data: Dashboard }) {
  const { t } = useTranslation();
  const departments = Object.entries(data.headcountByDepartment).sort((a, b) => b[1] - a[1]);
  const max = Math.max(1, ...departments.map(([, n]) => n));
  return (
    <Paper withBorder p="lg" h="100%">
      <Title order={4} mb="md">{t('dashboard.headcount')}</Title>
      <Stack gap="sm">
        {departments.slice(0, 8).map(([name, count]) => (
          <div key={name}>
            <Group justify="space-between" mb={4}>
              <Text size="sm">{name}</Text>
              <Text size="sm" fw={600} className="tabular">{count}</Text>
            </Group>
            <Progress value={(count / max) * 100} size="md" radius="xl" aria-hidden />
          </div>
        ))}
        {departments.length === 0 && <Text size="sm" c="dimmed">{t('dashboard.noActive')}</Text>}
      </Stack>
    </Paper>
  );
}

function PayrollCard({ payroll }: { payroll: NonNullable<Dashboard['payroll']> }) {
  const { t } = useTranslation();
  const current = payroll.currentPeriod;
  return (
    <Paper withBorder p="lg" h="100%">
      <Group justify="space-between" mb="md">
        <Title order={4}>{t('dashboard.payroll')}</Title>
        <Anchor component={Link} to="/payroll" size="sm">{t('dashboard.viewAll')}</Anchor>
      </Group>
      <Stack gap="md">
        <div>
          <Text size="xs" c="dimmed" tt="uppercase" fw={600}>{t('dashboard.payrollPeriod')}</Text>
          {current ? (
            <Group gap="xs" mt={4}>
              <Anchor component={Link} to={`/payroll/periods/${current.id}`} fw={600}>{periodLabel(current)}</Anchor>
              <StatusBadge status={current.status} />
            </Group>
          ) : (
            <Text size="sm" c="dimmed" mt={4}>{t('dashboard.noPeriod')}</Text>
          )}
        </div>
        <div>
          <Text size="xs" c="dimmed" tt="uppercase" fw={600}>
            {t('dashboard.otCost')}{payroll.lastProcessedPeriod ? ` · ${periodLabel(payroll.lastProcessedPeriod)}` : ''}
          </Text>
          <Text fw={700} fz={22} mt={4} className="tabular">
            {payroll.lastProcessedOtCost === null ? '—' : money(payroll.lastProcessedOtCost)}
          </Text>
        </div>
      </Stack>
    </Paper>
  );
}

/** FR-096 dashboard KPIs for the selected company (today in the company's time zone). */
export function DashboardView({ companyId }: { companyId: string }) {
  const { t } = useTranslation();
  const { data } = useQuery({
    queryKey: ['dashboard', companyId],
    queryFn: () => api<Dashboard>('/dashboard', { query: { companyId } }),
    refetchInterval: 60_000,
  });
  if (!data) {
    return (
      <SimpleGrid cols={{ base: 2, sm: 3, xl: 6 }}>
        {Array.from({ length: 6 }, (_, i) => <Skeleton key={i} h={112} radius="md" />)}
      </SimpleGrid>
    );
  }
  const active = data.activeEmployees;
  return (
    <Stack gap="lg">
      <SimpleGrid cols={{ base: 2, sm: 3, xl: 6 }} spacing={{ base: 'sm', md: 'md' }}>
        <StatCard label={t('dashboard.activeEmployees')} value={active} icon={<IconUsers size={18} />} to="/employees" />
        <StatCard label={t('dashboard.presentToday')} value={data.presentToday} color="teal" icon={<IconUserCheck size={18} />}
          hint={active ? `${pct(data.presentToday, active)}% ${t('dashboard.ofActive')}` : undefined} />
        <StatCard label={t('dashboard.absentToday')} value={data.absentToday} color="red" icon={<IconUserX size={18} />} />
        <StatCard label={t('dashboard.lateToday')} value={data.lateToday} color="orange" icon={<IconClockExclamation size={18} />} />
        <StatCard label={t('dashboard.onLeaveToday')} value={data.onLeaveToday} color="blue" icon={<IconBeach size={18} />} />
        <StatCard label={t('dashboard.pendingApprovals')} value={data.pendingApprovals} color="grape" icon={<IconChecklist size={18} />} to="/approvals"
          hint={data.pendingApprovals > 0 ? <Badge size="xs" color="grape">{t('dashboard.reviewApprovals')}</Badge> : undefined} />
      </SimpleGrid>
      <SimpleGrid cols={{ base: 1, md: 2, xl: data.payroll ? 3 : 2 }} spacing={{ base: 'sm', md: 'md' }}>
        <AttendanceRing data={data} />
        <Headcount data={data} />
        {data.payroll && <PayrollCard payroll={data.payroll} />}
      </SimpleGrid>
    </Stack>
  );
}
