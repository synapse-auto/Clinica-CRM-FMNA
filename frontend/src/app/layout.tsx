import type { Metadata } from "next";
import { Geist, Geist_Mono } from "next/font/google";
import "./globals.css";

import { Sidebar } from "@/components/layout/Sidebar";
import { getClinicaAtual } from "@/services/backend";

const geistSans = Geist({
  variable: "--font-geist-sans",
  subsets: ["latin"],
});

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: "Clínica Bem Estar - CRM",
  description: "Sistema de Gestão Clínica e CRM WhatsApp",
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
        className={`${geistSans.variable} ${geistMono.variable} bg-background text-foreground antialiased`}
      >
        <div className="flex h-dvh min-h-screen w-full overflow-hidden">
          <Sidebar clinicName={clinica.nome} />
          <main className="min-w-0 flex-1 overflow-hidden bg-background">{children}</main>
        </div>
      </body>
    </html>
  );
}
