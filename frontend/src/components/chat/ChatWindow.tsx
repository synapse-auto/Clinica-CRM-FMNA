import { Phone, MoreVertical, Paperclip, Smile, Send, Check } from 'lucide-react';

export function ChatWindow() {
  return (
    <div className="flex-1 flex flex-col bg-[#f8fafc] min-w-0">
      {/* Header */}
      <div className="h-16 border-b border-slate-200 bg-white flex items-center justify-between px-6 shrink-0 shadow-sm z-10">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-full bg-teal-500 flex items-center justify-center text-white font-bold text-sm">
            MF
          </div>
          <div>
            <h2 className="font-bold text-slate-800 text-base leading-tight">Maria Fernanda Santos</h2>
            <p className="text-xs text-slate-500">
              44 9 9801-2345 · Atendido por <span className="text-orange-500 font-medium">Ana Lima</span>
            </p>
          </div>
        </div>
        <div className="flex items-center gap-4 text-slate-400">
          <button className="hover:text-slate-600 transition-colors"><Phone className="w-5 h-5" /></button>
          <button className="hover:text-slate-600 transition-colors"><MoreVertical className="w-5 h-5" /></button>
        </div>
      </div>

      {/* Messages Area */}
      <div className="flex-1 overflow-y-auto p-6 space-y-6 custom-scrollbar">
        
        {/* Received Message */}
        <div className="flex flex-col items-start max-w-[80%]">
          <div className="bg-white border border-slate-200 text-slate-700 px-4 py-3 rounded-2xl rounded-tl-sm shadow-sm">
            Oi, gostaria de agendar uma consulta de pré-natal
          </div>
          <span className="text-[10px] text-slate-400 mt-1 ml-1">09:44</span>
        </div>

        {/* System Event */}
        <div className="flex justify-center">
          <div className="bg-slate-200/50 text-slate-500 text-xs px-3 py-1 rounded-full font-medium">
            Atendimento recebido — Ana Lima (Recepção)
          </div>
        </div>

        {/* Sent Message (IA) */}
        <div className="flex flex-col items-end self-end max-w-[80%] ml-auto">
          <span className="text-[10px] text-slate-400 mb-1 mr-1">IA — Mari</span>
          <div className="bg-teal-700 text-white px-4 py-3 rounded-2xl rounded-tr-sm shadow-sm">
            <p className="mb-3">Olá, Maria Fernanda! 🥰 Fico feliz em ajudar com o agendamento de pré-natal.</p>
            <p>Poderia me informar seu nome completo e se possui plano de saúde ou pagará particular?</p>
          </div>
          <div className="flex items-center gap-1 mt-1 mr-1">
            <span className="text-[10px] text-slate-400">09:45</span>
            <Check className="w-3 h-3 text-slate-400" />
          </div>
        </div>
      </div>

      {/* Input Area */}
      <div className="p-4 bg-white border-t border-slate-200 shrink-0">
        <div className="flex items-center gap-3">
          <button className="text-slate-400 hover:text-slate-600 transition-colors shrink-0">
            <Paperclip className="w-5 h-5 transform -rotate-45" />
          </button>
          <div className="flex-1 relative">
            <input 
              type="text" 
              placeholder="Digite uma mensagem..." 
              className="w-full bg-slate-50 border border-slate-200 rounded-full px-5 py-3 pr-12 text-sm focus:outline-none focus:ring-2 focus:ring-teal-500/20 focus:border-teal-500 transition-all"
            />
            <button className="absolute right-4 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600 transition-colors">
              <Smile className="w-5 h-5" />
            </button>
          </div>
          <button className="w-12 h-12 bg-teal-600 rounded-full flex items-center justify-center text-white hover:bg-teal-700 transition-colors shadow-sm shrink-0">
            <Send className="w-5 h-5 ml-1" />
          </button>
        </div>
      </div>
    </div>
  );
}
