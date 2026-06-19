type StatusBadgeProps = {
  children: string;
  tone?: 'teal' | 'blue' | 'orange' | 'pink' | 'slate' | 'green';
};

const tones = {
  teal: 'border-clinic-primary/20 bg-clinic-primary/10 text-clinic-primary',
  blue: 'border-clinic-blue/20 bg-clinic-blue/10 text-clinic-blue',
  orange: 'border-clinic-orange/20 bg-clinic-orange/10 text-clinic-orange',
  pink: 'border-clinic-pink/20 bg-clinic-pink/10 text-clinic-pink',
  slate: 'border-clinic-muted/20 bg-clinic-muted/10 text-clinic-muted',
  green: 'border-clinic-success/20 bg-clinic-success/10 text-clinic-success',
};

export function StatusBadge({ children, tone = 'teal' }: StatusBadgeProps) {
  return (
    <span className={`inline-flex items-center rounded-full border px-2 py-0.5 text-[9px] font-bold ${tones[tone]}`}>
      {children}
    </span>
  );
}
