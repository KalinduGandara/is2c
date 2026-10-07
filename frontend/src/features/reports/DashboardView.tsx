import { Badge, Group, Paper, Progress, SimpleGrid, Stack, Text, Title } from '@mantine/core';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router';
import { api } from '../../api/client';
import { money, periodLabel } from '../payroll/types';
import type { Dashboard } from './types';

function Kpi({ label, value, color }: { label: string; value: number | string; color?: string }) {
  return (
    <Paper withBorder p="md">
      <Text size="sm" c="dimmed">{label}</Text>
      <Text fw={700} fz={28} c={color}>{value}</Text>
    </Paper>
  );
}

/** FR-096 dashboard KPIs for the selected company (today in the company's time zone). */
export function DashboardView({ companyId }: { companyId: string }) {
  const { data } = useQuery({
    queryKey: ['dashboard', companyId],
    queryFn: () => api<Dashboard>('/dashboard', { query: { companyId } }),
    refetchInterval: 60_000,
  });
  if (!data) return null;
  const departments = Object.entries(data.headcountByDepartment).sort((a, b) => b[1] - a[1]);
  const max = Math.max(1, ...departments.map(([, n]) => n));
  return (
    <Stack>
      <Text c="dimmed" size="sm">Today: {data.date}</Text>
      <SimpleGrid cols={{ base: 2, sm: 3, lg: 6 }}>
        <Kpi label="Active employees" value={data.activeEmployees} />
        <Kpi label="Present today" value={data.presentToday} color="green" />
        <Kpi label="Absent today" value={data.absentToday} color="red" />
        <Kpi label="Late arrivals today" value={data.lateToday} color="orange" />
        <Kpi label="On leave today" value={data.onLeaveToday} color="blue" />
        <Paper withBorder p="md" component={Link} to="/approvals" style={{ textDecoration: 'none', color: 'inherit' }}>
          <Text size="sm" c="dimmed">My pending approvals</Text>
          <Text fw={700} fz={28}>{data.pendingApprovals}</Text>
        </Paper>
      </SimpleGrid>
      <SimpleGrid cols={{ base: 1, md: 2 }}>
        <Paper withBorder p="md">
          <Title order={5} mb="sm">Headcount by department</Title>
          <Stack gap="xs">
            {departments.map(([name, count]) => (
              <div key={name}>
                <Group justify="space-between"><Text size="sm">{name}</Text><Text size="sm" fw={600}>{count}</Text></Group>
                <Progress value={(count / max) * 100} size="sm" />
              </div>
            ))}
            {departments.length === 0 && <Text size="sm" c="dimmed">No active employees.</Text>}
          </Stack>
        </Paper>
        {data.payroll && (
          <Paper withBorder p="md">
            <Title order={5} mb="sm">Payroll</Title>
            <Stack gap="xs">
              <Group justify="space-between">
                <Text size="sm">Payroll period</Text>
                {data.payroll.currentPeriod ? (
                  <Badge component={Link} to={`/payroll/periods/${data.payroll.currentPeriod.id}`} style={{ cursor: 'pointer' }}>
                    {`${periodLabel(data.payroll.currentPeriod)} — ${data.payroll.currentPeriod.status}`}
                  </Badge>
                ) : <Text size="sm" c="dimmed">none</Text>}
              </Group>
              <Group justify="space-between">
                <Text size="sm">OT cost{data.payroll.lastProcessedPeriod ? ` (${periodLabel(data.payroll.lastProcessedPeriod)})` : ''}</Text>
                <Text size="sm" fw={600}>{data.payroll.lastProcessedOtCost === null ? '—' : money(data.payroll.lastProcessedOtCost)}</Text>
              </Group>
            </Stack>
          </Paper>
        )}
      </SimpleGrid>
    </Stack>
  );
}
