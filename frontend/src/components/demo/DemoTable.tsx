import type { ReactNode } from 'react';

type DemoTableProps<T> = {
  columns: Array<{ key: string; label: string; className?: string; render: (item: T) => ReactNode }>;
  data: T[];
  getKey: (item: T) => string | number;
};

export function DemoTable<T>({ columns, data, getKey }: DemoTableProps<T>) {
  return (
    <div className="overflow-hidden rounded-xl border border-clinic-border bg-white">
      <table className="w-full border-collapse text-left text-sm">
        <thead className="bg-teal-50/60 text-xs font-bold uppercase tracking-wide text-clinic-muted">
          <tr>
            {columns.map((column) => (
              <th key={column.key} className={`px-4 py-3 ${column.className ?? ''}`}>
                {column.label}
              </th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-clinic-border/70">
          {data.map((item) => (
            <tr key={getKey(item)} className="bg-white transition hover:bg-teal-50/35">
              {columns.map((column) => (
                <td key={column.key} className={`px-4 py-3 align-middle ${column.className ?? ''}`}>
                  {column.render(item)}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
