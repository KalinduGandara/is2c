import { Box, Center, Group, List, Paper, SimpleGrid, Stack, Text, ThemeIcon, Title } from '@mantine/core';
import { IconCalendarCheck, IconChecklist, IconReportMoney } from '@tabler/icons-react';
import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { BrandMark } from '../layout/BrandMark';

/** Split-screen frame for sign-in and password screens: brand story on the left, form card on the right. */
export function AuthShell({ children }: { children: ReactNode }) {
  const { t } = useTranslation();
  const points = [
    { icon: IconCalendarCheck, text: t('auth.heroPoint1') },
    { icon: IconChecklist, text: t('auth.heroPoint2') },
    { icon: IconReportMoney, text: t('auth.heroPoint3') },
  ];
  return (
    <SimpleGrid cols={{ base: 1, md: 2 }} spacing={0} mih="100vh">
      <Box className="app-auth-hero" visibleFrom="md" p={56} style={{ display: 'flex', flexDirection: 'column', justifyContent: 'space-between' }}>
        <BrandMark inverted />
        <Stack gap="xl" maw={460}>
          <Title order={1} c="white" fz={40} lh={1.15} style={{ letterSpacing: '-0.02em' }}>
            {t('auth.heroTitle')}
          </Title>
          <Text size="lg" c="rgba(255,255,255,0.8)">{t('auth.heroSubtitle')}</Text>
          <List spacing="md" center>
            {points.map(({ icon: Icon, text }) => (
              <List.Item key={text} icon={<ThemeIcon variant="white" color="brand" radius="xl" size={30}><Icon size={16} /></ThemeIcon>}>
                <Text c="white" fw={500}>{text}</Text>
              </List.Item>
            ))}
          </List>
        </Stack>
        <Text size="xs" c="rgba(255,255,255,0.6)">© {new Date().getFullYear()} {t('app.title')}</Text>
      </Box>
      <Center p={{ base: 'md', sm: 'xl' }} bg="var(--app-bg)">
        <Stack w="100%" maw={420} gap="lg">
          <Group hiddenFrom="md" justify="center">
            <BrandMark />
          </Group>
          <Paper withBorder shadow="md" p={{ base: 'lg', sm: 32 }} radius="lg">
            {children}
          </Paper>
        </Stack>
      </Center>
    </SimpleGrid>
  );
}
