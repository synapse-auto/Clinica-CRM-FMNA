'use client';

import { Check, ChevronDown, Search } from 'lucide-react';
import { Combobox } from '@base-ui/react/combobox';
import { Select } from '@base-ui/react/select';
import { cn } from '@/lib/utils';

export type FormOption = {
  value: string;
  label: string;
  disabled?: boolean;
};

type CommonProps = {
  id?: string;
  name?: string;
  value?: string;
  defaultValue?: string;
  onValueChange?: (value: string) => void;
  options: FormOption[];
  placeholder?: string;
  emptyOptionLabel?: string;
  disabled?: boolean;
  required?: boolean;
  className?: string;
  'aria-label'?: string;
};

const triggerClassName = 'flex h-10 w-full items-center justify-between gap-2 rounded-lg border border-clinic-border bg-clinic-input px-3 text-left text-sm text-clinic-text outline-none transition focus-visible:border-clinic-primary focus-visible:ring-2 focus-visible:ring-clinic-primary/15 disabled:cursor-not-allowed disabled:opacity-60';
const popupClassName = 'z-[70] max-h-64 min-w-[var(--anchor-width)] overflow-auto rounded-lg border border-clinic-border bg-clinic-surface p-1 shadow-xl custom-scrollbar';
const itemClassName = 'flex min-h-9 cursor-pointer items-center justify-between gap-2 rounded-md px-2.5 py-2 text-sm text-clinic-text outline-none data-[highlighted]:bg-clinic-soft data-[selected]:bg-clinic-primary/10 data-[selected]:font-semibold data-[disabled]:cursor-not-allowed data-[disabled]:opacity-50';

export function FormSelect({
  id,
  name,
  value,
  defaultValue,
  onValueChange,
  options,
  placeholder = 'Selecione...',
  emptyOptionLabel,
  disabled = false,
  required = false,
  className,
  'aria-label': ariaLabel,
}: CommonProps) {
  return (
    <Select.Root
      id={id}
      name={name}
      value={value === undefined ? undefined : value || null}
      defaultValue={value === undefined ? defaultValue || null : undefined}
      onValueChange={(nextValue) => onValueChange?.(String(nextValue ?? ''))}
      disabled={disabled}
      required={required}
      items={[
        ...(emptyOptionLabel ? [{ value: '', label: emptyOptionLabel }] : []),
        ...options.map(({ value: optionValue, label }) => ({ value: optionValue, label })),
      ]}
    >
      <Select.Trigger aria-label={ariaLabel} className={cn(triggerClassName, className)}>
        <Select.Value placeholder={placeholder} />
        <Select.Icon><ChevronDown className="h-4 w-4 shrink-0 text-clinic-muted" /></Select.Icon>
      </Select.Trigger>
      <Select.Portal>
        <Select.Positioner sideOffset={6} className="z-[70]">
          <Select.Popup className={popupClassName}>
            <Select.List>
              {emptyOptionLabel ? (
                <Select.Item value="" className={itemClassName}>
                  <Select.ItemText>{emptyOptionLabel}</Select.ItemText>
                  <Select.ItemIndicator><Check className="h-4 w-4 text-clinic-primary" /></Select.ItemIndicator>
                </Select.Item>
              ) : null}
              {options.map((option) => (
                <Select.Item key={option.value} value={option.value} disabled={option.disabled} className={itemClassName}>
                  <Select.ItemText>{option.label}</Select.ItemText>
                  <Select.ItemIndicator><Check className="h-4 w-4 text-clinic-primary" /></Select.ItemIndicator>
                </Select.Item>
              ))}
            </Select.List>
          </Select.Popup>
        </Select.Positioner>
      </Select.Portal>
    </Select.Root>
  );
}

export function SearchableSelect({
  id,
  name,
  value,
  defaultValue,
  onValueChange,
  options,
  placeholder = 'Buscar...',
  disabled = false,
  required = false,
  className,
  'aria-label': ariaLabel,
}: CommonProps) {
  return (
    <Combobox.Root
      id={id}
      name={name}
      value={value === undefined ? undefined : value || null}
      defaultValue={value === undefined ? defaultValue || null : undefined}
      onValueChange={(nextValue) => onValueChange?.(String(nextValue ?? ''))}
      disabled={disabled}
      required={required}
      items={options.map(({ value: optionValue, label }) => ({ value: optionValue, label }))}
      autoHighlight
    >
      <div className={cn(triggerClassName, 'px-0', className)}>
        <Search className="ml-3 h-4 w-4 shrink-0 text-clinic-muted" aria-hidden="true" />
        <Combobox.Input aria-label={ariaLabel} placeholder={placeholder} className="h-full min-w-0 flex-1 bg-transparent px-2 text-sm text-clinic-text outline-none placeholder:text-clinic-muted" />
      </div>
      <Combobox.Portal>
        <Combobox.Positioner sideOffset={6} className="z-[70]">
          <Combobox.Popup className={popupClassName}>
            <Combobox.List>
              {options.map((option) => (
                <Combobox.Item key={option.value} value={option.value} disabled={option.disabled} className={itemClassName}>
                  <span>{option.label}</span>
                  <Combobox.ItemIndicator><Check className="h-4 w-4 text-clinic-primary" /></Combobox.ItemIndicator>
                </Combobox.Item>
              ))}
            </Combobox.List>
            <Combobox.Empty className="px-3 py-4 text-sm text-clinic-muted">Nenhuma opção encontrada.</Combobox.Empty>
          </Combobox.Popup>
        </Combobox.Positioner>
      </Combobox.Portal>
    </Combobox.Root>
  );
}
