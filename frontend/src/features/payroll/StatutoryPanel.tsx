import { Badge, Paper, Stack, Table, Text, Title } from '@mantine/core';
import { useQuery } from '@tanstack/react-query';
import { api } from '../../api/client';
import { CALC_TYPES } from './ComponentsPanel';
import { money, type PayrollComponent, type SlabTable } from './types';

/** FR-081 the company's statutory pack: its components and tables (edited on the Components / Slab tables tabs). */
export function StatutoryPanel({ companyId }: { companyId: string }) {
  const { data } = useQuery({
    queryKey: ['payroll-statutory', companyId],
    queryFn: () => api<{ pack: string; components: PayrollComponent[]; slabTables: SlabTable[] }>(`/companies/${companyId}/statutory`),
  });
  if (!data) return null;
  return (
    <Stack>
      <Text>Statutory pack: <Badge>{data.pack}</Badge></Text>
      <Text size="sm" c="dimmed">Rates and tables are seeded data and can be changed on the Components and Slab tables tabs.</Text>
      <Table withTableBorder striped>
        <Table.Thead><Table.Tr><Table.Th>Code</Table.Th><Table.Th>Name</Table.Th><Table.Th>Calculation</Table.Th><Table.Th>Rate / basis</Table.Th></Table.Tr></Table.Thead>
        <Table.Tbody>
          {data.components.map((c) => (
            <Table.Tr key={c.id}>
              <Table.Td>{c.code}</Table.Td>
              <Table.Td>{c.name}</Table.Td>
              <Table.Td>{CALC_TYPES.find((t) => t.value === c.calcType)?.label}</Table.Td>
              <Table.Td>{[c.ratePercent !== null ? `${c.ratePercent}%` : null, c.multiplier !== null ? `×${c.multiplier}` : null, c.divisor !== null ? `÷${c.divisor}` : null, c.basisRef].filter(Boolean).join(' ')}</Table.Td>
            </Table.Tr>
          ))}
        </Table.Tbody>
      </Table>
      {data.slabTables.map((t) => (
        <Paper key={t.id} withBorder p="md">
          <Title order={5} mb="xs">{t.name} ({t.code})</Title>
          <Table>
            <Table.Thead><Table.Tr><Table.Th>From</Table.Th><Table.Th>To</Table.Th><Table.Th>Rate</Table.Th></Table.Tr></Table.Thead>
            <Table.Tbody>
              {t.rows.map((r) => (
                <Table.Tr key={r.lowerBound}>
                  <Table.Td>{money(r.lowerBound)}</Table.Td>
                  <Table.Td>{r.upperBound === null ? 'and above' : money(r.upperBound)}</Table.Td>
                  <Table.Td>{r.ratePercent}%</Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        </Paper>
      ))}
    </Stack>
  );
}
