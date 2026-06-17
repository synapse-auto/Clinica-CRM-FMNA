type StatusBadgeProps = {
  children: string;
  tone?: 'teal' | 'blue' | 'orange' | 'pink' | 'slate' | 'green';
};

const tones = {
  teal: 'bg-teal-50 text-teal-700 border-teal-100',
  blue: 'bg-blue-50 text-blue-700 border-blue-100',
  orange: 'bg-orange-50 text-orange-700 border-orange-100',
  pink: 'bg-pink-50 text-pink-700 border-pink-100',
  slate: 'bg-slate-50 text-slate-600 border-slate-200',
  green: 'bg-emerald-50 text-emerald-700 border-emerald-100',
};

export function StatusBadge({ children, tone = 'teal' }: StatusBadgeProps) {
  return (
    <span className={`inline-flex items-center rounded-full border px-2.5 py-1 text-xs font-bold ${tones[tone]}`}>
      {children}
    </span>
  );
}
