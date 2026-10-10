import { Paper, Skeleton, Stack } from '@mantine/core';

/** Placeholder shown while a page's primary data loads (avoids a blank screen). */
export function PageLoader({ rows = 4 }: { rows?: number }) {
  return (
    <Stack gap="md" role="status" aria-busy="true" aria-label="Loading">
      <Skeleton h={28} w={240} radius="md" />
      <Paper withBorder p="lg">
        <Stack gap="sm">
          {Array.from({ length: rows }, (_, i) => (
            <Skeleton key={i} h={16} w={`${90 - i * 12}%`} radius="sm" />
          ))}
        </Stack>
      </Paper>
    </Stack>
  );
}
