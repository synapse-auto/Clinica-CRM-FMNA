import { Phone, Mail, User, Clock, Calendar, StickyNote, Bell, ChevronDown } from 'lucide-react';

export function ContactDetails() {
  return (
    <div className="w-[320px] bg-white border-l border-slate-200 h-full flex flex-col shrink-0 overflow-y-auto custom-scrollbar">
      {/* Profile Header */}
      <div className="p-6 flex flex-col items-center border-b border-slate-100">
        <div className="w-20 h-20 rounded-full bg-teal-500 flex items-center justify-center text-white font-bold text-2xl mb-4 shadow-sm ring-4 ring-teal-50">
          MF
        </div>
        <h2 className="font-bold text-slate-800 text-lg text-center leading-tight mb-2">Maria Fernanda Santos</h2>
        <span className="bg-blue-100 text-blue-700 text-xs font-bold px-3 py-1 rounded-full flex items-center gap-1.5">
          <div className="w-1.5 h-1.5 rounded-full bg-blue-600"></div>
          Agendado
        </span>
      </div>

      <div className="p-6 space-y-8">
        {/* Contato Section */}
        <section>
          <h3 className="text-[11px] font-bold text-slate-400 uppercase tracking-wider mb-4">Contato</h3>
          <div className="space-y-3">
            <div className="flex items-center gap-3 text-sm text-slate-700">
              <Phone className="w-4 h-4 text-slate-400 shrink-0" />
              <span>44 9 9801-2345</span>
            </div>
            <div className="flex items-center gap-3 text-sm text-slate-700">
              <Mail className="w-4 h-4 text-slate-400 shrink-0" />
              <span className="truncate">mariafernanda@gmail.com</span>
            </div>
            <div className="flex items-center gap-3 text-sm text-orange-500 font-medium">
              <User className="w-4 h-4 shrink-0" />
              <span>Ana Lima</span>
            </div>
            <div className="flex items-center gap-3 text-sm text-slate-700">
              <Clock className="w-4 h-4 text-slate-400 shrink-0" />
              <span>A definir</span>
            </div>
          </div>
        </section>

        {/* Tags Section */}
        <section>
          <h3 className="text-[11px] font-bold text-slate-400 uppercase tracking-wider mb-4">Tags</h3>
          <div className="flex flex-wrap gap-2">
            <span className="bg-blue-50 text-blue-600 text-xs font-medium px-2.5 py-1.5 rounded-md flex items-center gap-1.5 border border-blue-100">
              <TagIcon /> Consulta Pré-natal
            </span>
            <span className="bg-purple-50 text-purple-600 text-xs font-medium px-2.5 py-1.5 rounded-md flex items-center gap-1.5 border border-purple-100">
              <TagIcon /> Novo Paciente
            </span>
          </div>
        </section>

        {/* Histórico Section */}
        <section>
          <h3 className="text-[11px] font-bold text-slate-400 uppercase tracking-wider mb-4">Histórico</h3>
          <div className="grid grid-cols-2 gap-3">
            <div className="bg-white border border-slate-200 rounded-xl p-3 flex flex-col items-center justify-center shadow-sm">
              <Calendar className="w-4 h-4 text-slate-400 mb-1" />
              <span className="text-xl font-bold text-slate-800">0</span>
              <span className="text-[10px] text-slate-500">Consultas</span>
            </div>
            <div className="bg-white border border-slate-200 rounded-xl p-3 flex flex-col items-center justify-center shadow-sm">
              <div className="w-4 h-4 text-orange-400 mb-1 flex items-center justify-center">★</div>
              <span className="text-xl font-bold text-slate-800">R$0</span>
              <span className="text-[10px] text-slate-500">Total pago</span>
            </div>
          </div>
        </section>

        {/* Notas Section */}
        <section>
          <h3 className="text-[11px] font-bold text-slate-400 uppercase tracking-wider mb-4 flex items-center gap-2">
            Notas
          </h3>
          <div className="flex gap-3 text-sm text-slate-700">
            <StickyNote className="w-4 h-4 text-slate-400 shrink-0 mt-0.5" />
            <p className="leading-relaxed text-sm">Nova paciente, chegou via Instagram.</p>
          </div>
        </section>

        {/* Lembretes Section */}
        <section>
          <h3 className="text-[11px] font-bold text-orange-500 uppercase tracking-wider mb-4 flex items-center gap-2">
            <Bell className="w-3 h-3" /> Lembretes
          </h3>
          <div className="bg-orange-50/50 border border-orange-200/60 rounded-xl p-4 space-y-3">
            <div className="flex gap-2">
              <div className="flex-1 bg-white border border-slate-200 rounded-lg px-3 py-2 flex items-center justify-between text-sm text-slate-600 cursor-pointer shadow-sm">
                Data <ChevronDown className="w-4 h-4 text-slate-400" />
              </div>
              <div className="w-24 bg-white border border-slate-200 rounded-lg px-3 py-2 flex items-center justify-between text-sm text-slate-600 cursor-pointer shadow-sm">
                10:00 <Clock className="w-3 h-3 text-slate-400" />
              </div>
            </div>
            <textarea 
              placeholder="Mensagem do lembrete..."
              className="w-full bg-white border border-slate-200 rounded-lg p-3 text-sm resize-none h-20 focus:outline-none focus:ring-2 focus:ring-orange-500/20 focus:border-orange-500 shadow-sm transition-all"
            ></textarea>
            <button className="w-full bg-orange-300 hover:bg-orange-400 text-orange-900 font-bold text-sm py-2.5 rounded-lg transition-colors shadow-sm">
              + Adicionar lembrete
            </button>
          </div>
        </section>
      </div>
    </div>
  );
}

function TagIcon() {
  return (
    <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
      <path d="M20.59 13.41l-7.17 7.17a2 2 0 0 1-2.83 0L2 12V2h10l8.59 8.59a2 2 0 0 1 0 2.82z"></path>
      <line x1="7" y1="7" x2="7.01" y2="7"></line>
    </svg>
  );
}
