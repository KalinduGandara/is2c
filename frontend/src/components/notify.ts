import { notifications } from '@mantine/notifications';
import { ApiError } from '../api/client';
import i18n from '../i18n';

export function showError(error: unknown) {
  const message =
    error instanceof ApiError
      ? [error.message, ...error.errors.map((e) => `${e.field}: ${e.message}`)].join('\n')
      : i18n.t('app.error');
  notifications.show({ color: 'red', title: i18n.t('app.error'), message, autoClose: 8000 });
}

export function showSuccess(message?: string) {
  notifications.show({ color: 'green', message: message ?? i18n.t('app.saved') });
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
