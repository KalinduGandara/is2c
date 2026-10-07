import { Checkbox, ScrollArea, Table } from '@mantine/core';
import type { PermissionCatalog } from './types';

interface Props {
  catalog: PermissionCatalog;
  value: string[];
  onChange: (value: string[]) => void;
  disabled?: boolean;
}

/** Module × action grid of permissions (FR-005). */
export function PermissionMatrix({ catalog, value, onChange, disabled }: Props) {
  const toggle = (permission: string, checked: boolean) =>
    onChange(checked ? [...value, permission] : value.filter((p) => p !== permission));
  return (
    <ScrollArea type="auto">
      <Table withTableBorder withColumnBorders miw={640}>
        <Table.Thead>
          <Table.Tr>
            <Table.Th>Module</Table.Th>
            {catalog.actions.map((a) => (
              <Table.Th key={a} ta="center">
                {a}
              </Table.Th>
            ))}
          </Table.Tr>
        </Table.Thead>
        <Table.Tbody>
          {catalog.modules.map((m) => (
            <Table.Tr key={m}>
              <Table.Td>{m}</Table.Td>
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
          ))}
        </Table.Tbody>
      </Table>
    </ScrollArea>
  );
}
