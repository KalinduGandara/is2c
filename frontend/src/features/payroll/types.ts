export type PeriodStatus = 'OPEN' | 'DRAFT' | 'IN_REVIEW' | 'APPROVED' | 'FINALIZED';

export interface PayrollPeriod {
  id: string;
  companyId: string;
  year: number;
  month: number;
  startDate: string;
  endDate: string;
  status: PeriodStatus;
  stale: boolean;
  approvalId: string | null;
  processedAt: string | null;
  finalizedAt: string | null;
  version: number;
}

export const PERIOD_STATUS_COLOR: Record<PeriodStatus, string> = {
  OPEN: 'gray',
  DRAFT: 'blue',
  IN_REVIEW: 'yellow',
  APPROVED: 'teal',
  FINALIZED: 'green',
};

export interface PayrollResult {
  id: string;
  employeeId: string;
  employeeNumber: string;
  employeeName: string;
  departmentName: string | null;
  designationName: string | null;
  gross: number;
  deductions: number;
  net: number;
  employerContributions: number;
  workedDays: number;
  noPayDays: number;
  otHours: number;
  payslipNo: string | null;
  locked: boolean;
}

export type ComponentKind = 'EARNING' | 'DEDUCTION' | 'EMPLOYER_CONTRIBUTION';

export interface ResultLine {
  componentCode: string;
  componentName: string;
  kind: ComponentKind;
  units: number | null;
  rate: number | null;
  amount: number;
  showOnPayslip: boolean;
}

export interface PayrollExceptionRow {
  employeeId: string | null;
  code: string;
  message: string;
  blocking: boolean;
}

export interface Totals {
  gross: number;
  deductions: number;
  net: number;
  employerContributions: number;
  employees: number;
  byComponent: Record<string, number>;
  netByDepartment: Record<string, number>;
}

export interface OneTimeItem {
  id: string;
  employeeId: string;
  componentId: string;
  amount: number;
  note: string | null;
  version: number;
}

export interface PayrollComponent {
  id: string;
  companyId: string;
  code: string;
  name: string;
  kind: ComponentKind;
  frequency: 'RECURRING' | 'ONE_TIME';
  assignment: 'PER_EMPLOYEE' | 'AUTOMATIC';
  calcType: 'FIXED' | 'PERCENT_OF' | 'PER_UNIT' | 'SLAB';
  sequence: number;
  bases: string[];
  basisRef: string | null;
  ratePercent: number | null;
  unitMetric: string | null;
  unitRateMode: 'FIXED_AMOUNT' | 'BASIS' | null;
  unitFixedAmount: number | null;
  divisor: number | null;
  multiplier: number | null;
  slabTableId: string | null;
  prorate: boolean;
  showOnPayslip: boolean;
  statutory: boolean;
  systemComponent: boolean;
  active: boolean;
  version: number;
}

export interface SlabRow {
  lowerBound: number;
  upperBound: number | null;
  ratePercent: number;
}

export interface SlabTable {
  id: string;
  companyId: string;
  code: string;
  name: string;
  statutory: boolean;
  rows: SlabRow[];
  version: number;
}

export interface StructureLine {
  componentId: string;
  amount: number | null;
  rateOverride: number | null;
  multiplierOverride: number | null;
  excluded: boolean;
}

export interface SalaryStructure {
  id: string;
  employeeId: string;
  effectiveFrom: string;
  effectiveTo: string | null;
  basicSalary: number;
  lines: StructureLine[];
  version: number;
}

export interface Delivery {
  employeeId: string;
  employeeNumber: string;
  employeeName: string;
  status: 'SENT' | 'FAILED' | 'NO_EMAIL';
}

export interface EmailReport {
  sent: number;
  failed: number;
  results: Delivery[];
}

const MONEY = new Intl.NumberFormat('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

export const money = (v: number | null | undefined) => (v === null || v === undefined ? '' : MONEY.format(v));

export const periodLabel = (p: Pick<PayrollPeriod, 'year' | 'month'>) =>
  new Date(Date.UTC(p.year, p.month - 1, 1)).toLocaleString('en-US', { month: 'long', year: 'numeric', timeZone: 'UTC' });
