import { Alert, Button, PasswordInput, Stack, Text, TextInput, Title } from '@mantine/core';
import { useForm } from '@mantine/form';
import { IconAlertCircle, IconLock, IconUser } from '@tabler/icons-react';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Navigate, useNavigate } from 'react-router';
import { ApiError } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { AuthShell } from './AuthShell';

export function LoginPage() {
  const { t } = useTranslation();
  const { login, status } = useAuth();
  const navigate = useNavigate();
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [capsLock, setCapsLock] = useState(false);
  const form = useForm({
    initialValues: { username: '', password: '' },
    validate: {
      username: (v) => (v.trim() ? null : t('auth.required')),
      password: (v) => (v ? null : t('auth.required')),
    },
  });

  if (status === 'authenticated') return <Navigate to="/" replace />;

  const submit = form.onSubmit(async (values) => {
    setBusy(true);
    setError(null);
    try {
      const me = await login(values.username, values.password);
      navigate(me.mustChangePassword ? '/change-password' : '/', { replace: true });
    } catch (e) {
      setError(e instanceof ApiError ? e.message : t('app.error'));
    } finally {
      setBusy(false);
    }
  });

  return (
    <AuthShell>
      <form onSubmit={submit} noValidate>
        <Stack gap="md">
          <div>
            <Title order={2}>{t('auth.welcomeBack')}</Title>
            <Text size="sm" c="dimmed" mt={4}>{t('auth.signInSubtitle')}</Text>
          </div>
          {error && (
            <Alert color="red" variant="light" role="alert" icon={<IconAlertCircle size={18} />}>
              {error}
            </Alert>
          )}
          <TextInput
            label={t('auth.username')}
            autoComplete="username"
            autoFocus
            size="md"
            leftSection={<IconUser size={16} />}
            {...form.getInputProps('username')}
          />
          <PasswordInput
            label={t('auth.password')}
            autoComplete="current-password"
            size="md"
            leftSection={<IconLock size={16} />}
            description={capsLock ? t('auth.capsLock') : undefined}
            onKeyUp={(e) => setCapsLock(e.getModifierState?.('CapsLock') ?? false)}
            {...form.getInputProps('password')}
          />
          <Button type="submit" loading={busy} size="md" fullWidth mt="xs">
            {t('auth.signIn')}
          </Button>
        </Stack>
      </form>
    </AuthShell>
  );
}
