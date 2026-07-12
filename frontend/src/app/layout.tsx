import type { Metadata } from 'next';
import { Geist, Geist_Mono } from 'next/font/google';
import './globals.css';

import { ThemeProvider } from '@/components/theme/ThemeProvider';
import { publicDocumentTitle } from '@/config/public-branding';

const geistSans = Geist({
  variable: '--font-geist-sans',
  subsets: ['latin'],
});

const geistMono = Geist_Mono({
  variable: '--font-geist-mono',
  subsets: ['latin'],
});

export const metadata: Metadata = {
  title: {
    default: publicDocumentTitle,
    template: `%s | ${publicDocumentTitle}`,
  },
  description: `Sistema de atendimento e gestão clínica${publicDocumentTitle === 'CRM de Atendimento Clínico' ? '' : ` da ${publicDocumentTitle.replace(/^CRM /, '')}`}`,
};

const themeInitializationScript = `
  (() => {
    try {
      const storedTheme = localStorage.getItem('clinica-crm-theme');
      const theme = storedTheme === 'dark' || storedTheme === 'light' ? storedTheme : 'light';
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
  return (
    <html lang="pt-BR" suppressHydrationWarning>
      <head>
        <script dangerouslySetInnerHTML={{ __html: themeInitializationScript }} />
      </head>
      <body
        className={`${geistSans.className} ${geistSans.variable} ${geistMono.variable} bg-background text-foreground antialiased`}
      >
        <ThemeProvider>
          {children}
        </ThemeProvider>
      </body>
    </html>
  );
}
