import Link from 'next/link';

type SegmentedTabsProps = {
  items: Array<{ label: string; href: string; active?: boolean; count?: number }>;
};

export function SegmentedTabs({ items }: SegmentedTabsProps) {
  return (
    <div className="inline-flex rounded-xl border border-clinic-border bg-white p-1 shadow-sm">
      {items.map((item) => (
        <Link
          key={item.href}
          href={item.href}
          className={`flex h-9 items-center gap-2 rounded-lg px-4 text-sm font-bold transition ${
            item.active
              ? 'bg-clinic-primary text-white'
              : 'text-clinic-muted hover:bg-teal-50 hover:text-clinic-text'
          }`}
        >
          {item.label}
          {typeof item.count === 'number' ? (
            <span className={`rounded-full px-1.5 text-[10px] ${item.active ? 'bg-white/20' : 'bg-slate-100 text-clinic-muted'}`}>
              {item.count}
            </span>
          ) : null}
        </Link>
      ))}
    </div>
  );
}
