export type ShiftType = 'FIXED' | 'FLEXIBLE' | 'NIGHT' | 'SPLIT' | 'CUSTOM';

export interface Shift {
  id: string;
  companyId: string;
  code: string;
  name: string;
  type: ShiftType;
  color: string | null;
  s1Start: string | null;
  s1End: string | null;
  s2Start: string | null;
  s2End: string | null;
  flexEarliestStart: string | null;
  flexLatestEnd: string | null;
  requiredMinutes: number | null;
  lateGraceMinutes: number;
  earlyGraceMinutes: number;
  breakMode: 'FIXED' | 'PUNCHED';
  breakMinutes: number;
  breakThresholdMinutes: number;
  halfDayThresholdMinutes: number | null;
  otPolicyId: string | null;
  windowBeforeMinutes: number | null;
  windowAfterMinutes: number | null;
  active: boolean;
  crossesMidnight: boolean;
  scheduledMinutes: number;
  version: number;
}

export interface OtPolicy {
  id: string;
  name: string;
  minOtMinutes: number;
  roundingMinutes: number;
  allowPreShiftOt: boolean;
  maxOtMinutes: number | null;
  requiresApproval: boolean;
  version: number;
}

export interface RotationPattern {
  id: string;
  name: string;
  days: (string | null)[];
  version: number;
}

export type RosterStatus = 'DRAFT' | 'PUBLISHED' | 'LOCKED';

export interface Roster {
  id: string;
  companyId: string;
  departmentId: string | null;
  name: string;
  periodType: 'WEEKLY' | 'MONTHLY';
  startDate: string;
  endDate: string;
  status: RosterStatus;
  revision: number;
  version: number;
}

export interface RosterEntry {
  employeeId: string;
  date: string;
  shiftId: string | null;
  off: boolean;
}

export interface RosterDetail {
  roster: Roster;
  entries: RosterEntry[];
  employees: { id: string; employeeNumber: string; name: string; departmentId: string }[];
}

export interface Revision {
  id: string;
  revision: number;
  employeeId: string;
  date: string;
  oldValue: string | null;
  newValue: string | null;
  reason: string;
  changedAt: string;
}

export interface ScheduleRow {
  employeeId: string;
  employeeNumber: string;
  name: string;
  days: { date: string; dayType: 'WORKDAY' | 'WEEKLY_OFF' | 'HOLIDAY'; shiftId: string | null; shiftCode: string | null; source: string; holidayName: string | null }[];
}

/** Inclusive list of ISO dates between two ISO dates. */
export function dateRange(from: string, to: string): string[] {
  const out: string[] = [];
  const d = new Date(`${from}T00:00:00Z`);
  const end = new Date(`${to}T00:00:00Z`);
  while (d <= end) {
    out.push(d.toISOString().slice(0, 10));
    d.setUTCDate(d.getUTCDate() + 1);
  }
  return out;
}

export function weekdayShort(iso: string): string {
  return new Date(`${iso}T00:00:00Z`).toLocaleDateString('en-GB', { weekday: 'short', timeZone: 'UTC' });
}

export const hhmm = (t: string | null) => (t ? t.slice(0, 5) : '');
