import { ActionIcon, Button, Checkbox, Divider, Grid, Group, MultiSelect, NumberInput, Select, Stack, TextInput, Textarea, Title } from '@mantine/core';
import { DateInput } from '@mantine/dates';
import { useForm } from '@mantine/form';
import { IconPlus, IconTrash } from '@tabler/icons-react';
import { useQuery } from '@tanstack/react-query';
import { api, type Page } from '../../api/client';
import { useAuth } from '../../auth/AuthContext';
import { WEEKDAYS } from '../../components/EntityForm';
import { applyServerErrors, showError } from '../../components/notify';
import type { CustomField } from '../org/CustomFieldsPanel';
import { toOptions, useBranches, useDepartments, useLookups } from '../org/useOrgData';
import { useShifts } from '../shifts/useShifts';
import type { Contact, Employee, EmployeeListItem } from './types';

export type EmployeeFormValues = Omit<Employee, 'id' | 'status' | 'exitDate' | 'exitReason' | 'version'> & {
  effectiveDate: string | null;
  changeReason: string | null;
};

interface Props {
  companyId: string;
  initial: Employee | null;
  onSubmit: (values: EmployeeFormValues) => Promise<void>;
}

const emptyContact: Contact = { name: '', relationship: '', phone: '' };

function dateStr(v: unknown): string | null {
  if (!v) return null;
  if (v instanceof Date) {
    return `${v.getFullYear()}-${String(v.getMonth() + 1).padStart(2, '0')}-${String(v.getDate()).padStart(2, '0')}`;
  }
  return String(v);
}

const DATE_FIELDS = ['dateOfBirth', 'joinDate', 'probationEndDate', 'confirmationDate', 'contractEndDate', 'effectiveDate'] as const;

