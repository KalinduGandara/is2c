import { Badge, Button, Drawer, Group, Stack, Table, Text, Textarea, Title } from '@mantine/core';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { api } from '../../api/client';
import { showError, showSuccess } from '../../components/notify';
import { TYPE_LABEL, type TxnDetail } from './types';

/** FR-071 approval detail with history and approve / reject / cancel actions. */
export function TxnDrawer({ id, onClose }: { id: string; onClose: () => void }) {
  const client = useQueryClient();
  const { data } = useQuery({ queryKey: ['approval', id], queryFn: () => api<TxnDetail>(`/approval/transactions/${id}`) });
  const [comment, setComment] = useState('');
  const act = async (action: 'approve' | 'reject' | 'cancel') => {
    try {
      await api(`/approval/transactions/${id}/${action}`, { method: 'POST', body: { comment } });
      showSuccess(action === 'approve' ? 'Approved' : action === 'reject' ? 'Rejected' : 'Cancelled');
      await client.invalidateQueries({ queryKey: ['approvals'] });
      onClose();
    } catch (e) {
      showError(e);
    }
  };
  const t = data?.transaction;
  return (
    <Drawer opened onClose={onClose} title="Approval" position="right" size="lg">
      {data && t && (
        <Stack>
          <Group>
            <Badge>{TYPE_LABEL[t.type] ?? t.type}</Badge>
            <Badge color={t.status === 'APPROVED' ? 'green' : t.status === 'REJECTED' ? 'red' : t.status === 'CANCELLED' ? 'gray' : 'yellow'}>{t.status}</Badge>
          </Group>
          <Text fw={500}>{t.summary}</Text>
          <Text size="sm" c="dimmed">Requested by {t.requesterName ?? 'system'} on {new Date(t.submittedAt).toLocaleString()}</Text>
          <Text size="sm">Level {t.currentLevel} of {data.levels.length}{data.currentApprovers.length > 0 && ` — waiting for ${data.currentApprovers.join(', ')}`}</Text>
          <Title order={5}>History</Title>
          <Table withTableBorder>
            <Table.Tbody>
              {data.history.map((h, i) => (
                <Table.Tr key={i}>
                  <Table.Td>{new Date(h.actedAt).toLocaleString()}</Table.Td>
                  <Table.Td>L{h.level} {h.action}</Table.Td>
                  <Table.Td>{h.userName ?? ''}{h.onBehalfOf ? ' (delegate)' : ''}</Table.Td>
                  <Table.Td>{h.comment}</Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
          {(data.canAct || data.canCancel) && (
            <>
              <Textarea label="Comment" description="Required when rejecting" value={comment} onChange={(e) => setComment(e.currentTarget.value)} />
              <Group justify="flex-end">
                {data.canCancel && <Button variant="default" onClick={() => void act('cancel')}>Cancel request</Button>}
                {data.canAct && <Button color="red" variant="light" disabled={!comment.trim()} onClick={() => void act('reject')}>Reject</Button>}
                {data.canAct && <Button onClick={() => void act('approve')}>Approve</Button>}
              </Group>
            </>
          )}
        </Stack>
      )}
    </Drawer>
  );
}
