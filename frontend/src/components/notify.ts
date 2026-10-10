import { notifications } from '@mantine/notifications';
import { IconCheck, IconX } from '@tabler/icons-react';
import { createElement } from 'react';
import { ApiError } from '../api/client';
import i18n from '../i18n';

export function showError(error: unknown) {
  const message =
    error instanceof ApiError
      ? [error.message, ...error.errors.map((e) => `${e.field}: ${e.message}`)].join('\n')
      : i18n.t('app.error');
  notifications.show({
    color: 'red',
    title: i18n.t('app.error'),
    message,
    autoClose: 8000,
    icon: createElement(IconX, { size: 18 }),
    withBorder: true,
  });
}

export function showSuccess(message?: string) {
  notifications.show({
    color: 'teal',
    message: message ?? i18n.t('app.saved'),
    icon: createElement(IconCheck, { size: 18 }),
    withBorder: true,
  });
}

/** Maps server field errors onto a Mantine form; returns true when any were applied. */
export function applyServerErrors(
  error: unknown,
  setErrors: (errors: Record<string, string>) => void,
): boolean {
  if (!(error instanceof ApiError) || error.errors.length === 0) return false;
  const mapped: Record<string, string> = {};
  for (const e of error.errors) mapped[e.field] = mapped[e.field] ? `${mapped[e.field]}; ${e.message}` : e.message;
  setErrors(mapped);
  return true;
}