export function EmployeeForm({ companyId, initial, onSubmit }: Props) {
  const { can } = useAuth();
  const { data: branches } = useBranches(companyId);
  const { data: departments } = useDepartments(companyId);
  const { data: designations } = useLookups(companyId, 'designations');
  const { data: grades } = useLookups(companyId, 'grades');
  const { data: categories } = useLookups(companyId, 'employment-categories');
  const { data: shifts = [] } = useShifts(companyId);
  const { data: customFields = [] } = useQuery({
    queryKey: ['custom-fields', companyId],
    queryFn: () => api<CustomField[]>(`/companies/${companyId}/custom-fields`),
  });
  const { data: managers } = useQuery({
    queryKey: ['employee-options', companyId],
    queryFn: () => api<Page<EmployeeListItem>>('/employees', { query: { companyId, status: 'ACTIVE', size: 200, sort: 'employeeNumber,asc' } }),
  });

  const form = useForm<EmployeeFormValues>({
    initialValues: {
      companyId,
      branchId: initial?.branchId ?? '',
      departmentId: initial?.departmentId ?? '',
      designationId: initial?.designationId ?? '',
      gradeId: initial?.gradeId ?? null,
      categoryId: initial?.categoryId ?? '',
      employeeNumber: initial?.employeeNumber ?? '',
      title: initial?.title ?? null,
      firstName: initial?.firstName ?? '',
      lastName: initial?.lastName ?? '',
      preferredName: initial?.preferredName ?? null,
      nic: initial?.nic ?? null,
      dateOfBirth: initial?.dateOfBirth ?? null,
      gender: initial?.gender ?? null,
      maritalStatus: initial?.maritalStatus ?? null,
      nationality: initial?.nationality ?? null,
      personalEmail: initial?.personalEmail ?? null,
      mobile: initial?.mobile ?? null,
      address: initial?.address ?? null,
      joinDate: initial?.joinDate ?? '',
      probationEndDate: initial?.probationEndDate ?? null,
      confirmationDate: initial?.confirmationDate ?? null,
      contractEndDate: initial?.contractEndDate ?? null,
      reportingManagerId: initial?.reportingManagerId ?? null,
      defaultShiftId: initial?.defaultShiftId ?? null,
      weeklyOffDays: initial?.weeklyOffDays ?? null,
      badgeId: initial?.badgeId ?? null,
      emergencyContacts: initial?.emergencyContacts ?? [],
      customFields: initial?.customFields ?? {},
      effectiveDate: null,
      changeReason: null,
    },
    validate: {
      firstName: (v) => (v?.trim() ? null : 'Required'),
      lastName: (v) => (v?.trim() ? null : 'Required'),
      branchId: (v) => (v ? null : 'Required'),
      departmentId: (v) => (v ? null : 'Required'),
      designationId: (v) => (v ? null : 'Required'),
      categoryId: (v) => (v ? null : 'Required'),
      joinDate: (v) => (v ? null : 'Required'),
    },
  });

  const submit = form.onSubmit(async (values) => {
    const body = { ...values } as Record<string, unknown>;
    for (const f of DATE_FIELDS) body[f] = dateStr(values[f]);
    for (const [k, v] of Object.entries(body)) if (v === '') body[k] = null;
    if (!can('EMPLOYEE:CONFIGURE') && !initial) body.employeeNumber = null;
    try {
      await onSubmit(body as EmployeeFormValues);
    } catch (e) {
      if (!applyServerErrors(e, form.setErrors)) showError(e);
    }
  });

  const managerOptions = (managers?.content ?? [])
    .filter((m) => m.id !== initial?.id)
    .map((m) => ({ value: m.id, label: `${m.employeeNumber} — ${m.firstName} ${m.lastName}` }));

  return (
    <form onSubmit={submit} noValidate>
      <Stack>
        <Title order={4}>Personal</Title>
        <Grid>
          <Grid.Col span={{ base: 12, sm: 2 }}><Select label="Title" data={['Mr', 'Mrs', 'Ms', 'Dr', 'Rev']} clearable {...form.getInputProps('title')} /></Grid.Col>
          <Grid.Col span={{ base: 12, sm: 5 }}><TextInput label="First name" withAsterisk {...form.getInputProps('firstName')} /></Grid.Col>
          <Grid.Col span={{ base: 12, sm: 5 }}><TextInput label="Last name" withAsterisk {...form.getInputProps('lastName')} /></Grid.Col>
          <Grid.Col span={{ base: 12, sm: 4 }}><TextInput label="Preferred name" {...form.getInputProps('preferredName')} /></Grid.Col>
          <Grid.Col span={{ base: 12, sm: 4 }}><TextInput label="NIC / passport" {...form.getInputProps('nic')} /></Grid.Col>
          <Grid.Col span={{ base: 12, sm: 4 }}><DateInput label="Date of birth" valueFormat="YYYY-MM-DD" clearable {...form.getInputProps('dateOfBirth')} /></Grid.Col>
          <Grid.Col span={{ base: 12, sm: 4 }}><Select label="Gender" data={['MALE', 'FEMALE', 'OTHER']} clearable {...form.getInputProps('gender')} /></Grid.Col>
          <Grid.Col span={{ base: 12, sm: 4 }}><Select label="Marital status" data={['SINGLE', 'MARRIED', 'DIVORCED', 'WIDOWED', 'OTHER']} clearable {...form.getInputProps('maritalStatus')} /></Grid.Col>
          <Grid.Col span={{ base: 12, sm: 4 }}><TextInput label="Nationality" {...form.getInputProps('nationality')} /></Grid.Col>
          <Grid.Col span={{ base: 12, sm: 6 }}><TextInput label="Personal email" {...form.getInputProps('personalEmail')} /></Grid.Col>
          <Grid.Col span={{ base: 12, sm: 6 }}><TextInput label="Mobile" {...form.getInputProps('mobile')} /></Grid.Col>
          <Grid.Col span={12}><Textarea label="Address" autosize minRows={2} {...form.getInputProps('address')} /></Grid.Col>
        </Grid>

        <Divider />
        <Title order={4}>Employment</Title>
        <Grid>
          <Grid.Col span={{ base: 12, sm: 4 }}>
            <TextInput label="Employee number" description={initial ? undefined : 'Leave blank to generate'}
              disabled={!can('EMPLOYEE:CONFIGURE')} {...form.getInputProps('employeeNumber')} />
          </Grid.Col>
          <Grid.Col span={{ base: 12, sm: 4 }}><Select label="Branch" withAsterisk searchable data={toOptions(branches)} {...form.getInputProps('branchId')} /></Grid.Col>
          <Grid.Col span={{ base: 12, sm: 4 }}><Select label="Department" withAsterisk searchable data={toOptions(departments)} {...form.getInputProps('departmentId')} /></Grid.Col>
          <Grid.Col span={{ base: 12, sm: 4 }}><Select label="Designation" withAsterisk searchable data={toOptions(designations)} {...form.getInputProps('designationId')} /></Grid.Col>
          <Grid.Col span={{ base: 12, sm: 4 }}><Select label="Grade" clearable data={toOptions(grades)} {...form.getInputProps('gradeId')} /></Grid.Col>
          <Grid.Col span={{ base: 12, sm: 4 }}><Select label="Employment category" withAsterisk data={toOptions(categories)} {...form.getInputProps('categoryId')} /></Grid.Col>
          <Grid.Col span={{ base: 12, sm: 3 }}><DateInput label="Join date" withAsterisk valueFormat="YYYY-MM-DD" {...form.getInputProps('joinDate')} /></Grid.Col>
          <Grid.Col span={{ base: 12, sm: 3 }}><DateInput label="Probation end" valueFormat="YYYY-MM-DD" clearable {...form.getInputProps('probationEndDate')} /></Grid.Col>
          <Grid.Col span={{ base: 12, sm: 3 }}><DateInput label="Confirmation date" valueFormat="YYYY-MM-DD" clearable {...form.getInputProps('confirmationDate')} /></Grid.Col>
          <Grid.Col span={{ base: 12, sm: 3 }}><DateInput label="Contract end" valueFormat="YYYY-MM-DD" clearable {...form.getInputProps('contractEndDate')} /></Grid.Col>
          <Grid.Col span={{ base: 12, sm: 6 }}><Select label="Reporting manager" clearable searchable data={managerOptions} {...form.getInputProps('reportingManagerId')} /></Grid.Col>
          <Grid.Col span={{ base: 12, sm: 6 }}><TextInput label="Attendance badge / device ID" {...form.getInputProps('badgeId')} /></Grid.Col>
          <Grid.Col span={{ base: 12, sm: 6 }}>
            <Select label="Default shift" clearable searchable
              data={shifts.filter((s) => s.active || s.id === initial?.defaultShiftId).map((s) => ({ value: s.id, label: `${s.code} — ${s.name}` }))}
              {...form.getInputProps('defaultShiftId')} />
          </Grid.Col>
          <Grid.Col span={{ base: 12, sm: 6 }}>
            <MultiSelect label="Weekly-off days" description="Leave empty to use the company default" data={WEEKDAYS}
              value={form.getValues().weeklyOffDays ?? []}
              onChange={(v) => form.setFieldValue('weeklyOffDays', v.length ? v : null)} />
          </Grid.Col>
        </Grid>

        <Divider />
        <Group justify="space-between">
          <Title order={4}>Emergency contacts</Title>
          <Button variant="light" size="xs" leftSection={<IconPlus size={14} />}
            onClick={() => form.insertListItem('emergencyContacts', { ...emptyContact })}>Add contact</Button>
        </Group>
        {form.getValues().emergencyContacts.map((_, i) => (
          <Grid key={i} align="flex-end">
            <Grid.Col span={{ base: 12, sm: 4 }}><TextInput label="Name" withAsterisk {...form.getInputProps(`emergencyContacts.${i}.name`)} /></Grid.Col>
            <Grid.Col span={{ base: 12, sm: 3 }}><TextInput label="Relationship" withAsterisk {...form.getInputProps(`emergencyContacts.${i}.relationship`)} /></Grid.Col>
            <Grid.Col span={{ base: 10, sm: 4 }}><TextInput label="Phone" withAsterisk {...form.getInputProps(`emergencyContacts.${i}.phone`)} /></Grid.Col>
            <Grid.Col span={{ base: 2, sm: 1 }}>
              <ActionIcon color="red" variant="subtle" aria-label="Remove contact" onClick={() => form.removeListItem('emergencyContacts', i)}><IconTrash size={16} /></ActionIcon>
            </Grid.Col>
          </Grid>
        ))}

        {customFields.some((f) => f.active) && (
          <>
            <Divider />
            <Title order={4}>Additional information</Title>
            <Grid>
              {customFields.filter((f) => f.active).map((f) => {
                const value = form.getValues().customFields[f.key] ?? '';
                const set = (v: string | null) => form.setFieldValue('customFields', { ...form.getValues().customFields, [f.key]: v ?? '' });
                const error = (form.errors[`customFields.${f.key}`] as string | undefined) ?? undefined;
                const common = { label: f.label, withAsterisk: f.required, error };
                return (
                  <Grid.Col key={f.key} span={{ base: 12, sm: 6 }}>
                    {f.type === 'SELECT' ? (
                      <Select {...common} data={f.options} clearable value={value || null} onChange={set} />
                    ) : f.type === 'BOOLEAN' ? (
                      <Checkbox mt="lg" label={f.label} error={error} checked={value === 'true'} onChange={(e) => set(String(e.currentTarget.checked))} />
                    ) : f.type === 'NUMBER' ? (
                      <NumberInput {...common} value={value} onChange={(v) => set(v === '' ? null : String(v))} />
                    ) : f.type === 'DATE' ? (
                      <DateInput {...common} valueFormat="YYYY-MM-DD" clearable value={value || null} onChange={(v) => set(dateStr(v))} />
                    ) : (
                      <TextInput {...common} value={value} onChange={(e) => set(e.currentTarget.value)} />
                    )}
                  </Grid.Col>
                );
              })}
            </Grid>
          </>
        )}

        {initial && (
          <>
            <Divider />
            <Grid>
              <Grid.Col span={{ base: 12, sm: 4 }}><DateInput label="Change effective date" valueFormat="YYYY-MM-DD" clearable {...form.getInputProps('effectiveDate')} /></Grid.Col>
              <Grid.Col span={{ base: 12, sm: 8 }}><TextInput label="Reason for change" {...form.getInputProps('changeReason')} /></Grid.Col>
            </Grid>
          </>
        )}
        <Group justify="flex-end">
          <Button type="submit" loading={form.submitting}>Save</Button>
        </Group>
      </Stack>
    </form>
  );
}
