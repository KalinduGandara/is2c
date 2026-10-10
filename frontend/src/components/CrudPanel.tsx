import { ActionIcon, Button, Drawer, Group, Text, TextInput, Tooltip } from '@mantine/core';
import { modals } from '@mantine/modals';
import { IconPencil, IconPlus, IconSearch, IconTrash } from '@tabler/icons-react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { DataTable, type Column } from './DataTable';
import { EntityForm, type FieldSpec, type FormValues } from './EntityForm';
import { showError, showSuccess } from './notify';

export interface CrudPanelProps<T> {
  queryKey: unknown[];
  load: () => Promise<T[]>;
  rowKey: (row: T) => string;
  columns: Column<T>[];
  fields: FieldSpec[] | ((row: T | null) => FieldSpec[]);
  emptyValues: FormValues;
  toValues: (row: T) => FormValues;
  create?: (values: FormValues) => Promise<unknown>;
  update?: (row: T, values: FormValues) => Promise<unknown>;
  remove?: (row: T) => Promise<unknown>;
  entityName: string;
  extraActions?: (row: T) => React.ReactNode;
  /** Short explanation shown in the toolbar. */
  description?: string;
}

/** Lists that grow beyond this many rows get a quick filter. */
const SEARCH_THRESHOLD = 6;

function matches(row: unknown, q: string): boolean {
  if (!row || typeof row !== 'object') return false;
  return Object.values(row as Record<string, unknown>).some(
    (v) => (typeof v === 'string' || typeof v === 'number') && String(v).toLowerCase().includes(q),
  );
}

/** List + create/edit drawer + delete confirmation for a configuration entity. */
export function CrudPanel<T>(props: CrudPanelProps<T>) {
  const { t } = useTranslation();
  const client = useQueryClient();
  const [editing, setEditing] = useState<{ row: T | null } | null>(null);
  const [q, setQ] = useState('');
  const { data = [], isLoading } = useQuery({ queryKey: props.queryKey, queryFn: props.load });
  // Invalidate every query of this entity type (e.g. option lists keyed by the same prefix).
  const invalidate = () => client.invalidateQueries({ queryKey: props.queryKey.slice(0, 1) });

  const removeMutation = useMutation({
    mutationFn: (row: T) => props.remove!(row),
    onSuccess: () => {
      showSuccess(t('app.deleted'));
      void invalidate();
    },
    onError: showError,
  });

  const actions: Column<T> | null =
    props.update || props.remove || props.extraActions
      ? {
          key: '__actions',
          header: '',
          width: 110,
          align: 'right',
          render: (row) => (
            <Group gap={4} wrap="nowrap" justify="flex-end" onClick={(e) => e.stopPropagation()}>
              {props.extraActions?.(row)}
              {props.update && (
                <Tooltip label={t('app.edit')}>
                  <ActionIcon variant="subtle" color="gray" aria-label={t('app.edit')} onClick={() => setEditing({ row })}>
                    <IconPencil size={16} />
                  </ActionIcon>
                </Tooltip>
              )}
              {props.remove && (
                <Tooltip label={t('app.delete')}>
                  <ActionIcon
                    variant="subtle"
                    color="red"
                    aria-label={t('app.delete')}
                    onClick={() =>
                      modals.openConfirmModal({
                        title: t('app.deleteEntity', { entity: props.entityName }),
                        children: <Text size="sm">{t('app.confirmDelete')}</Text>,
                        labels: { confirm: t('app.delete'), cancel: t('app.cancel') },
                        confirmProps: { color: 'red' },
                        onConfirm: () => removeMutation.mutate(row),
                      })
                    }
                  >
                    <IconTrash size={16} />
                  </ActionIcon>
                </Tooltip>
              )}
            </Group>
          ),
        }
      : null;

  const fields = typeof props.fields === 'function' ? props.fields(editing?.row ?? null) : props.fields;
  const query = q.trim().toLowerCase();
  const rows = query ? data.filter((r) => matches(r, query)) : data;
  const showSearch = data.length >= SEARCH_THRESHOLD;
  const createButton = props.create && (
    <Button leftSection={<IconPlus size={16} />} onClick={() => setEditing({ row: null })}>
      {t('app.create')} {props.entityName}
    </Button>
  );

  return (
    <>
      {(props.create || showSearch || props.description) && (
        <Group justify="space-between" mb="sm" gap="sm" wrap="wrap">
          <Group gap="sm" wrap="wrap" style={{ flex: 1, minWidth: 0 }}>
            {showSearch && (
              <TextInput
                leftSection={<IconSearch size={14} />}
                placeholder={t('app.filterPlaceholder')}
                aria-label={t('app.search')}
                value={q}
                onChange={(e) => setQ(e.currentTarget.value)}
                w={{ base: '100%', xs: 260 }}
              />
            )}
            {props.description && (
              <Text size="sm" c="dimmed">
                {props.description}
              </Text>
            )}
          </Group>
          {createButton}
        </Group>
      )}
      <DataTable
        columns={actions ? [...props.columns, actions] : props.columns}
        rows={rows}
        rowKey={props.rowKey}
        loading={isLoading}
        onRowClick={props.update ? (row) => setEditing({ row }) : undefined}
        emptyTitle={query ? t('app.noMatches') : t('app.nothingYet', { entity: props.entityName })}
        emptyDescription={query ? t('app.tryDifferentSearch') : props.create ? t('app.createFirst', { entity: props.entityName }) : undefined}
      />
      <Drawer
        opened={editing !== null}
        onClose={() => setEditing(null)}
        title={`${editing?.row ? t('app.edit') : t('app.create')} ${props.entityName}`}
        position="right"
        size="md"
      >
        {editing && (
          <EntityForm
            fields={fields}
            initialValues={editing.row ? props.toValues(editing.row) : props.emptyValues}
            onCancel={() => setEditing(null)}
            onSubmit={async (values) => {
              if (editing.row) await props.update!(editing.row, values);
              else await props.create!(values);
              showSuccess();
              setEditing(null);
              await invalidate();
            }}
          />
        )}
      </Drawer>
    </>
  );
}
