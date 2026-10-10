import { Button, Grid, Group, Paper, SimpleGrid, Skeleton, Stack, Text, ThemeIcon, Title, UnstyledButton } from '@mantine/core';
import { useMediaQuery } from '@mantine/hooks';
import {
  IconBeach, IconCalendarTime, IconClockHour4, IconDownload, IconFileAnalytics, IconFileSpreadsheet, IconFileTypeCsv, IconFileTypePdf, IconPencil,
  IconPlayerPlay, IconReportMoney, IconShieldCheck, IconTable, IconUsers, type Icon,
} from '@tabler/icons-react';
import { useQuery } from '@tanstack/react-query';
import { useRef, useState } from 'react';
import { api, download } from '../../api/client';
import { useAuth } from '../../auth/AuthContext';
import { EmptyState } from '../../components/EmptyState';
import { showError } from '../../components/notify';
import { ParamInput } from './ParamInput';
import { ReportTableView } from './ReportTableView';
import type { CatalogEntry, ReportTable } from './types';

const today = () => new Date().toISOString().slice(0, 10);

function defaults(entry: CatalogEntry): Record<string, string> {
  const values: Record<string, string> = {};
  for (const p of entry.params) {
    if (p.type === 'year') values[p.name] = String(new Date().getFullYear());
    if (p.type === 'date' && p.name === 'to') values[p.name] = today();
  }
  return values;
}

interface Meta { category: string; icon: Icon; color: string; description?: string }

const CATEGORIES = ['People', 'Attendance', 'Scheduling', 'Leave', 'Payroll', 'Compliance', 'Other'];

/** Presentation only: category, icon and a one-line description per known report key. */
const META: Record<string, Meta> = {
  'employee-list': { category: 'People', icon: IconUsers, color: 'brand', description: 'Employee directory with optional filters.' },
  'daily-attendance': { category: 'Attendance', icon: IconClockHour4, color: 'teal', description: 'Attendance of every employee on one day.' },
  'attendance-summary': { category: 'Attendance', icon: IconTable, color: 'teal', description: 'Attendance totals per employee for a date range.' },
  'attendance-edits': { category: 'Attendance', icon: IconPencil, color: 'teal', description: 'Manual changes made to attendance records.' },
  roster: { category: 'Scheduling', icon: IconCalendarTime, color: 'indigo', description: 'Shift assignments of a roster.' },
  'leave-balances': { category: 'Leave', icon: IconBeach, color: 'blue', description: 'Leave entitlement, usage and balance per employee.' },
  'leave-usage': { category: 'Leave', icon: IconBeach, color: 'blue', description: 'Leave taken over a date range.' },
  'payroll-register': { category: 'Payroll', icon: IconReportMoney, color: 'green', description: 'Earnings, deductions and net pay per employee for a period.' },
  'payroll-department-summary': { category: 'Payroll', icon: IconReportMoney, color: 'green', description: 'Payroll totals by department for a period.' },
  'audit-log': { category: 'Compliance', icon: IconShieldCheck, color: 'orange', description: 'Who changed what, and when.' },
};
const metaOf = (key: string): Meta => META[key] ?? { category: 'Other', icon: IconFileAnalytics, color: 'gray' };

const EXPORTS: [string, string, Icon][] = [['pdf', 'PDF', IconFileTypePdf], ['xlsx', 'Excel', IconFileSpreadsheet], ['csv', 'CSV', IconFileTypeCsv]];

function ReportCard({ entry, active, onSelect }: { entry: CatalogEntry; active: boolean; onSelect: () => void }) {
  const meta = metaOf(entry.key);
  const descId = `report-desc-${entry.key}`;
  return (
    <UnstyledButton onClick={onSelect} aria-label={entry.title} aria-describedby={meta.description ? descId : undefined} aria-pressed={active} w="100%">
      <Paper withBorder p="sm" className="app-hover-card"
        style={active ? { borderColor: 'var(--mantine-primary-color-filled)', background: 'var(--mantine-primary-color-light)' } : undefined}>
        <Group gap="sm" wrap="nowrap" align="flex-start">
          <ThemeIcon variant="light" color={meta.color} size={34} radius="md"><meta.icon size={18} /></ThemeIcon>
          <div style={{ minWidth: 0 }}>
            <Text fw={600} size="sm">{entry.title}</Text>
            {meta.description && <Text id={descId} size="xs" c="dimmed" lineClamp={2}>{meta.description}</Text>}
          </div>
        </Group>
      </Paper>
    </UnstyledButton>
  );
}

