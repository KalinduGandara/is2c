import { Alert, Badge, Code, Group, Paper, ScrollArea, SimpleGrid, Skeleton, Stack, Table, Text, ThemeIcon, Title } from '@mantine/core';
import { IconInfoCircle, IconScale } from '@tabler/icons-react';
import { useQuery } from '@tanstack/react-query';
import { api } from '../../api/client';
import { DataTable } from '../../components/DataTable';
import { CALC_TYPES, formula } from './ComponentsPanel';
import { KIND_COLOR, KIND_LABEL, money, type PayrollComponent, type SlabTable } from './types';

/** FR-081 the company's statutory pack: its components and tables (edited on the Components / Slab tables tabs). */
export function StatutoryPanel({ companyId }: { companyId: string }) {
  const { data, isLoading } = useQuery({
    queryKey: ['payroll-statutory', companyId],
    queryFn: () => api<{ pack: string; components: PayrollComponent[]; slabTables: SlabTable[] }>(`/companies/${companyId}/statutory`),
  });
  if (isLoading || !data) {
    return (
      <Stack>
        <Skeleton h={72} radius="md" />
        <Skeleton h={200} radius="md" />
      </Stack>
    );
  }
  return (
    <Stack gap="lg">
      <Paper withBorder p="lg">
        <Group gap="md" wrap="nowrap" align="flex-start">
          <ThemeIcon size={42} radius="md" variant="light"><IconScale size={22} /></ThemeIcon>
          <div>
            <Text size="sm" c="dimmed">Statutory pack</Text>
            <Group gap="xs"><Title order={4}>{data.pack}</Title><Badge variant="light">{data.components.length} components · {data.slabTables.length} tables</Badge></Group>
          </div>
        </Group>
        <Alert mt="md" variant="light" color="blue" icon={<IconInfoCircle size={18} />}>
          Rates and tables are seeded data and can be changed on the Components and Slab tables tabs.
        </Alert>
      </Paper>
      <div>
        <Title order={4} mb="sm">Statutory components</Title>
        <DataTable
          rows={data.components}
          rowKey={(c) => c.id}
          emptyTitle="No statutory components"
          columns={[
            { key: 'name', header: 'Component', render: (c) => (
              <Stack gap={0}>
                <Text size="sm" fw={600}>{c.name}</Text>
                <Code fz="xs" w="fit-content">{c.code}</Code>
              </Stack>
            ) },
            { key: 'kind', header: 'Type', render: (c) => <Badge variant="light" color={KIND_COLOR[c.kind]} miw="max-content">{KIND_LABEL[c.kind]}</Badge> },
            { key: 'calc', header: 'Calculation', render: (c) => CALC_TYPES.find((t) => t.value === c.calcType)?.label },
            { key: 'rate', header: 'Rate / basis', render: (c) => <Text size="sm" className="tabular">{formula(c, data.slabTables)}</Text> },
          ]}
        />
      </div>
      {data.slabTables.length > 0 && (
        <div>
          <Title order={4} mb="sm">Statutory slab tables</Title>
          <SimpleGrid cols={{ base: 1, lg: 2 }}>
            {data.slabTables.map((t) => (
              <Paper key={t.id} withBorder style={{ overflow: 'hidden' }}>
                <Group justify="space-between" px="md" py="sm" wrap="nowrap">
                  <Text fw={600} truncate>{t.name}</Text>
                  <Code>{t.code}</Code>
                </Group>
                <ScrollArea type="auto">
                  <Table className="app-table" miw={320}>
                    <Table.Thead><Table.Tr><Table.Th ta="right">From</Table.Th><Table.Th ta="right">To</Table.Th><Table.Th ta="right">Rate</Table.Th></Table.Tr></Table.Thead>
                    <Table.Tbody>
                      {t.rows.map((r) => (
                        <Table.Tr key={r.lowerBound}>
                          <Table.Td ta="right">{money(r.lowerBound)}</Table.Td>
                          <Table.Td ta="right">{r.upperBound === null ? <Text span size="sm" c="dimmed">and above</Text> : money(r.upperBound)}</Table.Td>
                          <Table.Td ta="right"><Text span size="sm" fw={600}>{r.ratePercent}%</Text></Table.Td>
                        </Table.Tr>
                      ))}
                    </Table.Tbody>
                  </Table>
                </ScrollArea>
              </Paper>
            ))}
          </SimpleGrid>
        </div>
      )}
    </Stack>
  );
}
