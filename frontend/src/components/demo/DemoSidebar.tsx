'use client';

import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import { useEffect, useRef, useState } from 'react';
import type { FocusEvent } from 'react';
import {
  BadgeHelp,
  Calendar,
  ClipboardX,
  ChevronRight,
  Clock,
  LayoutDashboard,
  LogOut,
  MessageSquare,
  Moon,
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

const menuIcons = {
  '/atendimentos': MessageSquare,
  '/dashboard': LayoutDashboard,
  '/agenda': Calendar,
  '/cancelamentos': ClipboardX,
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
  const [keyboardExpanded, setKeyboardExpanded] = useState(false);
  const inputModalityRef = useRef<'pointer' | 'keyboard'>('pointer');
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

  function markKeyboardInput(event: { key: string }) {
    if (event.key === 'Tab' || event.key === 'Enter' || event.key === ' ') {
      inputModalityRef.current = 'keyboard';
    }
  }

  useEffect(() => {
    window.addEventListener('keydown', markKeyboardInput);
    return () => window.removeEventListener('keydown', markKeyboardInput);
  }, []);

  async function logout() {
    await fetch('/api/auth/logout', { method: 'POST' });
    router.replace('/login');
    router.refresh();
  }

  const sidebarDesktopWidthClass = keyboardExpanded
    ? 'md:w-[256px] md:shadow-2xl'
    : 'md:w-16 md:hover:w-[256px] md:hover:shadow-2xl';
  const compactLabelClass = keyboardExpanded
    ? 'opacity-100 transition-opacity duration-150'
    : 'opacity-0 transition-opacity duration-150 group-hover/sidebar:opacity-100 max-md:opacity-100';
  const badgePositionClass = keyboardExpanded
    ? 'md:left-auto md:right-3 md:top-1/2 md:-translate-y-1/2'
    : 'md:left-8 md:right-auto md:top-2 md:translate-y-0 md:group-hover/sidebar:left-auto md:group-hover/sidebar:right-3 md:group-hover/sidebar:top-1/2 md:group-hover/sidebar:-translate-y-1/2';

  function handlePointerDown() {
    inputModalityRef.current = 'pointer';
    setKeyboardExpanded(false);
  }

  function handleFocus() {
    if (inputModalityRef.current === 'keyboard') {
      setKeyboardExpanded(true);
    }
  }

  function handleBlur(event: FocusEvent<HTMLElement>) {
    if (!event.currentTarget.contains(event.relatedTarget as Node | null)) {
      setKeyboardExpanded(false);
    }
  }

  return (
    <div
      className="relative h-screen w-[256px] shrink-0 transition-[width] duration-150 md:w-16"
      data-testid="sidebar-rail"
    >
    <aside
      className={`group/sidebar absolute inset-y-0 left-0 z-40 flex h-screen w-[256px] shrink-0 flex-col overflow-hidden bg-sidebar text-sidebar-foreground transition-[width,box-shadow] duration-150 ${sidebarDesktopWidthClass} max-md:static`}
      data-testid="main-sidebar"
      data-keyboard-expanded={keyboardExpanded}
      onPointerDownCapture={handlePointerDown}
      onKeyDownCapture={markKeyboardInput}
      onFocusCapture={handleFocus}
      onBlurCapture={handleBlur}
    >
      <div className="flex h-[84px] items-center gap-3 border-b border-sidebar-border px-2">
        <div className="flex h-11 w-11 items-center justify-center">
          {publicBranding.logoUrl ? (
            <Image src={publicBranding.logoUrl} alt={clinic.nome} width={44} height={44} priority className="h-11 w-11 object-contain" />
          ) : (
            <span aria-label={`${clinic.nome} sem logotipo`} className="flex h-10 w-10 items-center justify-center rounded-xl bg-sidebar-primary text-xs font-extrabold text-sidebar-primary-foreground">
              {brandingInitials(clinic.nome)}
            </span>
          )}
        </div>
        <div className={`min-w-0 ${compactLabelClass}`}>
          <p className="truncate text-[15px] font-bold leading-tight text-white">{clinic.nome}</p>
          <p className="text-xs text-sidebar-foreground/65">CRM</p>
        </div>
      </div>

      <div className="custom-scrollbar flex-1 overflow-y-auto px-2 py-5">
        <p className={`mb-3 px-2 text-[10px] font-semibold uppercase tracking-[0.14em] text-sidebar-foreground/55 ${compactLabelClass}`}>
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
                title={item.name}
                className={`group relative flex h-[46px] min-w-[240px] items-center justify-between rounded-xl px-3 text-[14px] font-semibold transition ${
                  active
                    ? 'bg-sidebar-accent text-white shadow-sm'
                    : 'text-sidebar-foreground hover:bg-sidebar-accent/55 hover:text-white'
                }`}
              >
                <span className="flex min-w-0 items-center gap-3">
                  <Icon className={`h-5 w-5 shrink-0 ${active ? 'text-sidebar-primary' : 'text-sidebar-foreground/60'}`} />
                  <span className={`truncate ${compactLabelClass}`}>{item.name}</span>
                </span>
                {badge ? (
                  <span className={`absolute right-3 top-1/2 min-w-5 -translate-y-1/2 rounded-full bg-clinic-danger px-1.5 py-0.5 text-center text-[11px] font-bold text-white ${badgePositionClass}`}>
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

      <div className="border-t border-sidebar-border p-2">
        <button
          type="button"
          onClick={toggleTheme}
          aria-label={`Ativar tema ${theme === 'dark' ? 'claro' : 'escuro'}`}
          title={`Tema ${theme === 'dark' ? 'claro' : 'escuro'}`}
          className="mb-3 flex h-11 min-w-[240px] w-full items-center gap-3 rounded-xl px-3 text-left text-[14px] font-semibold text-sidebar-foreground transition hover:bg-sidebar-accent/55 hover:text-white"
        >
          {theme === 'dark' ? (
            <Sun className="h-4 w-4 text-sidebar-foreground/65" />
          ) : (
            <Moon className="h-4 w-4 text-sidebar-foreground/65" />
          )}
          <span className={compactLabelClass}>Tema {theme === 'dark' ? 'Claro' : 'Escuro'}</span>
        </button>
        <div className="flex min-w-[240px] items-center justify-between gap-2 rounded-xl p-2 transition hover:bg-sidebar-accent/55">
          <Link href="/minha-conta" title="Minha conta" className="flex min-w-0 flex-1 items-center gap-3 rounded-md focus:outline-none focus:ring-2 focus:ring-sidebar-primary/45">
            <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-sidebar-primary text-sm font-bold text-sidebar-primary-foreground ring-2 ring-sidebar-primary/20">
              {initials}
            </div>
            <div className={`min-w-0 ${compactLabelClass}`}>
              <p className="truncate text-[14px] font-bold text-white">{user.nome}</p>
              <p className="truncate text-xs text-sidebar-foreground/65">{formatProfile(user.perfil)}</p>
            </div>
          </Link>
          <button
            type="button"
            aria-label="Sair"
            title="Sair"
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