/** FR-095 report catalog: pick a report, fill its parameters, view on screen and export. */
export function ReportsPanel({ companyId }: { companyId: string }) {
  const { can } = useAuth();
  const [selected, setSelected] = useState<CatalogEntry | null>(null);
  const [values, setValues] = useState<Record<string, string>>({});
  const [result, setResult] = useState<ReportTable | null>(null);
  const [running, setRunning] = useState(false);
  const detailRef = useRef<HTMLDivElement>(null);
  const stacked = useMediaQuery('(max-width: 62em)');
  const { data: catalog = [], isLoading } = useQuery({ queryKey: ['report-catalog'], queryFn: () => api<CatalogEntry[]>('/reports') });

  const query = () => {
    const q: Record<string, string> = {};
    for (const p of selected?.params ?? []) {
      const v = p.type === 'company' ? companyId : values[p.name];
      if (v) q[p.name] = v;
    }
    return q;
  };
  const ready = !!selected && selected.params.every((p) => !p.required || p.type === 'company' || !!values[p.name]);
  const run = async () => {
    if (!selected) return;
    setRunning(true);
    try {
      setResult(await api<ReportTable>(`/reports/${selected.key}`, { query: query() }));
    } catch (e) {
      showError(e);
    } finally {
      setRunning(false);
    }
  };
  const exportAs = (format: string) =>
    selected && void download(`/reports/${selected.key}/export`, { ...query(), format }).catch(showError);
  const select = (r: CatalogEntry) => {
    setSelected(r);
    setValues(defaults(r));
    setResult(null);
    if (stacked) requestAnimationFrame(() => detailRef.current?.scrollIntoView?.({ behavior: 'smooth', block: 'start' }));
  };

  const groups = CATEGORIES.map((category) => ({ category, entries: catalog.filter((r) => metaOf(r.key).category === category) })).filter((g) => g.entries.length > 0);
  const inputs = selected?.params.filter((p) => p.type !== 'company') ?? [];
  const meta = selected ? metaOf(selected.key) : null;

  return (
    <Grid gap="lg">
      <Grid.Col span={{ base: 12, md: 4, xl: 3 }}>
        <Stack gap="md" component="nav" aria-label="Report catalogue">
          {isLoading && Array.from({ length: 5 }, (_, i) => <Skeleton key={i} h={58} radius="md" />)}
          {!isLoading && catalog.length === 0 && (
            <Paper withBorder><EmptyState compact title="No reports available" description="You don't have access to any reports." /></Paper>
          )}
          {groups.map((g) => (
            <div key={g.category}>
              <Text size="xs" fw={700} c="dimmed" tt="uppercase" mb={6} style={{ letterSpacing: '0.04em' }}>{g.category}</Text>
              <Stack gap={6}>
                {g.entries.map((r) => <ReportCard key={r.key} entry={r} active={selected?.key === r.key} onSelect={() => select(r)} />)}
              </Stack>
            </div>
          ))}
        </Stack>
      </Grid.Col>
      <Grid.Col span={{ base: 12, md: 8, xl: 9 }} ref={detailRef}>
        {!selected || !meta ? (
          <Paper withBorder>
            <EmptyState icon={<IconFileAnalytics size={28} stroke={1.5} />} title="Choose a report"
              description="Pick a report from the catalogue to set its options, preview it on screen and export it." />
          </Paper>
        ) : (
          <Stack gap="md">
            <Paper withBorder p="lg">
              <Group gap="sm" wrap="nowrap" mb="md" align="flex-start">
                <ThemeIcon variant="light" color={meta.color} size={40} radius="md"><meta.icon size={22} /></ThemeIcon>
                <div>
                  <Title order={4}>{selected.title}</Title>
                  <Text size="sm" c="dimmed">{meta.description ?? `${meta.category} report`}</Text>
                </div>
              </Group>
              {inputs.length > 0 ? (
                <SimpleGrid cols={{ base: 1, sm: 2, lg: 3 }}>
                  {inputs.map((p) => (
                    <ParamInput key={p.name} param={p} companyId={companyId} value={values[p.name] ?? ''}
                      onChange={(v) => setValues({ ...values, [p.name]: v })} />
                  ))}
                </SimpleGrid>
              ) : (
                <Text size="sm" c="dimmed">This report has no options — it covers the selected company.</Text>
              )}
              <Group justify="flex-end" mt="md">
                <Button leftSection={<IconPlayerPlay size={16} />} disabled={!ready} loading={running} onClick={() => void run()}>Run</Button>
              </Group>
            </Paper>
            {result ? (
              <Paper withBorder style={{ overflow: 'hidden' }}>
                <Group justify="space-between" p="md" wrap="wrap" gap="xs" align="flex-start">
                  <div>
                    <Text fw={600}>{result.title}</Text>
                    {result.headerLines.map((l) => <Text key={l} size="sm" c="dimmed">{l}</Text>)}
                  </div>
                  {can('REPORT:EXPORT') && (
                    <Group gap="xs" role="group" aria-label="Export">
                      <IconDownload size={16} aria-hidden style={{ color: 'var(--mantine-color-dimmed)' }} />
                      {EXPORTS.map(([f, label, ExportIcon]) => (
                        <Button key={f} size="xs" variant="default" leftSection={<ExportIcon size={14} />} onClick={() => exportAs(f)}>{label}</Button>
                      ))}
                    </Group>
                  )}
                </Group>
                <ReportTableView table={result} loading={running} />
              </Paper>
            ) : running ? (
              <Paper withBorder p="lg">
                <Stack gap="sm" aria-busy="true">
                  {Array.from({ length: 5 }, (_, i) => <Skeleton key={i} h={14} w={`${95 - i * 10}%`} />)}
                </Stack>
              </Paper>
            ) : (
              <Paper withBorder>
                <EmptyState compact icon={<IconPlayerPlay size={20} stroke={1.5} />} title="Ready when you are"
                  description={ready ? 'Select Run to preview the report.' : 'Fill in the required options, then select Run.'} />
              </Paper>
            )}
          </Stack>
        )}
      </Grid.Col>
    </Grid>
  );
}
