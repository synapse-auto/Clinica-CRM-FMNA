import { ChatList } from '@/components/chat/ChatList';
import { ChatWindow } from '@/components/chat/ChatWindow';
import { ContactDetails } from '@/components/chat/ContactDetails';

export default function AtendimentosPage() {
  return (
    <div className="flex h-full overflow-hidden bg-clinic-canvas">
      <ChatList />
      <ChatWindow />
      <ContactDetails />
    </div>
  );
}
