import { Tabs } from '@mantine/core';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { DataTable } from './DataTable';
import { formatDate, humanize, initials } from './format';
import { StatusBadge } from './StatusBadge';
import { useUrlTab } from './useUrlTab';
import { renderPage } from '../test/utils';

describe('format helpers', () => {
  it('humanizes enum values and leaves prose alone', () => {
    expect(humanize('IN_REVIEW')).toBe('In review');
    expect(humanize('HALF_DAY_LEAVE')).toBe('Half day leave');
    expect(humanize('Already readable')).toBe('Already readable');
    expect(humanize(null)).toBe('');
  });

  it('formats calendar dates without shifting the day', () => {
    expect(formatDate('2025-01-15')).toBe('15 Jan 2025');
    expect(formatDate('2025-12-31T23:30:00')).toBe('31 Dec 2025');
    expect(formatDate(null)).toBe('—');
  });

  it('derives initials', () => {
    expect(initials('Nimal Perera')).toBe('NP');
    expect(initials('  madonna ')).toBe('M');
    expect(initials('')).toBe('?');
  });
});

describe('StatusBadge', () => {
  it('shows a readable label for workflow states', async () => {
    renderPage(<StatusBadge status="IN_REVIEW" />);
    expect(await screen.findByText('In review')).toBeInTheDocument();
  });
});

describe('DataTable', () => {
  const rows = [{ id: '1', name: 'Alpha' }, { id: '2', name: 'Beta' }];
  const columns = [{ key: 'name', header: 'Name', render: (r: { name: string }) => r.name }];

  it('shows a friendly empty state', async () => {
    renderPage(<DataTable<{ id: string; name: string }> rows={[]} columns={columns} rowKey={(r) => r.id} emptyTitle="No things yet" emptyDescription="Create one." />);
    expect(await screen.findByText('No things yet')).toBeInTheDocument();
    expect(screen.getByText('Create one.')).toBeInTheDocument();
  });

  it('NFR-012 opens clickable rows with the keyboard', async () => {
    const opened: string[] = [];
    renderPage(<DataTable rows={rows} columns={columns} rowKey={(r) => r.id} onRowClick={(r) => opened.push(r.id)} rowLabel={(r) => `Open ${r.name}`} />);
    const row = await screen.findByRole('row', { name: 'Open Beta' });
    row.focus();
    await userEvent.keyboard('{Enter}');
    expect(opened).toEqual(['2']);
  });

  it('shows the page position when paginated', async () => {
    renderPage(<DataTable rows={rows} columns={columns} rowKey={(r) => r.id} page={1} totalPages={3} totalElements={52} onPageChange={() => {}} />);
    expect(await screen.findByText('Page 2 of 3 · 52 records')).toBeInTheDocument();
  });
});

function TabbedPage() {
  const [tab, setTab] = useUrlTab('one', ['one', 'two']);
  return (
    <Tabs value={tab} onChange={setTab}>
      <Tabs.List>
        <Tabs.Tab value="one">One</Tabs.Tab>
        <Tabs.Tab value="two">Two</Tabs.Tab>
      </Tabs.List>
    </Tabs>
  );
}

describe('useUrlTab', () => {
  it('reads and writes the active tab in the query string', async () => {
    const { router } = renderPage(<TabbedPage />, '/page?tab=two');
    expect(await screen.findByRole('tab', { name: 'Two' })).toHaveAttribute('aria-selected', 'true');
    await userEvent.click(screen.getByRole('tab', { name: 'One' }));
    expect(router.state.location.search).toBe('');
    await userEvent.click(screen.getByRole('tab', { name: 'Two' }));
    expect(router.state.location.search).toBe('?tab=two');
  });

  it('ignores unknown tab values', async () => {
    renderPage(<TabbedPage />, '/page?tab=bogus');
    expect(await screen.findByRole('tab', { name: 'One' })).toHaveAttribute('aria-selected', 'true');
  });
});
