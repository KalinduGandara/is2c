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

const DAY_MONTH_YEAR = new Intl.DateTimeFormat('en-GB', { day: 'numeric', month: 'short', year: 'numeric', timeZone: 'UTC' });
const DAY_MONTH = new Intl.DateTimeFormat('en-GB', { day: 'numeric', month: 'short', timeZone: 'UTC' });
const utc = (iso: string) => new Date(`${iso.slice(0, 10)}T00:00:00Z`);

/** Compact date range: `15 – 17 Jan 2025`, `30 Jan – 2 Feb 2025`, `30 Dec 2025 – 2 Jan 2026`, or a single day. */
export function formatDateRange(from: string, to: string | null | undefined): string {
  const a = utc(from);
  if (!to || to === from) return DAY_MONTH_YEAR.format(a);
  const b = utc(to);
  if (a.getUTCFullYear() !== b.getUTCFullYear()) return `${DAY_MONTH_YEAR.format(a)} – ${DAY_MONTH_YEAR.format(b)}`;
  if (a.getUTCMonth() !== b.getUTCMonth()) return `${DAY_MONTH.format(a)} – ${DAY_MONTH_YEAR.format(b)}`;
  return `${a.getUTCDate()} – ${DAY_MONTH_YEAR.format(b)}`;
}

/** Inclusive number of calendar days between two ISO dates. */
export function calendarDays(from: string, to: string | null | undefined): number {
  return Math.round((utc(to ?? from).getTime() - utc(from).getTime()) / 86_400_000) + 1;
}

/** Number with up to one decimal (`1.5`, `2`). */
export function formatDays(days: number | null | undefined): string {
  if (days === null || days === undefined) return '—';
  return Number.isInteger(days) ? String(days) : days.toFixed(1);
}
