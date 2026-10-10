import { createElement, type ReactNode } from 'react';
import { IconBeach, IconClockEdit, IconFileDescription, IconReportMoney, type Icon } from '@tabler/icons-react';

export interface Txn {
  id: string;
  type: string;
  referenceId: string;
  requesterName: string | null;
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED';
  currentLevel: number;
  summary: string;
  submittedAt: string;
  completedAt: string | null;
}

export type ApprovalLevel = { approverType: string; roleCode: string | null; userId: string | null };

export interface TxnDetail {
  transaction: Txn;
  levels: ApprovalLevel[];
  history: { level: number; action: string; userName: string | null; onBehalfOf: string | null; comment: string | null; actedAt: string }[];
  currentApprovers: string[];
  canAct: boolean;
  canCancel: boolean;
}

export const TYPE_LABEL: Record<string, string> = {
  LEAVE_REQUEST: 'Leave',
  ATTENDANCE_CORRECTION: 'Attendance correction',
  PAYROLL_APPROVAL: 'Payroll',
};

export const TYPE_ICON: Record<string, Icon> = {
  LEAVE_REQUEST: IconBeach,
  ATTENDANCE_CORRECTION: IconClockEdit,
  PAYROLL_APPROVAL: IconReportMoney,
};

export const TYPE_COLOR: Record<string, string> = {
  LEAVE_REQUEST: 'blue',
  ATTENDANCE_CORRECTION: 'teal',
  PAYROLL_APPROVAL: 'green',
};

/** Icon element for a transaction type. */
export function typeIcon(type: string, size = 16): ReactNode {
  return createElement(TYPE_ICON[type] ?? IconFileDescription, { size });
}

/** Human description of who approves at a level. */
export function levelLabel(l: ApprovalLevel, roleName?: (code: string) => string | undefined): string {
  if (l.approverType === 'ROLE') return (l.roleCode && roleName?.(l.roleCode)) || l.roleCode || 'Role';
  if (l.approverType === 'USER') return 'Specific user';
  return 'Reporting manager';
}

const RELATIVE = new Intl.RelativeTimeFormat('en', { numeric: 'auto' });
const UNITS: [Intl.RelativeTimeFormatUnit, number][] = [
  ['year', 31_536_000],
  ['month', 2_592_000],
  ['week', 604_800],
  ['day', 86_400],
  ['hour', 3_600],
  ['minute', 60],
];

/** `3 hours ago`, `yesterday`, `just now`. */
export function relativeTime(iso: string, now = Date.now()): string {
  const seconds = (new Date(iso).getTime() - now) / 1000;
  if (Number.isNaN(seconds)) return iso;
  for (const [unit, size] of UNITS) {
    if (Math.abs(seconds) >= size) return RELATIVE.format(Math.round(seconds / size), unit);
  }
  return 'just now';
}
