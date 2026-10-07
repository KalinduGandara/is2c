import { Badge, Button, Group, Modal, Paper, Select, Stack, Tabs, Text, TextInput } from '@mantine/core';
import { DateInput } from '@mantine/dates';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import { api } from '../../api/client';
import { useCompany } from '../../app/CompanyContext';
import { useAuth } from '../../auth/AuthContext';
import { DataTable } from '../../components/DataTable';
import { showError, showSuccess } from '../../components/notify';
import { PageHeader } from '../../components/PageHeader';
import { SalaryStructurePanel } from '../payroll/SalaryStructurePanel';
import { DocumentsPanel } from './DocumentsPanel';
import { EmployeeForm } from './EmployeeForm';
import { STATUS_COLORS, type Employee, type HistoryEntry } from './types';

function StatusModal({ employee, onClose, onDone }: { employee: Employee; onClose: () => void; onDone: () => void }) {
  const [status, setStatus] = useState<string | null>(null);
  const [exitDate, setExitDate] = useState<string | null>(null);
  const [reason, setReason] = useState('');
  const leaving = status === 'RESIGNED' || status === 'TERMINATED';
  const submit = async () => {
    try {
      await api(`/employees/${employee.id}/status`, { method: 'POST', body: { status, exitDate: leaving ? exitDate : null, reason: reason || null } });
      showSuccess('Status changed');
      onDone();
    } catch (e) {
      showError(e);
    }
  };
  return (
    <Modal opened onClose={onClose} title="Change employment status">
      <Stack>
        <Select label="New status" data={['ACTIVE', 'RESIGNED', 'TERMINATED', 'INACTIVE'].filter((s) => s !== employee.status)} value={status} onChange={setStatus} />
        {leaving && <DateInput label="Exit date" valueFormat="YYYY-MM-DD" value={exitDate} onChange={(v) => setExitDate(v ? String(v) : null)} />}
        <TextInput label="Reason" value={reason} onChange={(e) => setReason(e.currentTarget.value)} />
        <Group justify="flex-end">
          <Button variant="default" onClick={onClose}>Cancel</Button>
          <Button onClick={() => void submit()} disabled={!status}>Save</Button>
        </Group>
      </Stack>
    </Modal>
  );
}

export function EmployeeDetailPage() {
  const { id } = useParams();
  const isNew = id === 'new';
  const { can } = useAuth();
  const { companyId } = useCompany();
  const navigate = useNavigate();
  const client = useQueryClient();
  const [statusOpen, setStatusOpen] = useState(false);
  const { data: employee, refetch } = useQuery({
    queryKey: ['employee', id],
    queryFn: () => api<Employee>(`/employees/${id}`),
    enabled: !isNew,
  });
  const { data: history = [] } = useQuery({
    queryKey: ['employee-history', id],
    queryFn: () => api<HistoryEntry[]>(`/employees/${id}/history`),
    enabled: !isNew,
  });

  if (isNew) {
    if (!companyId) return <Text>Select a company first.</Text>;
    return (
      <>
        <PageHeader title="New employee" />
        <Paper withBorder p="md">
          <EmployeeForm
            companyId={companyId}
            initial={null}
            onSubmit={async (values) => {
              const created = await api<Employee>('/employees', { method: 'POST', body: values });
              showSuccess(`Employee ${created.employeeNumber} created`);
              await client.invalidateQueries({ queryKey: ['employees'] });
              navigate(`/employees/${created.id}`, { replace: true });
            }}
          />
        </Paper>
      </>
    );
  }
  if (!employee) return null;

  return (
    <>
      <PageHeader
        title={`${employee.employeeNumber} — ${employee.firstName} ${employee.lastName}`}
        actions={
          <>
            <Badge size="lg" color={STATUS_COLORS[employee.status]}>{employee.status}</Badge>
            {can('EMPLOYEE:EDIT') && <Button variant="light" onClick={() => setStatusOpen(true)}>Change status</Button>}
          </>
        }
      />
      <Tabs defaultValue="profile" keepMounted={false}>
        <Tabs.List mb="md">
          <Tabs.Tab value="profile">Profile</Tabs.Tab>
          <Tabs.Tab value="history">History</Tabs.Tab>
          <Tabs.Tab value="documents">Documents</Tabs.Tab>
          {can('PAYROLL:VIEW') && <Tabs.Tab value="salary">Salary</Tabs.Tab>}
        </Tabs.List>
        <Tabs.Panel value="profile">
          <Paper withBorder p="md">
            <EmployeeForm
              key={employee.version}
              companyId={employee.companyId}
              initial={employee}
              onSubmit={async (values) => {
                await api(`/employees/${employee.id}`, { method: 'PUT', body: { ...values, version: employee.version } });
                showSuccess();
                await refetch();
                await client.invalidateQueries({ queryKey: ['employee-history', id] });
              }}
            />
          </Paper>
        </Tabs.Panel>
        <Tabs.Panel value="history">
          <DataTable
            rows={history}
            rowKey={(r) => r.id}
            columns={[
              { key: 'date', header: 'Effective', render: (r) => r.effectiveDate },
              { key: 'type', header: 'Event', render: (r) => r.eventType },
              { key: 'changes', header: 'Changes', render: (r) => <Text size="xs" ff="monospace">{r.changes}</Text> },
              { key: 'reason', header: 'Reason', render: (r) => r.reason },
            ]}
          />
        </Tabs.Panel>
        <Tabs.Panel value="documents">
          <DocumentsPanel employeeId={employee.id} />
        </Tabs.Panel>
        {can('PAYROLL:VIEW') && (
          <Tabs.Panel value="salary">
            <SalaryStructurePanel employeeId={employee.id} companyId={employee.companyId} />
          </Tabs.Panel>
        )}
      </Tabs>
      {statusOpen && (
        <StatusModal employee={employee} onClose={() => setStatusOpen(false)} onDone={() => {
          setStatusOpen(false);
          void refetch();
          void client.invalidateQueries({ queryKey: ['employee-history', id] });
        }} />
      )}
    </>
  );
}
