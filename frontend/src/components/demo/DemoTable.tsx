import type { ReactNode } from 'react';

type DemoTableProps<T> = {
  columns: Array<{ key: string; label: string; className?: string; render: (item: T) => ReactNode }>;
  data: T[];
  getKey: (item: T) => string | number;
};

export function DemoTable<T>({ columns, data, getKey }: DemoTableProps<T>) {
  return (
    <div className="overflow-x-auto rounded-lg border border-clinic-border bg-clinic-surface">
      <table className="w-full min-w-[680px] border-collapse text-left text-[11px]">
        <thead className="bg-clinic-table-head text-[9px] font-bold uppercase text-clinic-muted">
          <tr>
            {columns.map((column) => (
              <th key={column.key} className={`px-3 py-2.5 ${column.className ?? ''}`}>
                {column.label}
              </th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-clinic-border/70">
          {data.map((item) => (
            <tr key={getKey(item)} className="bg-clinic-surface transition hover:bg-clinic-hover">
              {columns.map((column) => (
                <td key={column.key} className={`px-3 py-3 align-middle text-clinic-muted ${column.className ?? ''}`}>
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
