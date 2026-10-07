export interface ReportParam {
  name: string;
  label: string;
  type: string;
  required: boolean;
}

export interface CatalogEntry {
  key: string;
  title: string;
  params: ReportParam[];
}

export interface TableColumn {
  header: string;
  type: 'TEXT' | 'AMOUNT' | 'NUMBER';
}

export type Cell = string | number | null;

export interface ReportTable {
  title: string;
  headerLines: string[];
  columns: TableColumn[];
  rows: Cell[][];
  footer: Cell[] | null;
  landscape: boolean;
  generatedBy: string;
  generatedAt: string;
}

export interface Dashboard {
  date: string;
  activeEmployees: number;
  presentToday: number;
  absentToday: number;
  lateToday: number;
  onLeaveToday: number;
  pendingApprovals: number;
  headcountByDepartment: Record<string, number>;
  payroll: {
    currentPeriod: { id: string; year: number; month: number; status: string } | null;
    lastProcessedPeriod: { id: string; year: number; month: number; status: string } | null;
    lastProcessedOtCost: number | null;
  } | null;
}
