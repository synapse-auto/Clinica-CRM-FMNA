import { Search, UserPlus, Filter } from 'lucide-react';

const CHATS = [
  { id: 1, name: 'Maria Fernanda Santos', time: '09:47', msg: 'Oi, gostaria de agendar uma consult...', tags: ['Consulta Pré-natal', 'A'], init: 'MF', color: 'bg-teal-500', active: true },
  { id: 2, name: 'Juliana Costa Rodrigues', time: '14:32', msg: 'Ótimo! Vou estar lá na sexta com cer...', tags: ['Consulta Pré-natal', 'A'], init: 'JC', color: 'bg-indigo-300' },
  { id: 3, name: 'Camila Rodrigues Oliveira', time: '16:10', msg: 'Combinado! Até amanhã então 🌸', tags: ['Consulta Pré-natal', 'F'], init: 'CR', color: 'bg-teal-500' },
  { id: 4, name: 'Beatriz Lima Ferreira', time: '10:05', msg: 'Estou na clínica agora! Obrigada pel...', tags: ['Consulta Pré-natal', 'M'], init: 'BL', color: 'bg-sky-400' },
  { id: 5, name: 'Sandra Pereira Gomes', time: '11:20', msg: 'Pode confirmar o retorno para dia 2...', tags: ['Retorno', 'P'], init: 'SP', color: 'bg-teal-300' },
  { id: 6, name: 'Fernanda Oliveira Costa', time: 'Ontem', msg: 'Estou muito ansiosa mas confiante n...', tags: ['Cirurgia', 'R', '2'], init: 'FO', color: 'bg-teal-600' },
];

export function ChatList() {
  return (
    <div className="w-[340px] flex flex-col border-r border-slate-200 h-full shrink-0">
      {/* Header */}
      <div className="p-4 border-b border-slate-100 space-y-4">
        <div className="flex justify-between items-center">
          <h2 className="text-xl font-bold text-slate-800">Atendimentos</h2>
          <div className="flex gap-2 text-slate-400">
            <button className="hover:text-slate-600 transition-colors"><UserPlus className="w-5 h-5" /></button>
            <button className="hover:text-slate-600 transition-colors"><Filter className="w-5 h-5" /></button>
          </div>
        </div>

        {/* Search */}
        <div className="relative">
          <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
          <input 
            type="text" 
            placeholder="Buscar lead..." 
            className="w-full pl-9 pr-4 py-2 bg-slate-50 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-teal-500/20 focus:border-teal-500 transition-all"
          />
        </div>

        {/* Filters */}
        <div className="flex gap-2 text-xs font-medium overflow-x-auto pb-1 hide-scrollbar">
          <button className="px-3 py-1 bg-teal-600 text-white rounded-full whitespace-nowrap">
            Todos <span className="opacity-80 ml-1">27</span>
          </button>
          <button className="px-3 py-1 bg-slate-100 text-slate-600 rounded-full whitespace-nowrap hover:bg-slate-200">
            IA <span className="text-slate-400 ml-1">19</span>
          </button>
          <button className="px-3 py-1 bg-slate-100 text-slate-600 rounded-full whitespace-nowrap hover:bg-slate-200">
            Humano <span className="text-slate-400 ml-1">8</span>
          </button>
          <button className="px-3 py-1 bg-slate-100 text-slate-600 rounded-full whitespace-nowrap hover:bg-slate-200">
            Follow UP
          </button>
        </div>
      </div>

      {/* List */}
      <div className="flex-1 overflow-y-auto custom-scrollbar">
        {CHATS.map(chat => (
          <div 
            key={chat.id} 
            className={`p-4 border-b border-slate-50 cursor-pointer transition-colors ${chat.active ? 'bg-slate-50 relative' : 'hover:bg-slate-50/50'}`}
          >
            {chat.active && <div className="absolute left-0 top-0 bottom-0 w-1 bg-teal-500" />}
            <div className="flex gap-3">
              <div className="relative shrink-0">
                <div className={`w-12 h-12 rounded-full ${chat.color} flex items-center justify-center text-white font-bold`}>
                  {chat.init}
                </div>
                <div className="absolute bottom-0 right-0 w-3.5 h-3.5 bg-green-500 border-2 border-white rounded-full"></div>
              </div>
              <div className="flex-1 min-w-0">
                <div className="flex justify-between items-baseline mb-1">
                  <h3 className="text-sm font-bold text-slate-800 truncate">{chat.name}</h3>
                  <span className="text-[10px] text-slate-400 shrink-0 ml-2">{chat.time}</span>
                </div>
                <p className="text-sm text-slate-500 truncate mb-2">{chat.msg}</p>
                <div className="flex gap-1">
                  {chat.tags.map((tag, i) => (
                    tag.length > 1 ? 
                      <span key={i} className="text-[10px] bg-blue-50 text-blue-600 px-2 py-0.5 rounded-full font-medium">{tag}</span> :
                      <span key={i} className={`w-5 h-5 flex items-center justify-center rounded-full text-[10px] font-bold text-white ${tag === 'A' ? 'bg-amber-500' : tag === 'F' ? 'bg-pink-500' : tag === 'M' ? 'bg-blue-500' : tag === 'P' ? 'bg-orange-500' : 'bg-teal-600'}`}>{tag}</span>
                  ))}
                </div>
              </div>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
