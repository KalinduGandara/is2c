import { ActionIcon, Button, Drawer, Group } from '@mantine/core';
import { modals } from '@mantine/modals';
import { IconPencil, IconPlus, IconTrash } from '@tabler/icons-react';
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
}

/** List + create/edit drawer + delete confirmation for a configuration entity. */
export function CrudPanel<T>(props: CrudPanelProps<T>) {
  const { t } = useTranslation();
  const client = useQueryClient();
  const [editing, setEditing] = useState<{ row: T | null } | null>(null);
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
          header: t('app.actions'),
          width: 120,
          render: (row) => (
            <Group gap={4} wrap="nowrap">
              {props.update && (
                <ActionIcon variant="subtle" aria-label={t('app.edit')} onClick={() => setEditing({ row })}>
                  <IconPencil size={16} />
                </ActionIcon>
              )}
              {props.remove && (
                <ActionIcon
                  variant="subtle"
                  color="red"
                  aria-label={t('app.delete')}
                  onClick={() =>
                    modals.openConfirmModal({
                      title: t('app.delete'),
                      children: t('app.confirmDelete'),
                      labels: { confirm: t('app.yes'), cancel: t('app.no') },
                      confirmProps: { color: 'red' },
                      onConfirm: () => removeMutation.mutate(row),
                    })
                  }
                >
                  <IconTrash size={16} />
                </ActionIcon>
              )}
              {props.extraActions?.(row)}
            </Group>
          ),
        }
      : null;

  const fields = typeof props.fields === 'function' ? props.fields(editing?.row ?? null) : props.fields;

  return (
    <>
      {props.create && (
        <Group justify="flex-end" mb="sm">
          <Button leftSection={<IconPlus size={16} />} onClick={() => setEditing({ row: null })}>
            {t('app.create')} {props.entityName}
          </Button>
        </Group>
      )}
      <DataTable
        columns={actions ? [...props.columns, actions] : props.columns}
        rows={data}
        rowKey={props.rowKey}
        loading={isLoading}
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
