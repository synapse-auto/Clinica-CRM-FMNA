'use client';

import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import { useEffect, useState } from 'react';
import {
  BadgeHelp,
  Calendar,
  ChevronRight,
  Clock,
  LayoutDashboard,
  LogOut,
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
import { menuItemsForProfile } from '@/lib/auth/permissions';
import type { AuthUser } from '@/lib/auth/types';
import type { ClinicaAtualResponse } from '@/types/dashboard';
import { getNotificacoesResumo } from '@/services/atendimentos';

const menuIcons = {
  '/atendimentos': MessageSquare,
  '/dashboard': LayoutDashboard,
  '/agenda': Calendar,
  '/pacientes': Users,
  '/equipe': UserPlus,
  '/automacao-ia': Zap,
  '/tags': Tag,
  '/msgs-rapidas': BadgeHelp,
  '/horarios': Clock,
  '/configuracoes': Settings,
} as const;

type DemoSidebarProps = {
  clinic: ClinicaAtualResponse;
  user: AuthUser;
};

export function DemoSidebar({ clinic, user }: DemoSidebarProps) {
  const pathname = usePathname();
  const router = useRouter();
  const { theme, toggleTheme } = useTheme();
  const [atendimentosBadge, setAtendimentosBadge] = useState(0);
  const initials = user.nome
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join('') || 'US';
  const menuItems = menuItemsForProfile(user.perfil);

  useEffect(() => {
    function updateBadge(event: Event) {
      setAtendimentosBadge(Number((event as CustomEvent<number>).detail) || 0);
    }
    async function refreshBadge() {
      try {
        setAtendimentosBadge(await getNotificacoesResumo());
      } catch {
        setAtendimentosBadge(0);
      }
    }
    window.addEventListener('atendimentos:badge', updateBadge);
    void refreshBadge();
    const interval = window.setInterval(() => void refreshBadge(), 15000);
    return () => {
      window.removeEventListener('atendimentos:badge', updateBadge);
      window.clearInterval(interval);
    };
  }, []);

  async function logout() {
    await fetch('/api/auth/logout', { method: 'POST' });
    router.replace('/login');
    router.refresh();
  }

  return (
    <aside className="flex h-screen w-[244px] shrink-0 flex-col bg-sidebar text-sidebar-foreground">
      <div className="flex h-[80px] items-center gap-3 border-b border-sidebar-border px-5">
        <div className="flex h-10 w-10 items-center justify-center rounded-lg border border-sidebar-primary/25 bg-sidebar-accent text-sidebar-primary">
          <Stethoscope className="h-5 w-5" />
        </div>
        <div className="min-w-0">
          <p className="truncate text-[15px] font-bold leading-tight text-white">{clinic.nome}</p>
          <p className="text-xs text-sidebar-foreground/65">CRM</p>
        </div>
      </div>

      <div className="flex-1 overflow-y-auto px-3 py-4 custom-scrollbar">
        <p className="mb-3 px-2 text-[11px] font-semibold uppercase tracking-[0.12em] text-sidebar-foreground/45">
          Menu
        </p>
        <nav className="space-y-1">
          {menuItems.map((item) => {
            const Icon = menuIcons[item.href as keyof typeof menuIcons];
            const active = pathname === item.href || pathname.startsWith(`${item.href}/`);
            const badge = item.href === '/atendimentos' ? atendimentosBadge : item.badge;
            return (
              <Link
                key={item.href}
                href={item.href}
                className={`group flex h-[46px] items-center justify-between rounded-lg px-3 text-[15px] font-semibold transition ${
                  active
                    ? 'bg-sidebar-accent text-white'
                    : 'text-sidebar-foreground hover:bg-sidebar-accent/55 hover:text-white'
                }`}
              >
                <span className="flex min-w-0 items-center gap-3">
                  <Icon className={`h-5 w-5 shrink-0 ${active ? 'text-sidebar-primary' : 'text-sidebar-foreground/60'}`} />
                  <span className="truncate">{item.name}</span>
                </span>
                {badge ? (
                  <span className="min-w-5 rounded-full bg-clinic-danger px-1.5 py-0.5 text-center text-[11px] font-bold text-white">
                    {badge}
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
          className="mb-2 flex h-11 w-full items-center gap-3 rounded-lg px-3 text-left text-[15px] font-semibold text-sidebar-foreground transition hover:bg-sidebar-accent/55 hover:text-white"
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
            <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-sidebar-primary text-sm font-bold text-sidebar-primary-foreground ring-2 ring-sidebar-primary/20">
              {initials}
            </div>
            <div className="min-w-0">
              <p className="truncate text-[15px] font-bold text-white">{user.nome}</p>
              <p className="truncate text-xs text-sidebar-foreground/65">{formatProfile(user.perfil)}</p>
            </div>
          </div>
          <button
            type="button"
            aria-label="Sair"
            onClick={logout}
            className="rounded-md p-1.5 text-sidebar-foreground/55 transition hover:bg-sidebar-accent hover:text-white"
          >
            <LogOut className="h-4 w-4" />
          </button>
        </div>
      </div>
    </aside>
  );
}

function formatProfile(profile: AuthUser['perfil']) {
  if (profile === 'RECEPCIONISTA') return 'Recepcionista';
  if (profile === 'MEDICO') return 'Médico';
  return 'Gestor';
}
