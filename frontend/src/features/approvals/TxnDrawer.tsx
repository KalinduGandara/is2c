import { Avatar, Badge, Button, Drawer, Group, Paper, Skeleton, Stack, Text, Textarea, ThemeIcon, Timeline, Title, Tooltip } from '@mantine/core';
import { modals } from '@mantine/modals';
import {
  IconArrowForward,
  IconCheck,
  IconClock,
  IconInfoCircle,
  IconRobot,
  IconSend,
  IconX,
  type Icon,
} from '@tabler/icons-react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { api } from '../../api/client';
import { formatDateTime, humanize } from '../../components/format';
import { showError, showSuccess } from '../../components/notify';
import { StatusBadge } from '../../components/StatusBadge';
import { levelLabel, relativeTime, TYPE_COLOR, TYPE_LABEL, typeIcon, type TxnDetail } from './types';

type Action = 'approve' | 'reject' | 'cancel';

const HISTORY_STYLE: Record<string, { icon: Icon; color: string; verb: string }> = {
  SUBMIT: { icon: IconSend, color: 'blue', verb: 'Submitted' },
  APPROVE: { icon: IconCheck, color: 'teal', verb: 'Approved' },
  REJECT: { icon: IconX, color: 'red', verb: 'Rejected' },
  CANCEL: { icon: IconX, color: 'gray', verb: 'Cancelled' },
  AUTO: { icon: IconRobot, color: 'grape', verb: 'Auto-approved' },
  FALLBACK: { icon: IconArrowForward, color: 'orange', verb: 'Escalated' },
};

function DrawerSkeleton() {
  return (
    <Stack role="status" aria-busy="true" aria-label="Loading">
      <Skeleton h={90} radius="md" />
      <Skeleton h={120} radius="md" />
      <Skeleton h={160} radius="md" />
    </Stack>
  );
}

