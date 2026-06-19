import Link from 'next/link';

type SegmentedTabsProps = {
  items: Array<{ label: string; href: string; active?: boolean; count?: number }>;
};

export function SegmentedTabs({ items }: SegmentedTabsProps) {
  return (
    <div className="inline-flex rounded-lg border border-clinic-border bg-clinic-surface-muted p-0.5">
      {items.map((item) => (
        <Link
          key={item.href}
          href={item.href}
          className={`flex h-7 items-center gap-1.5 rounded-md px-3 text-[10px] font-semibold transition ${
            item.active
              ? 'bg-clinic-primary text-white shadow-sm'
              : 'text-clinic-muted hover:bg-clinic-hover hover:text-clinic-text'
          }`}
        >
          {item.label}
          {typeof item.count === 'number' ? (
            <span className={`rounded-full px-1.5 text-[9px] ${item.active ? 'bg-primary-foreground/20' : 'bg-clinic-soft text-clinic-muted'}`}>
              {item.count}
            </span>
          ) : null}
        </Link>
      ))}
    </div>
  );
}
