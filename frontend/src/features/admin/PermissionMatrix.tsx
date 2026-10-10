import { Badge, Checkbox, Group, ScrollArea, Table, Text } from '@mantine/core';
import { humanize } from '../../components/format';
import type { PermissionCatalog } from './types';

interface Props {
  catalog: PermissionCatalog;
  value: string[];
  onChange: (value: string[]) => void;
  disabled?: boolean;
}

/** Module × action grid of permissions (FR-005) with a select-all toggle per module. */
export function PermissionMatrix({ catalog, value, onChange, disabled }: Props) {
  const toggle = (permission: string, checked: boolean) =>
    onChange(checked ? [...value, permission] : value.filter((p) => p !== permission));
  const toggleModule = (module: string, checked: boolean) => {
    const perms = catalog.actions.map((a) => `${module}:${a}`);
    onChange(checked ? [...value.filter((p) => !perms.includes(p)), ...perms] : value.filter((p) => !perms.includes(p)));
  };
  const total = catalog.modules.length * catalog.actions.length;
  const selected = value.filter((p) => catalog.modules.some((m) => catalog.actions.some((a) => p === `${m}:${a}`))).length;

  return (
    <div>
      <Group justify="space-between" mb="xs">
        <Text fw={600} size="sm">Permissions</Text>
        <Badge variant="light" color={selected ? 'brand' : 'gray'}>
          {selected} of {total} selected
        </Badge>
      </Group>
      <ScrollArea.Autosize type="auto" mah={520} style={{ border: '1px solid var(--app-border)', borderRadius: 'var(--mantine-radius-md)' }}>
        <Table stickyHeader highlightOnHover miw={Math.max(480, 170 + catalog.actions.length * 76)} verticalSpacing={6}>
          <Table.Thead>
            <Table.Tr>
              <Table.Th style={{ background: 'var(--app-surface)' }}>
                <Text size="xs" fw={600} c="dimmed">Module</Text>
              </Table.Th>
              {catalog.actions.map((a) => (
                <Table.Th key={a} ta="center" style={{ background: 'var(--app-surface)' }}>
                  <Text size="xs" fw={600} c="dimmed">{humanize(a)}</Text>
                </Table.Th>
              ))}
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {catalog.modules.map((m) => {
              const count = catalog.actions.filter((a) => value.includes(`${m}:${a}`)).length;
              const all = count === catalog.actions.length;
              return (
                <Table.Tr key={m}>
                  <Table.Td>
                    <Checkbox
                      size="xs"
                      label={<Text size="sm" fw={500}>{humanize(m)}</Text>}
                      aria-label={`All ${m} permissions`}
                      checked={all}
                      indeterminate={count > 0 && !all}
                      disabled={disabled}
                      onChange={(e) => toggleModule(m, e.currentTarget.checked)}
                    />
                  </Table.Td>
                  {catalog.actions.map((a) => {
                    const p = `${m}:${a}`;
                    return (
                      <Table.Td key={a} ta="center">
                        <Checkbox
                          aria-label={p}
                          checked={value.includes(p)}
                          disabled={disabled}
                          onChange={(e) => toggle(p, e.currentTarget.checked)}
                          styles={{ body: { justifyContent: 'center' } }}
                        />
                      </Table.Td>
                    );
                  })}
                </Table.Tr>
              );
            })}
          </Table.Tbody>
        </Table>
      </ScrollArea.Autosize>
    </div>
  );
}
