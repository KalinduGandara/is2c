import { Alert, Anchor, Button, Group, List, PasswordInput, Progress, Stack, Text, ThemeIcon, Title } from '@mantine/core';
import { useForm } from '@mantine/form';
import { IconCheck, IconInfoCircle, IconX } from '@tabler/icons-react';
import { useTranslation } from 'react-i18next';
import { Link, useNavigate } from 'react-router';
import { api } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { applyServerErrors, showError, showSuccess } from '../components/notify';
import { AuthShell } from './AuthShell';

const RULES = [
  { key: 'length', test: (v: string) => v.length >= 10 },
  { key: 'case', test: (v: string) => /[a-z]/.test(v) && /[A-Z]/.test(v) },
  { key: 'digit', test: (v: string) => /\d/.test(v) },
  { key: 'symbol', test: (v: string) => /[^A-Za-z0-9]/.test(v) },
] as const;

export function ChangePasswordPage() {
  const { t } = useTranslation();
  const { user, reload } = useAuth();
  const navigate = useNavigate();
  const form = useForm({
    initialValues: { currentPassword: '', newPassword: '', confirm: '' },
    validate: {
      currentPassword: (v) => (v ? null : t('auth.required')),
      newPassword: (v) => (RULES.every((r) => r.test(v)) ? null : t('auth.passwordRules')),
      confirm: (v, values) => (v === values.newPassword ? null : t('auth.passwordsDoNotMatch')),
    },
  });

  const value = form.getValues().newPassword;
  const passed = RULES.filter((r) => r.test(value)).length;
  const strength = (passed / RULES.length) * 100;
  const strengthColor = passed <= 1 ? 'red' : passed <= 3 ? 'yellow' : 'teal';

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
    <AuthShell>
      <form onSubmit={submit} noValidate>
        <Stack gap="md">
          <div>
            <Title order={2}>{t('auth.changePassword')}</Title>
            <Text size="sm" c="dimmed" mt={4}>{t('auth.changePasswordSubtitle')}</Text>
          </div>
          {user?.mustChangePassword && (
            <Alert color="yellow" variant="light" icon={<IconInfoCircle size={18} />}>
              {t('auth.mustChange')}
            </Alert>
          )}
          <PasswordInput label={t('auth.currentPassword')} autoComplete="current-password" autoFocus {...form.getInputProps('currentPassword')} />
          <div>
            <PasswordInput label={t('auth.newPassword')} autoComplete="new-password" {...form.getInputProps('newPassword')} />
            <Progress value={value ? Math.max(strength, 8) : 0} color={strengthColor} size={6} mt={8} aria-hidden />
            <List spacing={4} size="sm" mt="xs" center>
              {RULES.map((r) => {
                const ok = r.test(value);
                return (
                  <List.Item
                    key={r.key}
                    icon={
                      <ThemeIcon size={16} radius="xl" color={ok ? 'teal' : 'gray'} variant={ok ? 'filled' : 'light'}>
                        {ok ? <IconCheck size={11} /> : <IconX size={11} />}
                      </ThemeIcon>
                    }
                  >
                    <Text size="xs" c={ok ? undefined : 'dimmed'}>{t(`auth.rule.${r.key}`)}</Text>
                  </List.Item>
                );
              })}
            </List>
          </div>
          <PasswordInput label={t('auth.confirmPassword')} autoComplete="new-password" {...form.getInputProps('confirm')} />
          <Button type="submit" size="md" fullWidth loading={form.submitting}>
            {t('auth.updatePassword')}
          </Button>
          {!user?.mustChangePassword && (
            <Group justify="center">
              <Anchor component={Link} to="/" size="sm" c="dimmed">{t('app.cancel')}</Anchor>
            </Group>
          )}
        </Stack>
      </form>
    </AuthShell>
  );
}
