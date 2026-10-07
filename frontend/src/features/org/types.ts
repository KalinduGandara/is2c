export interface Branch {
  id: string;
  companyId: string;
  code: string;
  name: string;
  address: string | null;
  phone: string | null;
  version: number;
}

export interface Department {
  id: string;
  companyId: string;
  parentId: string | null;
  branchId: string | null;
  code: string;
  name: string;
  costCenterCode: string | null;
  managerEmployeeId: string | null;
  version: number;
  children: Department[];
}

export interface Lookup {
  id: string;
  companyId: string;
  code: string;
  name: string;
  rank: number | null;
  otEligible: boolean | null;
  version: number;
}

export interface CompanySettings {
  companyId: string;
  weeklyOffDays: string[];
  employeeNumberFormat: string;
  payrollStartDay: number;
  absentAsNoPay: boolean;
  noPayDivisor: number;
  punchWindowBeforeMinutes: number;
  punchWindowAfterMinutes: number;
  weekStartDay: string;
  version: number;
}

export interface CompanyDetail {
  id: string;
  code: string;
  legalName: string;
  registrationNo: string | null;
  address: string | null;
  phone: string | null;
  email: string | null;
  currency: string;
  epfEmployerNo: string | null;
  etfNo: string | null;
  statutoryPack: string;
  timeZone: string;
  version: number;
}
