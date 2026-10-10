import {
  IconBeach,
  IconBuildingSkyscraper,
  IconCalendarTime,
  IconChartBar,
  IconChecklist,
  IconClockHour4,
  IconLayoutDashboard,
  IconReportMoney,
  IconSettings,
  IconUserCircle,
  IconUsers,
  type Icon,
} from '@tabler/icons-react';

/** Icon per navigation item key (see app/nav.ts). */
export const NAV_ICONS: Record<string, Icon> = {
  dashboard: IconLayoutDashboard,
  self: IconUserCircle,
  approvals: IconChecklist,
  employees: IconUsers,
  organization: IconBuildingSkyscraper,
  attendance: IconClockHour4,
  shifts: IconCalendarTime,
  leave: IconBeach,
  payroll: IconReportMoney,
  reports: IconChartBar,
  administration: IconSettings,
};
