export interface LeaveType {
  id: string;
  code: string;
  name: string;
  paid: boolean;
  annualDays: number;
  accrualMethod: 'ANNUAL_UPFRONT' | 'MONTHLY';
  proRata: boolean;
  cfMaxDays: number;
  cfExpiryMonths: number;
  allowHalfDay: boolean;
  allowNegative: boolean;
  unlimited: boolean;
  countWeeklyOff: boolean;
  countHoliday: boolean;
  attachmentAboveDays: number | null;
  eligibleCategoryIds: string[];
  color: string | null;
  active: boolean;
  version: number;
}

export interface Balance {
  id: string | null;
  leaveTypeId: string;
  leaveTypeCode: string;
  leaveTypeName: string;
  unlimited: boolean;
  year: number;
  opening: number | null;
  entitled: number | null;
  carriedForward: number | null;
  adjustment: number | null;
  taken: number | null;
  pending: number | null;
  expired: number | null;
  available: number | null;
}

export interface LeaveRequest {
  id: string;
  employeeId: string;
  employeeNumber: string | null;
  employeeName: string | null;
  leaveType: string | null;
  fromDate: string;
  toDate: string;
  halfDayPart: 'MORNING' | 'AFTERNOON' | null;
  days: number;
  reason: string | null;
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED';
  decisionNote: string | null;
  appliedAt: string;
  attachments: { id: string; fileName: string }[];
}

export interface CalendarEntry {
  kind: 'LEAVE' | 'HOLIDAY';
  id: string;
  employeeId: string | null;
  employeeName: string;
  leaveType: string | null;
  color: string | null;
  from: string;
  to: string;
  status: string | null;
  halfDayPart: string | null;
}

export const LEAVE_STATUS_COLOR: Record<LeaveRequest['status'], string> = {
  PENDING: 'yellow', APPROVED: 'green', REJECTED: 'red', CANCELLED: 'gray',
};
