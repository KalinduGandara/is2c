import { Button, FileInput, Group, SegmentedControl, Select, Stack, Textarea } from '@mantine/core';
import { DatePickerInput } from '@mantine/dates';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { api } from '../../api/client';
import { showError, showSuccess } from '../../components/notify';
import type { LeaveType } from './types';

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
    <Stack>
      <Select label="Leave type" withAsterisk data={types.filter((t) => t.active).map((t) => ({ value: t.id, label: t.name }))} value={typeId} onChange={setTypeId} />
      <DatePickerInput type="range" label="Dates" withAsterisk allowSingleDateInRange valueFormat="YYYY-MM-DD" value={range}
        onChange={(v) => setRange([v[0] ? String(v[0]) : null, v[1] ? String(v[1]) : null])} />
      {single && type?.allowHalfDay && (
        <SegmentedControl data={[{ value: 'FULL', label: 'Full day' }, { value: 'MORNING', label: 'Morning' }, { value: 'AFTERNOON', label: 'Afternoon' }]} value={part} onChange={setPart} />
      )}
      <Textarea label="Reason" value={reason} onChange={(e) => setReason(e.currentTarget.value)} />
      <FileInput label="Supporting documents" description={type?.attachmentAboveDays != null ? `Required for more than ${type.attachmentAboveDays} day(s)` : undefined}
        multiple accept=".pdf,.jpg,.jpeg,.png,.docx" value={files} onChange={setFiles} clearable />
      <Group justify="flex-end">
        <Button onClick={() => void submit()} loading={busy} disabled={!typeId || !range[0]}>Apply</Button>
      </Group>
    </Stack>
  );
}
