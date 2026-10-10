import { Badge, Button, Code, Drawer, Group, Paper, ScrollArea, Select, SimpleGrid, Stack, Text, TextInput } from '@mantine/core';
import { DateInput } from '@mantine/dates';
import { IconChevronRight, IconDownload, IconFilterOff, IconHistory, IconSearch } from '@tabler/icons-react';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { api, download, type Page } from '../../api/client';
import { useAuth } from '../../auth/AuthContext';
import { DataTable } from '../../components/DataTable';
import { formatDateTime, humanize } from '../../components/format';
import { showError } from '../../components/notify';

interface AuditRow {
  id: string;
  occurredAt: string;
  username: string | null;
  action: string;
  entityType: string;
  entityId: string | null;
  reason: string | null;
  before: string | null;
  after: string | null;
}

const ENTITY_TYPES = ['EMPLOYEE', 'USER', 'ROLE', 'COMPANY', 'COMPANY_SETTINGS', 'BRANCH', 'DEPARTMENT', 'SECURITY',
  'ATTENDANCE_ADJUSTMENT', 'LEAVE_REQUEST', 'PAYROLL_PERIOD', 'PAYROLL_COMPONENT', 'SALARY_STRUCTURE', 'ROSTER'];

/** Colour by the verb in the action code (e.g. `LEAVE_APPROVE` → teal). */
function actionColor(action: string): string {
  const a = action.toUpperCase();
  if (/DELETE|REMOVE|REJECT|DEACTIVATE|REVOKE|FAIL|LOCK/.test(a)) return 'red';
  if (/CREATE|ADD|INSERT|ACTIVATE|JOIN/.test(a)) return 'green';
  if (/APPROVE|FINALI[SZ]E|PUBLISH/.test(a)) return 'teal';
  if (/UPDATE|EDIT|CHANGE|RESET/.test(a)) return 'blue';
  if (/LOGIN|LOGOUT|EXPORT|VIEW/.test(a)) return 'gray';
  return 'brand';
}

function pretty(json: string | null): string {
  if (!json) return '';
  try {
    return JSON.stringify(JSON.parse(json), null, 2);
  } catch {
    return json;
  }
}

function Snapshot({ label, value }: { label: string; value: string | null }) {
  return (
    <div style={{ minWidth: 0 }}>
      <Text size="xs" fw={600} c="dimmed" tt="uppercase" mb={4}>{label}</Text>
      {value ? (
        <ScrollArea.Autosize mah={420} type="auto">
          <Code block style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>{pretty(value)}</Code>
        </ScrollArea.Autosize>
      ) : (
        <Text size="sm" c="dimmed">—</Text>
      )}
    </div>
  );
}

