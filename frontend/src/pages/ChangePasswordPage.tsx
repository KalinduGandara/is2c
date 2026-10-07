import { Alert, Button, Center, Paper, PasswordInput, Stack, Text, Title } from '@mantine/core';
import { useForm } from '@mantine/form';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router';
import { api } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { applyServerErrors, showError, showSuccess } from '../components/notify';

export function ChangePasswordPage() {
  const { t } = useTranslation();
  const { user, reload } = useAuth();
  const navigate = useNavigate();
  const form = useForm({
    initialValues: { currentPassword: '', newPassword: '', confirm: '' },
    validate: {
      currentPassword: (v) => (v ? null : t('auth.required')),
      newPassword: (v) => (v.length >= 10 ? null : t('auth.passwordRules')),
      confirm: (v, values) => (v === values.newPassword ? null : t('auth.passwordsDoNotMatch')),
    },
  });

  const submit = form.onSubmit(async (values) => {
    try {
      await api('/auth/change-password', {
        method: 'POST',
        body: { currentPassword: values.currentPassword, newPassword: values.newPassword },
      });
      showSuccess(t('auth.passwordChanged'));
      await reload();
      navigate('/', { replace: true });
    } catch (e) {
      if (!applyServerErrors(e, form.setErrors)) showError(e);
    }
  });

  return (
    <Center mih="100vh" p="md">
      <Paper withBorder shadow="sm" p="xl" w="100%" maw={420}>
        <form onSubmit={submit} noValidate>
          <Stack>
            <Title order={3}>{t('auth.changePassword')}</Title>
            {user?.mustChangePassword && <Alert color="yellow">{t('auth.mustChange')}</Alert>}
            <Text size="sm" c="dimmed">
              {t('auth.passwordRules')}
            </Text>
            <PasswordInput label={t('auth.currentPassword')} autoComplete="current-password" {...form.getInputProps('currentPassword')} />
            <PasswordInput label={t('auth.newPassword')} autoComplete="new-password" {...form.getInputProps('newPassword')} />
            <PasswordInput label={t('auth.confirmPassword')} autoComplete="new-password" {...form.getInputProps('confirm')} />
            <Button type="submit">{t('app.save')}</Button>
          </Stack>
        </form>
      </Paper>
    </Center>
  );
}
