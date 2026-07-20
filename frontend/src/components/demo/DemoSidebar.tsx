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
  PanelLeftClose,
  PanelLeftOpen,
  Settings,
  Sun,
  Tag,
  UserCircle,
  UserPlus,
  Users,
  Zap,
} from 'lucide-react';
import Image from 'next/image';
import { useTheme } from '@/components/theme/ThemeProvider';
import { brandingInitials, publicBranding } from '@/config/public-branding';
import { menuItemsForProfile } from '@/lib/auth/permissions';
import type { AuthUser } from '@/lib/auth/types';
import type { ClinicaAtualResponse } from '@/types/dashboard';
import { getNotificacoesResumo } from '@/services/atendimentos';

const ATTENDIMENTOS_SIDEBAR_STORAGE_KEY = 'clinica-crm-atendimentos-sidebar-expanded';
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
  '/minha-conta': UserCircle,
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
  const [sidebarExpanded, setSidebarExpanded] = useState<boolean | null>(null);
  const compactable = pathname === '/atendimentos' || pathname.startsWith('/atendimentos/');
  const initials = user.nome
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join('') || 'US';
  const menuItems = menuItemsForProfile(user.perfil, user.podeGerenciarUsuarios);

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

  useEffect(() => {
    if (!compactable) return;
    const stored = window.localStorage.getItem(ATTENDIMENTOS_SIDEBAR_STORAGE_KEY);
    if (stored === 'true' || stored === 'false') {
      setSidebarExpanded(stored === 'true');
      return;
    }
    setSidebarExpanded(window.matchMedia?.('(min-width: 1440px)').matches ?? false);
  }, [compactable]);

  async function logout() {
    await fetch('/api/auth/logout', { method: 'POST' });
    router.replace('/login');
    router.refresh();
  }

  function toggleSidebar() {
    const nextExpanded = !(sidebarExpanded ?? window.matchMedia?.('(min-width: 1440px)').matches ?? false);
    setSidebarExpanded(nextExpanded);
    window.localStorage.setItem(ATTENDIMENTOS_SIDEBAR_STORAGE_KEY, String(nextExpanded));
  }

  const railWidthClass = !compactable
    ? 'w-[256px]'
    : sidebarExpanded === true
      ? 'w-[256px]'
      : sidebarExpanded === false
        ? 'w-[256px] md:w-16'
        : 'w-[256px] md:max-[1439px]:w-16';
  const sidebarWidthClass = !compactable
    ? 'w-[256px]'
    : sidebarExpanded === true
      ? 'w-[256px]'
      : sidebarExpanded === false
        ? 'w-[256px] md:w-16 md:hover:w-[256px] md:focus-within:w-[256px]'
        : 'w-[256px] md:max-[1439px]:w-16 md:max-[1439px]:hover:w-[256px] md:max-[1439px]:focus-within:w-[256px]';
  const temporaryLabelClass = compactable && sidebarExpanded !== true
    ? 'opacity-0 transition-opacity group-hover/sidebar:opacity-100 group-focus-within/sidebar:opacity-100 max-md:opacity-100'
    : '';

  return (
    <div
      className={`relative h-screen shrink-0 transition-[width] duration-150 ${railWidthClass}`}
      data-testid="sidebar-rail"
    >
    <aside
      className={`${compactable ? 'group/sidebar absolute inset-y-0 left-0 z-40 overflow-hidden transition-[width] duration-150 hover:shadow-2xl focus-within:shadow-2xl max-md:static' : ''} flex h-screen shrink-0 flex-col bg-sidebar text-sidebar-foreground ${sidebarWidthClass}`}
      data-testid="main-sidebar"
    >
      <div className={`flex h-[84px] items-center gap-3 border-b border-sidebar-border ${compactable ? 'px-2' : 'px-6'}`}>
        <div className="flex h-11 w-11 items-center justify-center">
          {publicBranding.logoUrl ? (
            <Image src={publicBranding.logoUrl} alt={clinic.nome} width={44} height={44} priority className="h-11 w-11 object-contain" />
          ) : (
            <span aria-label={`${clinic.nome} sem logotipo`} className="flex h-10 w-10 items-center justify-center rounded-xl bg-sidebar-primary text-xs font-extrabold text-sidebar-primary-foreground">
              {brandingInitials(clinic.nome)}
            </span>
          )}
        </div>
        <div className={`min-w-0 ${temporaryLabelClass}`}>
          <p className="truncate text-[15px] font-bold leading-tight text-white">{clinic.nome}</p>
          <p className="text-xs text-sidebar-foreground/65">CRM</p>
        </div>
      </div>

      <div className={`flex-1 overflow-y-auto py-5 custom-scrollbar ${compactable ? 'px-2' : 'px-4'}`}>
        {compactable ? (
          <button
            type="button"
            aria-label={sidebarExpanded === true ? 'Recolher barra lateral' : 'Fixar barra lateral expandida'}
            aria-pressed={sidebarExpanded === true}
            title={sidebarExpanded === true ? 'Recolher barra lateral' : 'Fixar barra lateral expandida'}
            onClick={toggleSidebar}
            className="mb-3 flex h-10 w-full min-w-[240px] items-center gap-3 rounded-xl px-3 text-[13px] font-semibold text-sidebar-foreground transition hover:bg-sidebar-accent/55 hover:text-white focus-visible:outline-2 focus-visible:outline-sidebar-primary"
          >
            {sidebarExpanded === true ? <PanelLeftClose className="h-5 w-5 shrink-0" /> : <PanelLeftOpen className="h-5 w-5 shrink-0" />}
            <span className={temporaryLabelClass}>{sidebarExpanded === true ? 'Recolher navegação' : 'Fixar navegação'}</span>
          </button>
        ) : (
          <p className="mb-3 px-2 text-[10px] font-semibold uppercase tracking-[0.14em] text-sidebar-foreground/55">
            Menu
          </p>
        )}
        <nav className="space-y-1">
          {menuItems.map((item) => {
            const Icon = menuIcons[item.href as keyof typeof menuIcons];
            const active = pathname === item.href || pathname.startsWith(`${item.href}/`);
            const badge = item.href === '/atendimentos' ? atendimentosBadge : item.badge;
            return (
              <Link
                key={item.href}
                href={item.href}
                title={compactable ? item.name : undefined}
                className={`group relative flex h-[46px] items-center justify-between rounded-xl px-3 text-[14px] font-semibold transition ${compactable ? 'min-w-[240px]' : ''} ${
                  active
                    ? 'bg-sidebar-accent text-white shadow-sm'
                    : 'text-sidebar-foreground hover:bg-sidebar-accent/55 hover:text-white'
                }`}
              >
                <span className="flex min-w-0 items-center gap-3">
                  <Icon className={`h-5 w-5 shrink-0 ${active ? 'text-sidebar-primary' : 'text-sidebar-foreground/60'}`} />
                  <span className={`truncate ${temporaryLabelClass}`}>{item.name}</span>
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

      <div className={`border-t border-sidebar-border ${compactable ? 'p-2' : 'p-4'}`}>
        <button
          type="button"
          onClick={toggleTheme}
          aria-label={`Ativar tema ${theme === 'dark' ? 'claro' : 'escuro'}`}
          title={compactable ? `Tema ${theme === 'dark' ? 'claro' : 'escuro'}` : undefined}
          className={`mb-3 flex h-11 w-full items-center gap-3 rounded-xl px-3 text-left text-[14px] font-semibold text-sidebar-foreground transition hover:bg-sidebar-accent/55 hover:text-white ${compactable ? 'min-w-[240px]' : ''}`}
        >
          {theme === 'dark' ? (
            <Sun className="h-4 w-4 text-sidebar-foreground/65" />
          ) : (
            <Moon className="h-4 w-4 text-sidebar-foreground/65" />
          )}
          <span className={temporaryLabelClass}>Tema {theme === 'dark' ? 'Claro' : 'Escuro'}</span>
        </button>
        <div className={`flex items-center justify-between gap-2 rounded-xl p-2 transition hover:bg-sidebar-accent/55 ${compactable ? 'min-w-[240px]' : ''}`}>
          <Link href="/minha-conta" className="flex min-w-0 flex-1 items-center gap-3 rounded-md focus:outline-none focus:ring-2 focus:ring-sidebar-primary/45">
            <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-sidebar-primary text-sm font-bold text-sidebar-primary-foreground ring-2 ring-sidebar-primary/20">
              {initials}
            </div>
            <div className={`min-w-0 ${temporaryLabelClass}`}>
              <p className="truncate text-[14px] font-bold text-white">{user.nome}</p>
              <p className="truncate text-xs text-sidebar-foreground/65">{formatProfile(user.perfil)}</p>
            </div>
          </Link>
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
    </div>
  );
}

function formatProfile(profile: AuthUser['perfil']) {
  if (profile === 'RECEPCIONISTA') return 'Recepcionista';
  if (profile === 'MEDICO') return 'Médico';
  return 'Gestor';
}
