import type { Metadata } from 'next';
import { Geist, Geist_Mono } from 'next/font/google';
import './globals.css';

import { AppShell } from '@/components/demo/AppShell';
import { getClinicaAtual } from '@/services/backend';

const geistSans = Geist({
  variable: '--font-geist-sans',
  subsets: ['latin'],
});

const geistMono = Geist_Mono({
  variable: '--font-geist-mono',
  subsets: ['latin'],
});

export const metadata: Metadata = {
  title: 'CRM Clínico',
  description: 'Sistema de gestão clínica e CRM WhatsApp',
};

export default async function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  const clinica = await getClinicaAtual();

  return (
    <html lang="pt-BR">
      <body
        className={`${geistSans.className} ${geistSans.variable} ${geistMono.variable} bg-background text-foreground antialiased`}
      >
        <AppShell clinic={clinica}>{children}</AppShell>
      </body>
    </html>
  );
}
