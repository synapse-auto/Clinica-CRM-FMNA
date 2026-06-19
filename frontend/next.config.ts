import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  eslint: {
    // O lint roda separadamente via `npm run lint`; o lint embutido do Next 15
    // ainda usa opções incompatíveis com a configuração flat do ESLint.
    ignoreDuringBuilds: true,
  },
};

export default nextConfig;
