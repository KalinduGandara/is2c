import { ScrollArea, Table } from '@mantine/core';
import { IconTableOff } from '@tabler/icons-react';
import { EmptyState } from '../../components/EmptyState';
import { money } from '../payroll/types';
import type { Cell, ReportTable, TableColumn } from './types';

const show = (v: Cell, c: TableColumn) => (v === null || v === undefined ? '' : c.type === 'AMOUNT' && typeof v === 'number' ? money(v) : String(v));
const align = (c: TableColumn) => (c.type === 'TEXT' ? 'left' : 'right');
const cell = { whiteSpace: 'nowrap' } as const;

/** On-screen preview of a report: quiet header, right-aligned figures, bold footer totals. */
export function ReportTableView({ table, loading }: { table: ReportTable; loading?: boolean }) {
  return (
    <>
      <ScrollArea type="auto" style={{ borderTop: '1px solid var(--app-border)' }}>
        <Table className="app-table" striped highlightOnHover miw={600} aria-busy={loading || undefined} style={loading ? { opacity: 0.5 } : undefined}>
          <Table.Thead>
            <Table.Tr>
              {table.columns.map((c) => <Table.Th key={c.header} ta={align(c)}>{c.header}</Table.Th>)}
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {table.rows.map((r, i) => (
              <Table.Tr key={i}>
                {table.columns.map((c, j) => <Table.Td key={c.header} ta={align(c)} style={cell}>{show(r[j], c)}</Table.Td>)}
              </Table.Tr>
            ))}
          </Table.Tbody>
          {table.footer && table.rows.length > 0 && (
            <Table.Tfoot>
              <Table.Tr fw={700} style={{ borderTop: '2px solid var(--app-border)' }}>
                {table.columns.map((c, j) => <Table.Td key={c.header} ta={align(c)} style={cell}>{show(table.footer![j], c)}</Table.Td>)}
              </Table.Tr>
            </Table.Tfoot>
          )}
        </Table>
      </ScrollArea>
      {table.rows.length === 0 && (
        <EmptyState compact icon={<IconTableOff size={20} stroke={1.5} />} title="No rows" description="Nothing matches these options. Try a different date range or filter." />
      )}
    </>
  );
}
