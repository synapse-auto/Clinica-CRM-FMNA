'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import {
  MessageSquare,
  LayoutDashboard,
  Calendar,
  Users,
  UserPlus,
  Zap,
  Tag,
  MessageCircleQuestion,
  Clock,
  Settings,
  Moon,
  ChevronRight,
} from 'lucide-react';
import { Switch } from '@/components/ui/switch';

const MENU_ITEMS = [
  { name: 'Atendimentos', icon: MessageSquare, href: '/atendimentos', badge: 4 },
  { name: 'Dashboard', icon: LayoutDashboard, href: '/dashboard' },
  { name: 'Agenda', icon: Calendar, href: '/agenda' },
  { name: 'Pacientes', icon: Users, href: '/pacientes' },
  { name: 'Equipe', icon: UserPlus, href: '/equipe' },
  { name: 'Automação', icon: Zap, href: '/automacao' },
  { name: 'Tags', icon: Tag, href: '/tags' },
  { name: 'Msgs Rápidas', icon: MessageCircleQuestion, href: '/msgs-rapidas' },
  { name: 'Horários', icon: Clock, href: '/horarios' },
  { name: 'Configurações', icon: Settings, href: '/configuracoes' },
];

type SidebarProps = {
  clinicName?: string;
};

export function Sidebar({ clinicName = 'Clínica' }: SidebarProps) {
  const pathname = usePathname();

  return (
    <aside className="w-[260px] bg-slate-900 h-screen flex flex-col text-slate-300 border-r border-slate-800 shrink-0">
      {/* Logo Area */}
      <div className="h-16 flex items-center px-6 border-b border-slate-800">
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 bg-teal-900/50 rounded-lg flex items-center justify-center border border-teal-800/50">
            <span className="text-teal-400 font-bold text-xs">CRM</span>
          </div>
          <div>
            <h1 className="text-white font-bold text-sm tracking-tight leading-tight">
              {clinicName}
            </h1>
            <span className="text-slate-500 text-xs">CRM</span>
          </div>
        </div>
      </div>

      {/* Navigation */}
      <div className="flex-1 overflow-y-auto py-6 px-4 custom-scrollbar">
        <div className="text-xs font-semibold text-slate-500 mb-4 px-2 tracking-wider">
          MENU
        </div>
        <nav className="space-y-1">
          {MENU_ITEMS.map((item) => {
            const isActive = pathname === item.href || pathname.startsWith(item.href + '/');
            return (
              <Link
                key={item.name}
                href={item.href}
                className={`flex items-center justify-between px-3 py-2.5 rounded-lg text-sm font-medium transition-colors ${
                  isActive
                    ? 'bg-teal-900/40 text-white'
                    : 'text-slate-400 hover:text-white hover:bg-slate-800/50'
                }`}
              >
                <div className="flex items-center gap-3">
                  <item.icon className={`w-5 h-5 ${isActive ? 'text-teal-400' : 'text-slate-500'}`} />
                  {item.name}
                </div>
                {isActive && !item.badge && (
                  <ChevronRight className="w-4 h-4 text-teal-500" />
                )}
                {item.badge && (
                  <span className="bg-red-500 text-white text-[10px] font-bold px-1.5 py-0.5 rounded-full min-w-5 text-center">
                    {item.badge}
                  </span>
                )}
              </Link>
            );
          })}
        </nav>
      </div>

      {/* Footer Area */}
      <div className="p-4 border-t border-slate-800 space-y-2">
        <button className="flex items-center gap-3 px-3 py-2 text-sm text-slate-400 hover:text-white hover:bg-slate-800/50 w-full rounded-lg transition-colors">
          <div className="w-5 h-5 flex items-center justify-center">
            <MessageSquare className="w-4 h-4 text-teal-400" />
          </div>
          <span className="flex-1 text-left">WhatsApp IA</span>
          <span className="text-[10px] bg-teal-900/50 text-teal-400 px-2 py-0.5 rounded border border-teal-800/50 uppercase font-semibold">Demo</span>
        </button>
        <button className="flex items-center gap-3 px-3 py-2 text-sm text-slate-400 hover:text-white hover:bg-slate-800/50 w-full rounded-lg transition-colors">
          <div className="w-5 h-5 flex items-center justify-center">
            <MessageSquare className="w-4 h-4 text-teal-400" />
          </div>
          <span className="flex-1 text-left">WhatsApp I...</span>
          <span className="text-[10px] bg-teal-900/50 text-teal-400 px-2 py-0.5 rounded border border-teal-800/50 uppercase font-semibold">Demo</span>
        </button>
        <div className="flex items-center justify-between px-3 py-2 text-sm text-slate-400 w-full rounded-lg">
          <div className="flex items-center gap-3">
            <Moon className="w-5 h-5 text-slate-500" />
            Tema Escuro
          </div>
          <Switch
            label=""
            aria-label="Alternar tema escuro"
            className="min-h-5"
            switchClassName="bg-slate-700 data-[checked]:bg-teal-500 focus-visible:ring-teal-400/40"
          />
        </div>

        {/* User Profile */}
        <div className="mt-4 pt-4 border-t border-slate-800 flex items-center justify-between px-2 cursor-pointer hover:bg-slate-800/50 p-2 rounded-lg transition-colors">
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 rounded-full bg-teal-600 flex items-center justify-center text-white font-bold text-xs shrink-0">
              US
            </div>
            <div className="overflow-hidden">
              <p className="text-sm font-medium text-white truncate">Usuário conectado</p>
              <p className="text-xs text-slate-500 truncate">Equipe</p>
            </div>
          </div>
          <ChevronRight className="w-4 h-4 text-slate-500 shrink-0" />
        </div>
      </div>
    </aside>
  );
}