/** FR-071 approval detail with history and approve / reject / cancel actions. */
export function TxnDrawer({ id, onClose }: { id: string; onClose: () => void }) {
  const client = useQueryClient();
  const { data } = useQuery({ queryKey: ['approval', id], queryFn: () => api<TxnDetail>(`/approval/transactions/${id}`) });
  const [comment, setComment] = useState('');
  const [busy, setBusy] = useState<Action | null>(null);
  const act = async (action: Action) => {
    setBusy(action);
    try {
      await api(`/approval/transactions/${id}/${action}`, { method: 'POST', body: { comment } });
      showSuccess(action === 'approve' ? 'Approved' : action === 'reject' ? 'Rejected' : 'Cancelled');
      await client.invalidateQueries({ queryKey: ['approvals'] });
      onClose();
    } catch (e) {
      showError(e);
    } finally {
      setBusy(null);
    }
  };
  const confirm = (action: 'reject' | 'cancel') =>
    modals.openConfirmModal({
      title: action === 'reject' ? 'Reject this request?' : 'Cancel this request?',
      children: (
        <Text size="sm">
          {action === 'reject'
            ? 'The requester will be notified with your comment. This cannot be undone.'
            : 'The request will be withdrawn from all approvers. This cannot be undone.'}
        </Text>
      ),
      labels: { confirm: action === 'reject' ? 'Reject' : 'Cancel request', cancel: action === 'reject' ? 'Back' : 'Keep request' },
      confirmProps: { color: 'red' },
      onConfirm: () => void act(action),
    });

  const t = data?.transaction;
  const typeColor = TYPE_COLOR[t?.type ?? ''] ?? 'gray';

  // Progress through the levels: completed levels, the current one, and those still to come.
  const levels = data?.levels ?? [];
  const actedAt = (level: number) =>
    [...(data?.history ?? [])].reverse().find((h) => h.level === level && ['APPROVE', 'REJECT', 'AUTO'].includes(h.action));
  const levelState = (level: number): 'done' | 'current' | 'rejected' | 'todo' | 'skipped' => {
    if (!t) return 'todo';
    if (t.status === 'APPROVED') return 'done';
    if (level < t.currentLevel) return 'done';
    if (level === t.currentLevel) return t.status === 'REJECTED' ? 'rejected' : t.status === 'PENDING' ? 'current' : 'skipped';
    return t.status === 'PENDING' ? 'todo' : 'skipped';
  };
  const activeIndex = t ? (t.status === 'APPROVED' ? levels.length - 1 : t.currentLevel - 1) : -1;

  return (
    <Drawer
      opened
      onClose={onClose}
      position="right"
      size="lg"
      title={
        t ? (
          <Group gap="sm" wrap="nowrap">
            <ThemeIcon variant="light" color={typeColor} radius="md" size={34}>
              {typeIcon(t.type, 18)}
            </ThemeIcon>
            <span>
              <Text component="span" display="block" fw={600} lh={1.2}>{TYPE_LABEL[t.type] ?? humanize(t.type)} approval</Text>
              <Text component="span" display="block" size="xs" c="dimmed">Submitted {relativeTime(t.submittedAt)}</Text>
            </span>
          </Group>
        ) : (
          'Approval'
        )
      }
    >
      {!(data && t) ? (
        <DrawerSkeleton />
      ) : (
        <Stack gap="lg">
          <Paper withBorder p="md">
            <Group justify="space-between" align="flex-start" wrap="nowrap" gap="sm">
              <Text fw={600} style={{ wordBreak: 'break-word' }}>{t.summary}</Text>
              <StatusBadge status={t.status} style={{ flexShrink: 0 }} />
            </Group>
            <Group gap="sm" mt="md" wrap="nowrap">
              <Avatar name={t.requesterName ?? 'System'} color="initials" size="md" radius="xl" />
              <div>
                <Text size="sm" fw={500}>{t.requesterName ?? 'System'}</Text>
                <Text size="xs" c="dimmed">Requested on {formatDateTime(t.submittedAt)}</Text>
              </div>
            </Group>
            {t.completedAt && (
              <Text size="xs" c="dimmed" mt="xs">Completed on {formatDateTime(t.completedAt)}</Text>
            )}
          </Paper>

          {levels.length > 0 && (
            <div>
              <Group justify="space-between" mb="sm">
                <Title order={5}>Approval progress</Title>
                <Text size="xs" c="dimmed">
                  Level {Math.min(t.currentLevel, levels.length)} of {levels.length}
                </Text>
              </Group>
              <Timeline active={activeIndex} bulletSize={26} lineWidth={2} color={t.status === 'REJECTED' ? 'red' : undefined}>
                {levels.map((l, i) => {
                  const level = i + 1;
                  const state = levelState(level);
                  const acted = actedAt(level);
                  return (
                    <Timeline.Item
                      key={level}
                      bullet={
                        state === 'done' ? <IconCheck size={14} /> : state === 'rejected' ? <IconX size={14} /> : state === 'current' ? <IconClock size={14} /> : <Text size="xs" fw={600}>{level}</Text>
                      }
                      color={state === 'rejected' ? 'red' : state === 'done' ? 'teal' : undefined}
                      title={
                        <Group gap="xs">
                          <Text size="sm" fw={600}>Level {level}: {levelLabel(l)}</Text>
                          {state === 'current' && <Badge size="xs" color="yellow" variant="light">Waiting</Badge>}
                        </Group>
                      }
                    >
                      {acted ? (
                        <Text size="xs" c="dimmed">
                          {HISTORY_STYLE[acted.action]?.verb ?? humanize(acted.action)}
                          {acted.userName ? ` by ${acted.userName}` : ''}
                          {acted.onBehalfOf ? ' (delegate)' : ''} · {formatDateTime(acted.actedAt)}
                        </Text>
                      ) : state === 'current' && data.currentApprovers.length > 0 ? (
                        <Text size="xs" c="dimmed">Waiting for {data.currentApprovers.join(', ')}</Text>
                      ) : state === 'todo' ? (
                        <Text size="xs" c="dimmed">Not started</Text>
                      ) : null}
                    </Timeline.Item>
                  );
                })}
              </Timeline>
            </div>
          )}

          <div>
            <Title order={5} mb="sm">History</Title>
            {data.history.length === 0 ? (
              <Text size="sm" c="dimmed">No activity yet.</Text>
            ) : (
              <Timeline bulletSize={24} lineWidth={2} active={data.history.length - 1}>
                {data.history.map((h, i) => {
                  const style = HISTORY_STYLE[h.action] ?? { icon: IconInfoCircle, color: 'gray', verb: humanize(h.action) };
                  const HIcon = style.icon;
                  return (
                    <Timeline.Item
                      key={i}
                      color={style.color}
                      bullet={<HIcon size={13} />}
                      title={
                        <Text size="sm" fw={500}>
                          {style.verb}
                          {h.userName && <Text span c="dimmed" fw={400}> by {h.userName}{h.onBehalfOf ? ' (delegate)' : ''}</Text>}
                        </Text>
                      }
                    >
                      <Tooltip label={formatDateTime(h.actedAt)} withArrow>
                        <Text size="xs" c="dimmed" w="fit-content">
                          Level {h.level} · {relativeTime(h.actedAt)}
                        </Text>
                      </Tooltip>
                      {h.comment && (
                        <Paper withBorder p="xs" mt={6} radius="md" bg="var(--mantine-color-default-hover)">
                          <Text size="sm" style={{ whiteSpace: 'pre-wrap' }}>{h.comment}</Text>
                        </Paper>
                      )}
                    </Timeline.Item>
                  );
                })}
              </Timeline>
            )}
          </div>

          {(data.canAct || data.canCancel) && (
            <Paper withBorder p="md">
              <Textarea
                label="Comment"
                description="Required when rejecting"
                placeholder={data.canAct ? 'Add a note for the requester (optional when approving)' : 'Optional note'}
                autosize
                minRows={2}
                maxRows={6}
                value={comment}
                onChange={(e) => setComment(e.currentTarget.value)}
              />
              <Group justify="space-between" mt="md" wrap="wrap" gap="sm">
                <Text size="xs" c="dimmed">
                  {data.canAct && !comment.trim() ? 'Add a comment to enable Reject.' : ''}
                </Text>
                <Group gap="xs" wrap="wrap" justify="flex-end">
                  {data.canCancel && (
                    <Button variant="default" loading={busy === 'cancel'} disabled={busy !== null && busy !== 'cancel'} onClick={() => confirm('cancel')}>
                      Cancel request
                    </Button>
                  )}
                  {data.canAct && (
                    <Button
                      color="red"
                      variant="light"
                      leftSection={<IconX size={16} />}
                      loading={busy === 'reject'}
                      disabled={!comment.trim() || (busy !== null && busy !== 'reject')}
                      onClick={() => confirm('reject')}
                    >
                      Reject
                    </Button>
                  )}
                  {data.canAct && (
                    <Button
                      color="teal"
                      leftSection={<IconCheck size={16} />}
                      loading={busy === 'approve'}
                      disabled={busy !== null && busy !== 'approve'}
                      onClick={() => void act('approve')}
                    >
                      Approve
                    </Button>
                  )}
                </Group>
              </Group>
            </Paper>
          )}
        </Stack>
      )}
    </Drawer>
  );
}
