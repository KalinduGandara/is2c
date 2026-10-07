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

export interface TxnDetail {
  transaction: Txn;
  levels: { approverType: string; roleCode: string | null; userId: string | null }[];
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
