import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { api } from '../api/client';
import { json, mockFetch, renderPage } from '../test/utils';
import { CrudPanel } from './CrudPanel';

interface Thing {
  id: string;
  code: string;
  name: string;
}

function Panel() {
  return (
    <CrudPanel<Thing>
      entityName="thing"
      queryKey={['things']}
      load={() => api<Thing[]>('/things')}
      rowKey={(r) => r.id}
      columns={[{ key: 'code', header: 'Code', render: (r) => r.code }]}
      fields={[{ name: 'code', label: 'Code', required: true }, { name: 'name', label: 'Name' }]}
      emptyValues={{ code: '', name: '' }}
      toValues={(r) => ({ ...r })}
      create={(v) => api('/things', { method: 'POST', body: v })}
    />
  );
}

describe('CrudPanel', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('lists rows and creates a record with blank text normalised to null', async () => {
    const { calls } = mockFetch({
      'GET /things': () => json([{ id: '1', code: 'A', name: 'Alpha' }]),
      'POST /things': () => json({ id: '2' }, 201),
    });
    renderPage(<Panel />);
    expect(await screen.findByText('A')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: /Create thing/ }));
    await userEvent.type(await screen.findByLabelText(/Code/), 'B');
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));
    await waitFor(() => expect(calls.some((c) => c.method === 'POST')).toBe(true));
    expect(calls.find((c) => c.method === 'POST')?.body).toEqual({ code: 'B', name: null });
  });

  it('NFR-009 shows server field errors next to the inputs', async () => {
    mockFetch({
      'GET /things': () => json([]),
      'POST /things': () => json({ code: 'VALIDATION_FAILED', detail: 'Validation failed', errors: [{ field: 'code', message: 'Code already exists in this company' }] }, 422),
    });
    renderPage(<Panel />);
    await userEvent.click(await screen.findByRole('button', { name: /Create thing/ }));
    await userEvent.type(await screen.findByLabelText(/Code/), 'DUP');
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));
    expect(await screen.findByText('Code already exists in this company')).toBeInTheDocument();
  });

  it('validates required fields client-side', async () => {
    const { calls } = mockFetch({ 'GET /things': () => json([]) });
    renderPage(<Panel />);
    await userEvent.click(await screen.findByRole('button', { name: /Create thing/ }));
    await userEvent.click(await screen.findByRole('button', { name: 'Save' }));
    expect(await screen.findByText('Required')).toBeInTheDocument();
    expect(calls.filter((c) => c.method === 'POST')).toHaveLength(0);
  });
});
