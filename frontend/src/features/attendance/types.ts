export type DayStatus = 'PRESENT' | 'ABSENT' | 'HALF_DAY' | 'ON_LEAVE' | 'HALF_DAY_LEAVE' | 'WEEKLY_OFF' | 'HOLIDAY' | 'INCOMPLETE' | 'UNSCHEDULED';

export interface Daily {
  id: string;
  employeeId: string;
  employeeNumber: string | null;
  employeeName: string | null;
  date: string;
  dayType: string;
  status: DayStatus;
  firstIn: string | null;
  lastOut: string | null;
  scheduledMinutes: number;
  workedMinutes: number;
  lateMinutes: number;
  earlyMinutes: number;
  shortMinutes: number;
  otWorkdayMinutes: number;
  otOffdayMinutes: number;
  otHolidayMinutes: number;
  extraMinutes: number;
  otRequiresApproval: boolean;
  otApproved: boolean;
  otRejected: boolean;
  manual: boolean;
  exceptions: string[];
}

export interface Adjustment {
  id: string;
  type: 'CREATE' | 'EDIT';
  newIn: string | null;
  newOut: string | null;
  beforeIn: string | null;
  beforeOut: string | null;
  reason: string;
  status: string;
  overrideLock: boolean;
  requestedAt: string;
}

export interface DayDetail {
  daily: Daily | null;
  events: { id: string; time: string; direction: string; deviceId: string | null }[];
  adjustments: Adjustment[];
}

export const STATUS_COLOR: Record<DayStatus, string> = {
  PRESENT: 'green', ABSENT: 'red', HALF_DAY: 'orange', ON_LEAVE: 'blue', HALF_DAY_LEAVE: 'cyan',
  WEEKLY_OFF: 'gray', HOLIDAY: 'grape', INCOMPLETE: 'yellow', UNSCHEDULED: 'dark',
};

export const time = (v: string | null) => (v ? v.slice(11, 16) : '—');
export const hours = (minutes: number) => (minutes ? (minutes / 60).toFixed(2) : '');
export const totalOt = (d: Daily) => d.otWorkdayMinutes + d.otOffdayMinutes + d.otHolidayMinutes;

export function today(): string {
  return new Date().toISOString().slice(0, 10);
}

export function daysAgo(n: number): string {
  const d = new Date();
  d.setDate(d.getDate() - n);
  return d.toISOString().slice(0, 10);
}
