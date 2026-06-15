import type { Config } from "tailwindcss";

const config = {
  darkMode: "class",
  content: [
    "./src/app/**/*.{ts,tsx,mdx}",
    "./src/components/**/*.{ts,tsx,mdx}",
    "./src/lib/**/*.{ts,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        clinic: {
          shell: "#001114",
          "shell-active": "#003d42",
          canvas: "#effafa",
          card: "#ffffff",
          border: "#c9dde1",
          primary: "#008b8f",
          "primary-strong": "#00767a",
          cyan: "#12b8d8",
          blue: "#2563eb",
          indigo: "#5b5cf6",
          orange: "#f59e0b",
          text: "#06141b",
          muted: "#47636b",
        },
      },
      fontFamily: {
        sans: ["var(--font-geist-sans)", "Inter", "ui-sans-serif", "system-ui", "sans-serif"],
        mono: ["var(--font-geist-mono)", "ui-monospace", "SFMono-Regular", "monospace"],
      },
      borderRadius: {
        clinic: "0.875rem",
        "clinic-lg": "1rem",
      },
      boxShadow: {
        clinic: "0 1px 2px rgb(6 20 27 / 0.05)",
        "clinic-raised": "0 10px 30px rgb(6 20 27 / 0.08)",
      },
    },
  },
} satisfies Config;

export default config;
