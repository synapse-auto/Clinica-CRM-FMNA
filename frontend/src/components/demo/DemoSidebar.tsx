'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import {
  BadgeHelp,
  Calendar,
  ChevronRight,
  Clock,
  LayoutDashboard,
  MessageCircle,
  MessageSquare,
  Moon,
  Settings,
  Stethoscope,
  Tag,
  UserPlus,
  Users,
  Zap,
} from 'lucide-react';
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
  const initials = clinic.nome
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join('') || 'CL';

  return (
    <aside className="flex h-screen w-[220px] shrink-0 flex-col bg-[#001114] text-[#b7cdd3]">
      <div className="flex h-[73px] items-center gap-3 border-b border-[#0b2b30] px-5">
        <div className="flex h-9 w-9 items-center justify-center rounded-lg border border-teal-700/40 bg-teal-900/40 text-teal-300">
          <Stethoscope className="h-5 w-5" />
        </div>
        <div className="min-w-0">
          <p className="truncate text-sm font-bold leading-tight text-white">{clinic.nome}</p>
          <p className="text-[11px] text-[#7fa0a8]">CRM</p>
        </div>
      </div>

      <div className="flex-1 overflow-y-auto px-3 py-5 custom-scrollbar">
        <p className="mb-4 px-2 text-[11px] font-semibold uppercase tracking-[0.18em] text-[#5e7d85]">
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
                    ? 'bg-[#003d42] text-white'
                    : 'text-[#a8c0c8] hover:bg-[#08262b] hover:text-white'
                }`}
              >
                <span className="flex min-w-0 items-center gap-3">
                  <item.icon className={`h-4.5 w-4.5 shrink-0 ${active ? 'text-[#00d0b0]' : 'text-[#7fa0a8]'}`} />
                  <span className="truncate">{item.name}</span>
                </span>
                {item.badge ? (
                  <span className="min-w-5 rounded-full bg-red-500 px-1.5 py-0.5 text-center text-[10px] font-bold text-white">
                    {item.badge}
                  </span>
                ) : active ? (
                  <ChevronRight className="h-4 w-4 text-teal-400" />
                ) : null}
              </Link>
            );
          })}
        </nav>

        <div className="mt-5 space-y-1 border-t border-[#0b2b30] pt-4">
          <SidebarUtility label="WhatsApp IA" />
          <SidebarUtility label="WhatsApp Integrado" />
        </div>
      </div>

      <div className="border-t border-[#0b2b30] p-4">
        <button className="mb-3 flex h-10 w-full items-center gap-3 rounded-lg px-3 text-left text-sm font-semibold text-[#a8c0c8] transition hover:bg-[#08262b] hover:text-white">
          <Moon className="h-4 w-4 text-[#7fa0a8]" />
          Tema Escuro
        </button>
        <div className="flex items-center justify-between rounded-lg p-2 transition hover:bg-[#08262b]">
          <div className="flex min-w-0 items-center gap-3">
            <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-teal-600 text-xs font-bold text-white ring-2 ring-teal-300/30">
              {initials}
            </div>
            <div className="min-w-0">
              <p className="truncate text-sm font-bold text-white">{clinic.nome}</p>
              <p className="truncate text-[11px] text-[#7fa0a8]">
                {clinic.tipoClinica === 'ULTRASSONOGRAFIA' ? 'Ultrassonografia' : 'Gestor'}
              </p>
            </div>
          </div>
          <ChevronRight className="h-4 w-4 shrink-0 text-[#5e7d85]" />
        </div>
      </div>
    </aside>
  );
}

function SidebarUtility({ label }: { label: string }) {
  return (
    <button className="flex h-9 w-full items-center gap-3 rounded-lg px-3 text-left text-sm font-semibold text-[#00d0b0] transition hover:bg-[#08262b]">
      <MessageCircle className="h-4 w-4" />
      <span className="min-w-0 flex-1 truncate">{label}</span>
      <span className="rounded-full bg-teal-900/70 px-2 py-0.5 text-[9px] font-bold uppercase text-teal-300">
        Demo
      </span>
    </button>
  );
}
