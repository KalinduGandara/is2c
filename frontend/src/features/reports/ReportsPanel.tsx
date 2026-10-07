import { Button, Grid, Group, NavLink, Paper, Stack, Text } from '@mantine/core';
import { IconDownload } from '@tabler/icons-react';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { api, download } from '../../api/client';
import { useAuth } from '../../auth/AuthContext';
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

/** FR-095 report catalog: pick a report, fill its parameters, view on screen and export. */
export function ReportsPanel({ companyId }: { companyId: string }) {
  const { can } = useAuth();
  const [selected, setSelected] = useState<CatalogEntry | null>(null);
  const [values, setValues] = useState<Record<string, string>>({});
  const [result, setResult] = useState<ReportTable | null>(null);
  const [running, setRunning] = useState(false);
  const { data: catalog = [] } = useQuery({ queryKey: ['report-catalog'], queryFn: () => api<CatalogEntry[]>('/reports') });

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

  return (
    <Grid>
      <Grid.Col span={{ base: 12, md: 3 }}>
        <Paper withBorder p={4}>
          {catalog.map((r) => (
            <NavLink
              key={r.key}
              component="button"
              label={r.title}
              active={selected?.key === r.key}
              onClick={() => { setSelected(r); setValues(defaults(r)); setResult(null); }}
            />
          ))}
        </Paper>
      </Grid.Col>
      <Grid.Col span={{ base: 12, md: 9 }}>
        {!selected ? (
          <Text c="dimmed">Choose a report.</Text>
        ) : (
          <Stack>
            <Group align="flex-end">
              {selected.params.filter((p) => p.type !== 'company').map((p) => (
                <ParamInput key={p.name} param={p} companyId={companyId} value={values[p.name] ?? ''}
                  onChange={(v) => setValues({ ...values, [p.name]: v })} />
              ))}
              <Button disabled={!ready} loading={running} onClick={() => void run()}>Run</Button>
            </Group>
            {result && (
              <>
                {can('REPORT:EXPORT') && (
                  <Group gap="xs">
                    <Text size="sm">Export:</Text>
                    {[['pdf', 'PDF'], ['xlsx', 'Excel'], ['csv', 'CSV']].map(([f, label]) => (
                      <Button key={f} size="xs" variant="light" leftSection={<IconDownload size={14} />} onClick={() => exportAs(f)}>{label}</Button>
                    ))}
                  </Group>
                )}
                <ReportTableView table={result} />
              </>
            )}
          </Stack>
        )}
      </Grid.Col>
    </Grid>
  );
}
