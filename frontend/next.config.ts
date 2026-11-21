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
    // Next 16 부터 필수다. 없으면 /_next/image 가 전부 400 을 돌려줘서
    // next/image 를 지나는 래스터 이미지(푸터 에스크로 마크가 처음이었다)가
    // 통째로 깨진다. 75 는 next/image 의 기본 품질값이다.
    qualities: [75],
  },

  // 서버 정보 노출 최소화
  poweredByHeader: false,

  /**
   * 관리자 API 프록시 — 쇼핑몰 주소(/api/admin/…)로 받아서 같은 서버의 백엔드로 직결한다.
   *
   * <p>관리자 화면은 지금까지 api.hanseongge.com 을 불렀다. 도메인이 다르면 브라우저가
   * 요청마다 사전확인(OPTIONS)을 먼저 보내 <b>요청 수가 2배</b>가 되고, 그 길목의
   * nginx 속도 제한에 걸린 응답에는 CORS 헤더가 없어 브라우저가 전부 「CORS 차단」으로
   * 보여 준다 — 화면 꾸미기의 「불러오지 못했습니다」(2026-11-22). 같은 주소로 받으면
   * CORS 자체가 없고, 백엔드로는 루프백으로 바로 가므로 제한도 지나지 않는다.
   *
   * <p><b>로그인만은 프록시하지 않는다.</b> nginx 의 로그인 전용 속도 제한(분당 5회)이
   * 비밀번호 무차별 대입을 막는 울타리인데, 이 프록시가 그 우회로가 되면 안 된다.
   * 첫 규칙이 로그인을 없는 경로로 보내 404 로 끊는다 — 규칙은 위에서부터 먼저
   * 맞는 것이 이긴다. 화면의 로그인 호출은 지금처럼 공개 주소로 나간다.
   *
   * <p>목적지 포트는 배포 구성(systemd)과 한 쌍이다 — 운영 8080, QA 8180.
   * QA 는 basePath(/qa) 때문에 규칙이 /qa/api/admin/… 에 걸리는데, 여기서 포트를
   * 가르지 않으면 QA 화면이 운영 백엔드를 보게 된다.
   */
  async rewrites() {
    const backend =
      process.env.API_INTERNAL_URL ??
      (basePath === '/qa' ? 'http://127.0.0.1:8180' : 'http://127.0.0.1:8080');
    return [
      { source: '/api/admin/auth/login', destination: '/api/_login-proxied-anywhere-404' },
      { source: '/api/admin/:path*', destination: `${backend}/api/admin/:path*` },
    ];
  },

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
        /*
         * 배너 미리보기 한 화면만 예외다.
         *
         * DENY 는 <b>우리 화면이 우리 화면을 끼우는 것까지</b> 막는다. 관리자 배너
         * 화면의 「미리보기」가 /banner-preview 를 iframe 에 띄우는데, DENY 로는
         * 「연결을 거부했습니다」만 뜬다 (2026-11-10 확인).
         *
         * iframe 이어야 하는 이유는 PC/휴대폰 배너가 갈리는 기준이 창 너비 미디어
         * 쿼리라서다 — 모달 안에 그대로 그리면 PC 관리자 화면에서 휴대폰 쪽을
         * 확인할 방법이 없다.
         *
         * <b>위 규칙보다 뒤에 와야 한다.</b> 같은 키를 두 규칙이 내보내면 Next 는
         * 나중 것으로 덮는다 — 순서를 바꾸면 DENY 가 이겨서 다시 거부된다.
         * 막으려는 것은 남의 사이트가 우리 화면을 끼우는 클릭재킹이고 SAMEORIGIN 이
         * 그것을 그대로 막는다. 나머지 화면은 DENY 그대로다.
         */
        source: '/banner-preview',
        headers: [{ key: 'X-Frame-Options', value: 'SAMEORIGIN' }],
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
