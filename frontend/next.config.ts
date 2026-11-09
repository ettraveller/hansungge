import type { NextConfig } from 'next';

/**
 * 주소 앞에 붙일 경로. 운영은 비어 있고, QA 는 `/qa` 다.
 *
 * <p>Next 가 모든 링크·정적 자산·라우트에 이 값을 붙여 준다. 그래서 화면 코드에는
 * `/qa` 가 한 글자도 들어가지 않는다 — 들어가면 운영에서도 붙어 버린다.
 *
 * <p><b>빌드 시점에 박힌다.</b> 서버 환경변수로는 못 바꾼다. QA 와 운영은 같은 소스를
 * 다른 값으로 두 번 빌드하는 것이지, 하나를 두 곳에 올리는 것이 아니다.
 */
const basePath = process.env.NEXT_PUBLIC_BASE_PATH ?? '';

const nextConfig: NextConfig = {
  reactStrictMode: true,

  ...(basePath ? { basePath } : {}),

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
