import { Group, Pagination, Paper, ScrollArea, Skeleton, Table, Text } from '@mantine/core';
import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { EmptyState } from './EmptyState';

export interface Column<T> {
  key: string;
  header: string;
  render: (row: T) => ReactNode;
  width?: number | string;
  align?: 'left' | 'right' | 'center';
}

interface Props<T> {
  columns: Column<T>[];
  rows: T[];
  rowKey: (row: T) => string;
  loading?: boolean;
  page?: number;
  totalPages?: number;
  /** Total matching records (shown in the footer when paginated). */
  totalElements?: number;
  onPageChange?: (page: number) => void;
  onRowClick?: (row: T) => void;
  /** Accessible label for clickable rows (defaults to the first column's text). */
  rowLabel?: (row: T) => string;
  emptyTitle?: string;
  emptyDescription?: ReactNode;
  emptyIcon?: ReactNode;
  emptyAction?: ReactNode;
  /** Render without the surrounding card (e.g. inside a drawer). */
  bare?: boolean;
  minWidth?: number;
}

/** Card-framed responsive table with skeleton loading, empty state and optional server-side pagination (0-based pages). */
export function DataTable<T>(props: Props<T>) {
  const { columns, rows, rowKey, loading, page, totalPages, totalElements, onPageChange, onRowClick, rowLabel, bare, minWidth = 600 } = props;
  const { t } = useTranslation();
  const showSkeleton = loading && rows.length === 0;

  const table = (
    <>
      <ScrollArea type="auto">
        <Table highlightOnHover={!!onRowClick || rows.length > 0} miw={minWidth} className="app-table" aria-busy={loading || undefined}>
          <Table.Thead>
            <Table.Tr>
              {columns.map((c) => (
                <Table.Th key={c.key} style={{ width: c.width, textAlign: c.align }}>
                  {c.header}
                </Table.Th>
              ))}
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {showSkeleton &&
              Array.from({ length: 4 }, (_, i) => (
                <Table.Tr key={`sk-${i}`}>
                  {columns.map((c) => (
                    <Table.Td key={c.key}>
                      <Skeleton h={14} w={`${50 + ((i * 17 + c.key.length * 7) % 40)}%`} radius="sm" />
                    </Table.Td>
                  ))}
                </Table.Tr>
              ))}
            {rows.map((row) => (
              <Table.Tr
                key={rowKey(row)}
                data-clickable={onRowClick ? true : undefined}
                tabIndex={onRowClick ? 0 : undefined}
                aria-label={onRowClick && rowLabel ? rowLabel(row) : undefined}
                onClick={onRowClick ? () => onRowClick(row) : undefined}
                onKeyDown={
                  onRowClick
                    ? (e) => {
                        if ((e.key === 'Enter' || e.key === ' ') && e.target === e.currentTarget) {
                          e.preventDefault();
                          onRowClick(row);
                        }
                      }
                    : undefined
                }
              >
                {columns.map((c) => (
                  <Table.Td key={c.key} style={{ textAlign: c.align }}>
                    {c.render(row)}
                  </Table.Td>
                ))}
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>
      </ScrollArea>
      {!loading && rows.length === 0 && (
        <EmptyState
          compact
          title={props.emptyTitle ?? t('app.noRecords')}
          description={props.emptyDescription}
          icon={props.emptyIcon}
          action={props.emptyAction}
        />
      )}
      {totalPages !== undefined && totalPages > 1 && onPageChange && (
        <Group justify="space-between" px="md" py="sm" style={{ borderTop: '1px solid var(--app-border)' }} wrap="wrap" gap="xs">
          <Text size="sm" c="dimmed">
            {totalElements !== undefined
              ? t('app.pageOfWithTotal', { page: (page ?? 0) + 1, pages: totalPages, total: totalElements })
              : t('app.pageOf', { page: (page ?? 0) + 1, pages: totalPages })}
          </Text>
          <Pagination size="sm" total={totalPages} value={(page ?? 0) + 1} onChange={(p) => onPageChange(p - 1)} />
        </Group>
      )}
    </>
  );

  if (bare) return table;
  return (
    <Paper withBorder style={{ overflow: 'hidden' }}>
      {table}
    </Paper>
  );
}
