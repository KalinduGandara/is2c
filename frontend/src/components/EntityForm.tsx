import { Button, Checkbox, Group, MultiSelect, NumberInput, Select, Stack, Textarea, TextInput } from '@mantine/core';
import { DateInput, TimeInput } from '@mantine/dates';
import { useForm } from '@mantine/form';
import { useTranslation } from 'react-i18next';
import { applyServerErrors, showError } from './notify';

export type FieldType = 'text' | 'textarea' | 'number' | 'checkbox' | 'select' | 'multiselect' | 'date' | 'time';

export interface FieldSpec {
  name: string;
  label: string;
  type?: FieldType;
  required?: boolean;
  options?: { value: string; label: string }[];
  min?: number;
  max?: number;
  description?: string;
  placeholder?: string;
  /** Hide the field depending on other values. */
  visible?: (values: Record<string, unknown>) => boolean;
}

export type FormValues = Record<string, unknown>;

interface Props {
  fields: FieldSpec[];
  initialValues: FormValues;
  onSubmit: (values: FormValues) => Promise<void>;
  onCancel?: () => void;
  submitLabel?: string;
}

export const WEEKDAYS = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'].map((d) => ({
  value: d,
  label: d.charAt(0) + d.slice(1).toLowerCase(),
}));

function toDateString(value: unknown): string | null {
  if (!value) return null;
  if (value instanceof Date) {
    const y = value.getFullYear();
    const m = String(value.getMonth() + 1).padStart(2, '0');
    const d = String(value.getDate()).padStart(2, '0');
    return `${y}-${m}-${d}`;
  }
  return String(value);
}

/** Spec-driven form used by the configuration screens; maps server field errors onto inputs. */
export function EntityForm({ fields, initialValues, onSubmit, onCancel, submitLabel }: Props) {
  const { t } = useTranslation();
  const form = useForm<FormValues>({
    initialValues,
    validate: Object.fromEntries(
      fields
        .filter((f) => f.required)
        .map((f) => [
          f.name,
          (v: unknown, values: FormValues) =>
            f.visible && !f.visible(values)
              ? null
              : v === null || v === undefined || v === '' || (Array.isArray(v) && v.length === 0)
                ? t('auth.required')
                : null,
        ]),
    ),
  });

  const submit = form.onSubmit(async (values) => {
    const normalized: FormValues = { ...values };
    for (const f of fields) {
      if (f.type === 'date') normalized[f.name] = toDateString(values[f.name]);
      if (f.type === 'text' || f.type === 'textarea' || f.type === undefined) {
        const v = values[f.name];
        normalized[f.name] = typeof v === 'string' && v.trim() === '' ? null : v;
      }
      if (f.type === 'time' && values[f.name] === '') normalized[f.name] = null;
    }
    try {
      await onSubmit(normalized);
    } catch (e) {
      if (!applyServerErrors(e, form.setErrors)) showError(e);
    }
  });

  return (
    <form onSubmit={submit} noValidate>
      <Stack gap="sm">
        {fields
          .filter((f) => !f.visible || f.visible(form.getValues()))
          .map((f) => {
            const key = form.key(f.name);
            const common = {
              label: f.label,
              description: f.description,
              placeholder: f.placeholder,
              withAsterisk: f.required,
            };
            switch (f.type) {
              case 'textarea':
                return <Textarea key={key} {...common} autosize minRows={2} {...form.getInputProps(f.name)} />;
              case 'number':
                return <NumberInput key={key} {...common} min={f.min} max={f.max} {...form.getInputProps(f.name)} />;
              case 'checkbox':
                return (
                  <Checkbox key={key} label={f.label} description={f.description}
                    {...form.getInputProps(f.name, { type: 'checkbox' })} />
                );
              case 'select':
                return <Select key={key} {...common} data={f.options ?? []} clearable={!f.required} searchable {...form.getInputProps(f.name)} />;
              case 'multiselect':
                return <MultiSelect key={key} {...common} data={f.options ?? []} searchable {...form.getInputProps(f.name)} />;
              case 'date':
                return <DateInput key={key} {...common} valueFormat="YYYY-MM-DD" clearable {...form.getInputProps(f.name)} />;
              case 'time':
                return <TimeInput key={key} {...common} {...form.getInputProps(f.name)} />;
              default:
                return <TextInput key={key} {...common} {...form.getInputProps(f.name)} />;
            }
          })}
        <Group justify="flex-end" mt="sm">
          {onCancel && (
            <Button variant="default" onClick={onCancel}>
              {t('app.cancel')}
            </Button>
          )}
          <Button type="submit" loading={form.submitting}>
            {submitLabel ?? t('app.save')}
          </Button>
        </Group>
      </Stack>
    </form>
  );
}
