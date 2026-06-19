import type { Metadata } from 'next';
import { Geist, Geist_Mono } from 'next/font/google';
import './globals.css';

import { AppShell } from '@/components/demo/AppShell';
import { ThemeProvider } from '@/components/theme/ThemeProvider';
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

const themeInitializationScript = `
  (() => {
    try {
      const storedTheme = localStorage.getItem('clinica-crm-theme');
      const systemTheme = window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
      const theme = storedTheme === 'dark' || storedTheme === 'light' ? storedTheme : systemTheme;
      document.documentElement.classList.toggle('dark', theme === 'dark');
      document.documentElement.dataset.theme = theme;
      document.documentElement.style.colorScheme = theme;
    } catch (_) {
      document.documentElement.dataset.theme = 'light';
    }
  })();
`;

export default async function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  const clinica = await getClinicaAtual();

  return (
    <html lang="pt-BR" suppressHydrationWarning>
      <head>
        <script dangerouslySetInnerHTML={{ __html: themeInitializationScript }} />
      </head>
      <body
        className={`${geistSans.className} ${geistSans.variable} ${geistMono.variable} bg-background text-foreground antialiased`}
      >
        <ThemeProvider>
          <AppShell clinic={clinica}>{children}</AppShell>
        </ThemeProvider>
      </body>
    </html>
  );
}
