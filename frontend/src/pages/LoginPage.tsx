import { Alert, Button, Center, Paper, PasswordInput, Stack, TextInput, Title } from '@mantine/core';
import { useForm } from '@mantine/form';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Navigate, useNavigate } from 'react-router';
import { ApiError } from '../api/client';
import { useAuth } from '../auth/AuthContext';

export function LoginPage() {
  const { t } = useTranslation();
  const { login, status } = useAuth();
  const navigate = useNavigate();
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
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
    <Center mih="100vh" p="md">
      <Paper withBorder shadow="sm" p="xl" w="100%" maw={400}>
        <form onSubmit={submit} noValidate>
          <Stack>
            <Title order={3}>{t('auth.signInTitle')}</Title>
            {error && (
              <Alert color="red" role="alert">
                {error}
              </Alert>
            )}
            <TextInput label={t('auth.username')} autoComplete="username" {...form.getInputProps('username')} />
            <PasswordInput label={t('auth.password')} autoComplete="current-password" {...form.getInputProps('password')} />
            <Button type="submit" loading={busy}>
              {t('auth.signIn')}
            </Button>
          </Stack>
        </form>
      </Paper>
    </Center>
  );
}
