import { Center, Group, Loader, Pagination, ScrollArea, Table, Text } from '@mantine/core';
import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';

export interface Column<T> {
  key: string;
  header: string;
  render: (row: T) => ReactNode;
  width?: number | string;
}

interface Props<T> {
  columns: Column<T>[];
  rows: T[];
  rowKey: (row: T) => string;
  loading?: boolean;
  page?: number;
  totalPages?: number;
  onPageChange?: (page: number) => void;
  onRowClick?: (row: T) => void;
}

/** Responsive table with optional server-side pagination (pages are 0-based like the API). */
export function DataTable<T>({ columns, rows, rowKey, loading, page, totalPages, onPageChange, onRowClick }: Props<T>) {
  const { t } = useTranslation();
  return (
    <>
      <ScrollArea type="auto">
        <Table striped highlightOnHover verticalSpacing="xs" miw={600}>
          <Table.Thead>
            <Table.Tr>
              {columns.map((c) => (
                <Table.Th key={c.key} style={{ width: c.width }}>
                  {c.header}
                </Table.Th>
              ))}
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {rows.map((row) => (
              <Table.Tr
                key={rowKey(row)}
                onClick={onRowClick ? () => onRowClick(row) : undefined}
                style={onRowClick ? { cursor: 'pointer' } : undefined}
              >
                {columns.map((c) => (
                  <Table.Td key={c.key}>{c.render(row)}</Table.Td>
                ))}
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>
      </ScrollArea>
      {loading && (
        <Center p="md">
          <Loader size="sm" />
        </Center>
      )}
      {!loading && rows.length === 0 && (
        <Text c="dimmed" ta="center" p="md">
          {t('app.noRecords')}
        </Text>
      )}
      {totalPages !== undefined && totalPages > 1 && onPageChange && (
        <Group justify="center" mt="sm">
          <Pagination total={totalPages} value={(page ?? 0) + 1} onChange={(p) => onPageChange(p - 1)} />
        </Group>
      )}
    </>
  );
}
