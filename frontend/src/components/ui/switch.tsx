import { Switch as SwitchPrimitive } from '@base-ui/react/switch';
import type { ComponentProps } from 'react';
import { cn } from '@/lib/utils';

type SwitchProps = Omit<ComponentProps<typeof SwitchPrimitive.Root>, 'children'> & {
  label: string;
};

export function Switch({ label, className, ...props }: SwitchProps) {
  return (
    <label className={cn('inline-flex min-h-8 items-center gap-2 text-[10px] font-bold text-clinic-text', className)}>
      <SwitchPrimitive.Root
        {...props}
        className="relative inline-flex h-5 w-9 shrink-0 cursor-pointer items-center rounded-full bg-clinic-border p-0.5 outline-none transition-colors data-[checked]:bg-clinic-primary data-[disabled]:cursor-not-allowed data-[disabled]:opacity-50 focus-visible:ring-2 focus-visible:ring-clinic-primary/40"
      >
        <SwitchPrimitive.Thumb className="pointer-events-none block h-4 w-4 translate-x-0 rounded-full bg-white shadow-sm transition-transform data-[checked]:translate-x-4" />
      </SwitchPrimitive.Root>
      <span>{label}</span>
    </label>
  );
}