/** FR-008 audit search and export. Dates are UTC days. */
export function AuditLogPanel() {
  const [entityType, setEntityType] = useState<string | null>(null);
  const [action, setAction] = useState('');
  const [from, setFrom] = useState<string | null>(null);
  const [to, setTo] = useState<string | null>(null);
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState<AuditRow | null>(null);
  const [exporting, setExporting] = useState<string | null>(null);
  const { can } = useAuth();
  const day = (d: string | null, plus = 0) => {
    if (!d || !/^\d{4}-\d{2}-\d{2}$/.test(d)) return null;
    const t = new Date(`${d}T00:00:00Z`);
    t.setUTCDate(t.getUTCDate() + plus);
    return t.toISOString();
  };
  const filters = { entityType, action: action.trim().toUpperCase(), from: day(from), to: day(to, 1) };
  const filtered = !!(entityType || action.trim() || from || to);
  const { data, isLoading } = useQuery({
    queryKey: ['audit', filters, page],
    queryFn: () => api<Page<AuditRow>>('/audit', { query: { ...filters, page, size: 25 } }),
  });
  const clear = () => {
    setEntityType(null);
    setAction('');
    setFrom(null);
    setTo(null);
    setPage(0);
  };
  const exportAs = async (format: string) => {
    setExporting(format);
    try {
      await download('/audit/export', { ...filters, format });
    } catch (e) {
      showError(e);
    } finally {
      setExporting(null);
    }
  };

  return (
    <>
      <Paper withBorder p="sm" mb="sm">
        <Group gap="sm" wrap="wrap" align="flex-end" justify="space-between">
          <Group gap="sm" wrap="wrap" align="flex-end" style={{ flex: 1, minWidth: 0 }}>
            <Select
              aria-label="Entity type"
              placeholder="Entity type"
              clearable
              searchable
              data={ENTITY_TYPES.map((v) => ({ value: v, label: humanize(v) }))}
              value={entityType}
              onChange={(v) => { setEntityType(v); setPage(0); }}
              w={{ base: '100%', sm: 170 }}
            />
            <TextInput
              aria-label="Action"
              placeholder="Action (e.g. UPDATE)"
              leftSection={<IconSearch size={14} />}
              value={action}
              onChange={(e) => { setAction(e.currentTarget.value); setPage(0); }}
              w={{ base: '100%', sm: 170 }}
            />
            <DateInput
              aria-label="From date"
              placeholder="From date"
              valueFormat="D MMM YYYY"
              clearable
              value={from}
              maxDate={to ?? undefined}
              onChange={(v) => { setFrom(v); setPage(0); }}
              w={{ base: '100%', sm: 145 }}
            />
            <DateInput
              aria-label="To date"
              placeholder="To date"
              valueFormat="D MMM YYYY"
              clearable
              value={to}
              minDate={from ?? undefined}
              onChange={(v) => { setTo(v); setPage(0); }}
              w={{ base: '100%', sm: 145 }}
            />
            {filtered && (
              <Button variant="subtle" color="gray" leftSection={<IconFilterOff size={16} />} onClick={clear}>
                Clear filters
              </Button>
            )}
          </Group>
          {can('AUDIT:EXPORT') && (
            <Group gap="xs" wrap="wrap">
              {['csv', 'xlsx', 'pdf'].map((f) => (
                <Button key={f} variant="default" size="xs" leftSection={<IconDownload size={14} />}
                  loading={exporting === f} disabled={exporting !== null && exporting !== f}
                  onClick={() => void exportAs(f)}>
                  Export {f.toUpperCase()}
                </Button>
              ))}
            </Group>
          )}
        </Group>
      </Paper>
      <DataTable
        rows={data?.content ?? []}
        loading={isLoading}
        rowKey={(r) => r.id}
        page={page}
        totalPages={data?.totalPages}
        totalElements={data?.totalElements}
        onPageChange={setPage}
        onRowClick={setSelected}
        rowLabel={(r) => `View ${humanize(r.action)} on ${humanize(r.entityType)}`}
        minWidth={920}
        emptyIcon={<IconHistory size={20} stroke={1.5} />}
        emptyTitle={filtered ? 'No matching audit entries' : 'No audit entries yet'}
        emptyDescription={filtered ? 'Try widening the date range or clearing filters.' : 'Changes to critical records will be recorded here.'}
        columns={[
          { key: 'at', header: 'When', render: (r) => <Text size="sm" style={{ whiteSpace: 'nowrap' }}>{formatDateTime(r.occurredAt)}</Text> },
          { key: 'user', header: 'User', render: (r) => r.username ?? <Text size="sm" c="dimmed">System</Text> },
          { key: 'action', header: 'Action', width: 180, render: (r) => <Badge variant="light" color={actionColor(r.action)}>{humanize(r.action)}</Badge> },
          {
            key: 'entity', header: 'Entity',
            render: (r) => (
              <div style={{ minWidth: 0 }}>
                <Text size="sm">{humanize(r.entityType)}</Text>
                {r.entityId && <Text size="xs" c="dimmed" ff="monospace" truncate maw={180}>{r.entityId}</Text>}
              </div>
            ),
          },
          { key: 'reason', header: 'Reason', render: (r) => <Text size="sm" c={r.reason ? undefined : 'dimmed'} lineClamp={2} maw={280}>{r.reason ?? '—'}</Text> },
          {
            key: 'change', header: '', width: 90, align: 'right',
            render: (r) =>
              (r.before || r.after) && (
                <Group gap={2} justify="flex-end" wrap="nowrap" c="dimmed">
                  <Text size="xs">Details</Text>
                  <IconChevronRight size={14} />
                </Group>
              ),
          },
        ]}
      />
      <Drawer
        opened={selected !== null}
        onClose={() => setSelected(null)}
        title={<Text fw={600}>Audit entry</Text>}
        position="right"
        size="xl"
      >
        {selected && (
          <Stack>
            <SimpleGrid cols={{ base: 1, xs: 2 }} spacing="sm">
              <div>
                <Text size="xs" c="dimmed">When</Text>
                <Text size="sm">{formatDateTime(selected.occurredAt)}</Text>
              </div>
              <div>
                <Text size="xs" c="dimmed">User</Text>
                <Text size="sm">{selected.username ?? 'System'}</Text>
              </div>
              <div>
                <Text size="xs" c="dimmed">Action</Text>
                <Badge variant="light" color={actionColor(selected.action)}>{humanize(selected.action)}</Badge>
              </div>
              <div>
                <Text size="xs" c="dimmed">Entity</Text>
                <Text size="sm">{humanize(selected.entityType)}</Text>
                {selected.entityId && <Text size="xs" c="dimmed" ff="monospace" style={{ wordBreak: 'break-all' }}>{selected.entityId}</Text>}
              </div>
            </SimpleGrid>
            {selected.reason && (
              <div>
                <Text size="xs" c="dimmed">Reason</Text>
                <Text size="sm">{selected.reason}</Text>
              </div>
            )}
            <SimpleGrid cols={{ base: 1, md: 2 }} spacing="md">
              <Snapshot label="Before" value={selected.before} />
              <Snapshot label="After" value={selected.after} />
            </SimpleGrid>
          </Stack>
        )}
      </Drawer>
    </>
  );
}
