import { MantineProvider } from '@mantine/core';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { PermissionMatrix } from './PermissionMatrix';

describe('PermissionMatrix', () => {
  it('FR-005 toggles MODULE:ACTION permissions', async () => {
    const onChange = vi.fn();
    render(
      <MantineProvider>
        <PermissionMatrix catalog={{ modules: ['ORG', 'PAYROLL'], actions: ['VIEW', 'EDIT'] }} value={['ORG:VIEW']} onChange={onChange} />
      </MantineProvider>,
    );
    expect(screen.getByLabelText('ORG:VIEW')).toBeChecked();
    await userEvent.click(screen.getByLabelText('PAYROLL:EDIT'));
    expect(onChange).toHaveBeenLastCalledWith(['ORG:VIEW', 'PAYROLL:EDIT']);
    await userEvent.click(screen.getByLabelText('ORG:VIEW'));
    expect(onChange).toHaveBeenLastCalledWith([]);
  });
});
