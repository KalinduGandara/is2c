import { Alert, Badge, Button, FileInput, Group, Input, Paper, SegmentedControl, Select, Stack, Text, Textarea } from '@mantine/core';
import { DatePickerInput } from '@mantine/dates';
import { IconAlertTriangle, IconCalendar, IconInfoCircle, IconPaperclip, IconSend } from '@tabler/icons-react';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { api } from '../../api/client';
import { showError, showSuccess } from '../../components/notify';
import { calendarDays, formatDateRange, formatDays, type Balance, type LeaveType } from './types';

interface Props {
  companyId: string;
  /** When set, HR applies on behalf of this employee; otherwise the caller applies for themselves. */
  employeeId?: string | null;
  onDone?: () => void;
}

/** FR-063 leave application (self-service or on behalf). */
export function ApplyLeaveForm({ companyId, employeeId, onDone }: Props) {
  const { data: types = [] } = useQuery({ queryKey: ['leave-types', companyId], queryFn: () => api<LeaveType[]>(`/companies/${companyId}/leave-types`) });
  const [typeId, setTypeId] = useState<string | null>(null);
  const [range, setRange] = useState<[string | null, string | null]>([null, null]);
  const [part, setPart] = useState('FULL');
  const [reason, setReason] = useState('');
  const [files, setFiles] = useState<File[]>([]);
  const [busy, setBusy] = useState(false);
  const type = types.find((t) => t.id === typeId);
  const single = range[0] !== null && (range[1] === null || range[0] === range[1]);
  const year = range[0] ? Number(range[0].slice(0, 4)) : new Date().getFullYear();

  // Balance of the selected type, for guidance only — the server remains the authority.
  const { data: balances } = useQuery({
    queryKey: employeeId ? ['leave-balances', employeeId, year] : ['my-balances', year],
    queryFn: () =>
      employeeId
        ? api<Balance[]>('/leave/balances', { query: { employeeId, year } })
        : api<Balance[]>('/me/leave/balances', { query: { year } }),
    enabled: !!typeId,
    retry: false,
  });
  const balance = balances?.find((b) => b.leaveTypeId === typeId);

  const halfDay = single && !!type?.allowHalfDay && part !== 'FULL';
  const requested = range[0] ? (halfDay ? 0.5 : calendarDays(range[0], range[1])) : 0;
  const excludesNonWorking = !!type && !type.unlimited && (!type.countWeeklyOff || !type.countHoliday);
  const overBalance =
    !!type && !type.unlimited && !type.allowNegative && balance?.available != null && requested > balance.available;
  const needsAttachment = type?.attachmentAboveDays != null && requested > type.attachmentAboveDays && files.length === 0;

  const submit = async () => {
    const [from, to] = range;
    if (!typeId || !from) return;
    const data = {
      employeeId: employeeId ?? undefined,
      leaveTypeId: typeId,
      fromDate: from,
      toDate: to ?? from,
      halfDayPart: single && part !== 'FULL' ? part : null,
      reason: reason || null,
    };
    const form = new FormData();
    form.append('data', new Blob([JSON.stringify(data)], { type: 'application/json' }));
    files.forEach((f) => form.append('files', f));
    setBusy(true);
    try {
      const r = await api<{ days: number; status: string }>(employeeId ? '/leave/requests' : '/me/leave/requests', { method: 'POST', body: form });
      showSuccess(`Leave for ${r.days} day(s) ${r.status === 'APPROVED' ? 'approved' : 'submitted for approval'}`);
      setRange([null, null]);
      setReason('');
      setFiles([]);
      onDone?.();
    } catch (e) {
      showError(e);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Stack gap="md">
      <Select
        label="Leave type"
        withAsterisk
        placeholder="Choose a leave type"
        nothingFoundMessage="No leave types available"
        data={types.filter((t) => t.active).map((t) => ({ value: t.id, label: t.name }))}
        value={typeId}
        onChange={setTypeId}
      />

      {type && (
        <Paper withBorder p="sm" radius="md" bg="var(--mantine-color-default-hover)">
          <Group justify="space-between" wrap="wrap" gap="xs">
            <Group gap={6}>
              <Badge variant="light" color={type.paid ? 'teal' : 'gray'}>{type.paid ? 'Paid' : 'Unpaid'}</Badge>
              {type.allowHalfDay && <Badge variant="light" color="blue">Half days allowed</Badge>}
            </Group>
            {type.unlimited ? (
              <Text size="sm" c="dimmed">No balance limit</Text>
            ) : balance ? (
              <Group gap="lg" wrap="nowrap">
                <div>
                  <Text size="xs" c="dimmed">Available ({year})</Text>
                  <Text fw={700} className="tabular" c={overBalance ? 'red' : undefined}>{formatDays(balance.available)} days</Text>
                </div>
                {!!balance.pending && (
                  <div>
                    <Text size="xs" c="dimmed">Pending</Text>
                    <Text fw={600} className="tabular">{formatDays(balance.pending)}</Text>
                  </div>
                )}
              </Group>
            ) : (
              <Text size="xs" c="dimmed">Balance not available</Text>
            )}
          </Group>
        </Paper>
      )}

      <DatePickerInput
        type="range"
        label="Dates"
        withAsterisk
        allowSingleDateInRange
        placeholder="Pick the first and last day"
        description="Click the same day twice for a single day."
        leftSection={<IconCalendar size={16} />}
        valueFormat="D MMM YYYY"
        value={range}
        onChange={(v) => setRange([v[0] ? String(v[0]) : null, v[1] ? String(v[1]) : null])}
      />

      {single && type?.allowHalfDay && (
        <Input.Wrapper label="Duration" description="Take only part of the day off.">
          <SegmentedControl
            mt={6}
            fullWidth
            data={[{ value: 'FULL', label: 'Full day' }, { value: 'MORNING', label: 'Morning' }, { value: 'AFTERNOON', label: 'Afternoon' }]}
            value={part}
            onChange={setPart}
          />
        </Input.Wrapper>
      )}

      {range[0] && (
        <Group gap="xs" wrap="nowrap" align="flex-start">
          <IconInfoCircle size={16} style={{ marginTop: 2, flexShrink: 0 }} color="var(--mantine-color-dimmed)" />
          <Text size="sm" c="dimmed">
            <Text span fw={600} c="var(--mantine-color-text)" className="tabular">
              {formatDays(requested)} {requested === 1 ? 'day' : 'days'}
            </Text>{' '}
            · {formatDateRange(range[0], range[1])}
            {halfDay && ` (${part === 'MORNING' ? 'morning' : 'afternoon'})`}
            {excludesNonWorking && !halfDay && requested > 1 && '. Weekly-off days and holidays are excluded according to the policy.'}
          </Text>
        </Group>
      )}

      {overBalance && (
        <Alert color="orange" variant="light" icon={<IconAlertTriangle size={18} />} p="xs">
          This exceeds the available balance of {formatDays(balance?.available)} days and may be rejected.
        </Alert>
      )}

      <Textarea label="Reason" placeholder="Optional note for your approver" autosize minRows={2} maxRows={5} value={reason} onChange={(e) => setReason(e.currentTarget.value)} />

      <FileInput
        label="Supporting documents"
        placeholder="Attach files (PDF, image, DOCX)"
        leftSection={<IconPaperclip size={16} />}
        description={type?.attachmentAboveDays != null ? `Required for more than ${type.attachmentAboveDays} day(s)` : undefined}
        error={needsAttachment ? 'Please attach a supporting document for this many days.' : undefined}
        multiple
        accept=".pdf,.jpg,.jpeg,.png,.docx"
        value={files}
        onChange={setFiles}
        clearable
      />

      <Group justify="flex-end" mt="xs">
        <Button leftSection={<IconSend size={16} />} onClick={() => void submit()} loading={busy} disabled={!typeId || !range[0]}>Apply</Button>
      </Group>
    </Stack>
  );
}
