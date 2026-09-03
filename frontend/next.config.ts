import type { NextConfig } from 'next';

const nextConfig: NextConfig = {
  reactStrictMode: true,

  // systemd 로 배포하므로 standalone 산출물을 만든다 (설계서 §14.3).
  // 결과: .next/standalone/server.js
  output: 'standalone',

  // 상품 이미지는 NCP Object Storage 에서 온다. 도메인 확정 후 여기에 추가한다.
  images: {
    remotePatterns: [],
  },

  // 서버 정보 노출 최소화
  poweredByHeader: false,

  async headers() {
    return [
      {
        source: '/:path*',
        headers: [
          { key: 'X-Content-Type-Options', value: 'nosniff' },
          { key: 'Referrer-Policy', value: 'strict-origin-when-cross-origin' },
          { key: 'X-Frame-Options', value: 'DENY' },
        ],
      },
      {
        // 관리자 화면은 검색엔진·캐시에서 제외
        source: '/admin/:path*',
        headers: [
          { key: 'X-Robots-Tag', value: 'noindex, nofollow, noarchive' },
          { key: 'Cache-Control', value: 'no-store' },
        ],
      },
    ];
  },
};

export default nextConfig;
