import { Search, Filter, UserPlus, Phone, MoreVertical, Paperclip, Smile, Send } from 'lucide-react';
import { ChatList } from '@/components/chat/ChatList';
import { ChatWindow } from '@/components/chat/ChatWindow';
import { ContactDetails } from '@/components/chat/ContactDetails';

export default function AtendimentosPage() {
  return (
    <div className="flex-1 flex h-full overflow-hidden bg-white">
      {/* Coluna 1: Lista de Chats */}
      <ChatList />

      {/* Coluna 2: Janela de Chat */}
      <ChatWindow />

      {/* Coluna 3: Detalhes do Contato */}
      <ContactDetails />
    </div>
  );
}
