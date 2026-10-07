import { Text } from '@mantine/core';

/** Holiday calendar UI arrives with T-012. */
export function HolidaysPanel({ companyId }: { companyId: string }) {
  return <Text c="dimmed" data-company={companyId}>Holiday calendar is not available yet.</Text>;
}
