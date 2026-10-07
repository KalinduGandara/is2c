import { ScrollArea, Stack, Table, Text } from '@mantine/core';
import { money } from '../payroll/types';
import type { Cell, ReportTable, TableColumn } from './types';

const show = (v: Cell, c: TableColumn) => (v === null ? '' : c.type === 'AMOUNT' && typeof v === 'number' ? money(v) : String(v));

export function ReportTableView({ table }: { table: ReportTable }) {
  return (
    <Stack gap={4}>
      <Text fw={600}>{table.title}</Text>
      {table.headerLines.map((l) => <Text key={l} size="sm" c="dimmed">{l}</Text>)}
      <ScrollArea type="auto">
        <Table striped withTableBorder miw={600}>
          <Table.Thead>
            <Table.Tr>
              {table.columns.map((c) => <Table.Th key={c.header} ta={c.type === 'TEXT' ? 'left' : 'right'}>{c.header}</Table.Th>)}
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {table.rows.map((r, i) => (
              <Table.Tr key={i}>
                {table.columns.map((c, j) => <Table.Td key={c.header} ta={c.type === 'TEXT' ? 'left' : 'right'}>{show(r[j], c)}</Table.Td>)}
              </Table.Tr>
            ))}
            {table.footer && (
              <Table.Tr fw={600}>
                {table.columns.map((c, j) => <Table.Td key={c.header} ta={c.type === 'TEXT' ? 'left' : 'right'}>{show(table.footer![j], c)}</Table.Td>)}
              </Table.Tr>
            )}
          </Table.Tbody>
        </Table>
      </ScrollArea>
      {table.rows.length === 0 && <Text size="sm" c="dimmed">No rows.</Text>}
    </Stack>
  );
}
