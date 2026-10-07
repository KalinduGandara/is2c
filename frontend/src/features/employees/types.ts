export type EmployeeStatus = 'ACTIVE' | 'RESIGNED' | 'TERMINATED' | 'INACTIVE';

export interface EmployeeListItem {
  id: string;
  companyId: string;
  employeeNumber: string;
  firstName: string;
  lastName: string;
  preferredName: string | null;
  branchName: string | null;
  departmentName: string | null;
  designationName: string | null;
  status: EmployeeStatus;
  joinDate: string;
}

export interface Contact {
  name: string;
  relationship: string;
  phone: string;
}

export interface Employee {
  id: string;
  companyId: string;
  branchId: string;
  departmentId: string;
  designationId: string;
  gradeId: string | null;
  categoryId: string;
  employeeNumber: string;
  title: string | null;
  firstName: string;
  lastName: string;
  preferredName: string | null;
  nic: string | null;
  dateOfBirth: string | null;
  gender: string | null;
  maritalStatus: string | null;
  nationality: string | null;
  personalEmail: string | null;
  mobile: string | null;
  address: string | null;
  joinDate: string;
  probationEndDate: string | null;
  confirmationDate: string | null;
  contractEndDate: string | null;
  exitDate: string | null;
  exitReason: string | null;
  reportingManagerId: string | null;
  defaultShiftId: string | null;
  weeklyOffDays: string[] | null;
  badgeId: string | null;
  status: EmployeeStatus;
  emergencyContacts: Contact[];
  customFields: Record<string, string>;
  version: number;
}

export interface HistoryEntry {
  id: string;
  effectiveDate: string;
  eventType: string;
  changes: string;
  reason: string | null;
  recordedAt: string;
}

export interface DocumentInfo {
  id: string;
  docType: string;
  title: string;
  fileName: string;
  contentType: string;
  sizeBytes: number;
  expiryDate: string | null;
  uploadedAt: string;
}

export const STATUS_COLORS: Record<EmployeeStatus, string> = {
  ACTIVE: 'green',
  RESIGNED: 'orange',
  TERMINATED: 'red',
  INACTIVE: 'gray',
};
