import { MantineProvider, Title } from '@mantine/core';

export function App() {
  return (
    <MantineProvider>
      <Title order={1}>Premium HRM</Title>
    </MantineProvider>
  );
}
