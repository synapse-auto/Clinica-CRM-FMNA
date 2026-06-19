'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import {
  BadgeHelp,
  Calendar,
  ChevronRight,
  Clock,
  LayoutDashboard,
  MessageSquare,
  Moon,
  Settings,
  Stethoscope,
  Sun,
  Tag,
  UserPlus,
  Users,
  Zap,
} from 'lucide-react';
import { useTheme } from '@/components/theme/ThemeProvider';
import type { ClinicaAtualResponse } from '@/types/dashboard';

const menuItems = [
  { name: 'Atendimentos', icon: MessageSquare, href: '/atendimentos', badge: 4 },
  { name: 'Dashboard', icon: LayoutDashboard, href: '/dashboard' },
  { name: 'Agenda', icon: Calendar, href: '/agenda' },
  { name: 'Pacientes', icon: Users, href: '/pacientes' },
  { name: 'Equipe', icon: UserPlus, href: '/equipe' },
  { name: 'Automação', icon: Zap, href: '/automacao-ia' },
  { name: 'Tags', icon: Tag, href: '/tags' },
  { name: 'Msgs Rápidas', icon: BadgeHelp, href: '/msgs-rapidas' },
  { name: 'Horários', icon: Clock, href: '/horarios' },
  { name: 'Configurações', icon: Settings, href: '/configuracoes' },
];

type DemoSidebarProps = {
  clinic: ClinicaAtualResponse;
};

export function DemoSidebar({ clinic }: DemoSidebarProps) {
  const pathname = usePathname();
  const { theme, toggleTheme } = useTheme();
  const initials = clinic.nome
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join('') || 'CL';

  return (
    <aside className="flex h-screen w-[218px] shrink-0 flex-col bg-sidebar text-sidebar-foreground">
      <div className="flex h-[72px] items-center gap-3 border-b border-sidebar-border px-5">
        <div className="flex h-9 w-9 items-center justify-center rounded-lg border border-sidebar-primary/25 bg-sidebar-accent text-sidebar-primary">
          <Stethoscope className="h-5 w-5" />
        </div>
        <div className="min-w-0">
          <p className="truncate text-sm font-bold leading-tight text-white">{clinic.nome}</p>
          <p className="text-[11px] text-sidebar-foreground/65">CRM</p>
        </div>
      </div>

      <div className="flex-1 overflow-y-auto px-3 py-4 custom-scrollbar">
        <p className="mb-3 px-2 text-[10px] font-semibold uppercase tracking-[0.18em] text-sidebar-foreground/45">
          Menu
        </p>
        <nav className="space-y-1">
          {menuItems.map((item) => {
            const active = pathname === item.href || pathname.startsWith(`${item.href}/`);
            return (
              <Link
                key={item.href}
                href={item.href}
                className={`group flex h-[42px] items-center justify-between rounded-lg px-3 text-sm font-semibold transition ${
                  active
                    ? 'bg-sidebar-accent text-white'
                    : 'text-sidebar-foreground hover:bg-sidebar-accent/55 hover:text-white'
                }`}
              >
                <span className="flex min-w-0 items-center gap-3">
                  <item.icon className={`h-4.5 w-4.5 shrink-0 ${active ? 'text-sidebar-primary' : 'text-sidebar-foreground/60'}`} />
                  <span className="truncate">{item.name}</span>
                </span>
                {item.badge ? (
                  <span className="min-w-5 rounded-full bg-clinic-danger px-1.5 py-0.5 text-center text-[10px] font-bold text-white">
                    {item.badge}
                  </span>
                ) : active ? (
                  <ChevronRight className="h-4 w-4 text-sidebar-primary" />
                ) : null}
              </Link>
            );
          })}
        </nav>

      </div>

      <div className="border-t border-sidebar-border p-3">
        <button
          type="button"
          onClick={toggleTheme}
          aria-label={`Ativar tema ${theme === 'dark' ? 'claro' : 'escuro'}`}
          className="mb-2 flex h-10 w-full items-center gap-3 rounded-lg px-3 text-left text-sm font-semibold text-sidebar-foreground transition hover:bg-sidebar-accent/55 hover:text-white"
        >
          {theme === 'dark' ? (
            <Sun className="h-4 w-4 text-sidebar-foreground/65" />
          ) : (
            <Moon className="h-4 w-4 text-sidebar-foreground/65" />
          )}
          Tema {theme === 'dark' ? 'Claro' : 'Escuro'}
        </button>
        <div className="flex items-center justify-between rounded-lg p-2 transition hover:bg-sidebar-accent/55">
          <div className="flex min-w-0 items-center gap-3">
            <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-sidebar-primary text-xs font-bold text-sidebar-primary-foreground ring-2 ring-sidebar-primary/20">
              {initials}
            </div>
            <div className="min-w-0">
              <p className="truncate text-sm font-bold text-white">{clinic.nome}</p>
              <p className="truncate text-[11px] text-sidebar-foreground/65">
                {clinic.tipoClinica === 'ULTRASSONOGRAFIA' ? 'Ultrassonografia' : 'Gestor'}
              </p>
            </div>
          </div>
          <ChevronRight className="h-4 w-4 shrink-0 text-sidebar-foreground/45" />
        </div>
      </div>
    </aside>
  );
}
