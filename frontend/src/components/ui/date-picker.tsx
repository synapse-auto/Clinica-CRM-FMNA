'use client';

import { useMemo, useState } from 'react';
import { CalendarDays, X } from 'lucide-react';
import { Popover } from '@base-ui/react/popover';
import { DayPicker } from 'react-day-picker';
import { format, parse } from 'date-fns';
import { ptBR } from 'date-fns/locale';
import { cn } from '@/lib/utils';

type DatePickerProps = {
  id?: string;
  value: string;
  onValueChange: (value: string) => void;
  placeholder?: string;
  disabled?: boolean;
  required?: boolean;
  min?: string;
  max?: string;
  className?: string;
  'aria-label'?: string;
};

function parseDate(value: string) {
  if (!value) return undefined;
  const parsed = parse(value, 'yyyy-MM-dd', new Date());
  return Number.isNaN(parsed.getTime()) ? undefined : parsed;
}

export function DatePicker({
  id,
  value,
  onValueChange,
  placeholder = 'Selecionar data',
  disabled = false,
  required = false,
  min,
  max,
  className,
  'aria-label': ariaLabel,
}: DatePickerProps) {
  const [open, setOpen] = useState(false);
  const selected = useMemo(() => parseDate(value), [value]);
  const fromDate = useMemo(() => parseDate(min ?? ''), [min]);
  const toDate = useMemo(() => parseDate(max ?? ''), [max]);
  const displayValue = selected ? format(selected, 'dd/MM/yyyy', { locale: ptBR }) : placeholder;

  return (
    <Popover.Root open={open} onOpenChange={setOpen}>
      <Popover.Trigger
        id={id}
        aria-label={ariaLabel}
        aria-required={required || undefined}
        disabled={disabled}
        className={cn(
          'flex h-10 w-full items-center justify-between gap-2 rounded-lg border border-clinic-border bg-clinic-input px-3 text-left text-sm text-clinic-text outline-none transition focus-visible:border-clinic-primary focus-visible:ring-2 focus-visible:ring-clinic-primary/15 disabled:cursor-not-allowed disabled:opacity-60',
          !selected && 'text-clinic-muted',
          className,
        )}
      >
        <span>{displayValue}</span>
        <CalendarDays className="h-4 w-4 shrink-0 text-clinic-muted" />
      </Popover.Trigger>
      <Popover.Portal>
        <Popover.Positioner sideOffset={6} className="z-[70]">
          <Popover.Popup className="rounded-lg border border-clinic-border bg-clinic-surface p-3 shadow-xl">
            <DayPicker
              mode="single"
              selected={selected}
              onSelect={(date) => {
                onValueChange(date ? format(date, 'yyyy-MM-dd') : '');
                setOpen(false);
              }}
              locale={ptBR}
              weekStartsOn={0}
              disabled={[
                ...(fromDate ? [{ before: fromDate }] : []),
                ...(toDate ? [{ after: toDate }] : []),
              ]}
              classNames={{
                months: 'flex',
                month: 'space-y-2',
                month_caption: 'relative flex h-8 items-center justify-center text-sm font-bold text-clinic-text',
                caption_label: 'capitalize',
                nav: 'absolute inset-x-0 top-0 flex items-center justify-between',
                button_previous: 'flex h-7 w-7 items-center justify-center rounded-md text-clinic-muted hover:bg-clinic-soft',
                button_next: 'flex h-7 w-7 items-center justify-center rounded-md text-clinic-muted hover:bg-clinic-soft',
                month_grid: 'w-full border-collapse',
                weekdays: 'flex',
                weekday: 'w-9 py-1 text-center text-[10px] font-bold text-clinic-muted',
                week: 'mt-1 flex w-full',
                day: 'h-9 w-9 p-0 text-center',
                day_button: 'h-8 w-8 rounded-md text-xs font-semibold text-clinic-text hover:bg-clinic-soft aria-selected:bg-clinic-primary aria-selected:text-white disabled:opacity-40',
                selected: '',
                today: '[&>button]:border [&>button]:border-clinic-primary',
                outside: 'opacity-40',
                disabled: 'opacity-40',
              }}
            />
            {!required && value ? (
              <button
                type="button"
                onClick={() => { onValueChange(''); setOpen(false); }}
                className="mt-2 flex h-8 w-full items-center justify-center gap-1 rounded-md text-xs font-semibold text-clinic-muted hover:bg-clinic-soft hover:text-clinic-text"
              >
                <X className="h-3.5 w-3.5" /> Limpar data
              </button>
            ) : null}
          </Popover.Popup>
        </Popover.Positioner>
      </Popover.Portal>
    </Popover.Root>
  );
}
