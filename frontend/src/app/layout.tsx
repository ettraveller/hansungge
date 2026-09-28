import type { Metadata } from 'next';
import { Noto_Sans_KR, Noto_Serif_KR } from 'next/font/google';
import { QueryProvider } from '@/app/providers';
import '@/styles/globals.css';

const notoSansKr = Noto_Sans_KR({
  subsets: ['latin'],
  weight: ['300', '400', '500', '700'],
  variable: '--font-noto-sans-kr',
  display: 'swap',
});

/**
 * 제목과 금액에만 쓰는 명조 (2026-09-28).
 *
 * <p>굵기를 둘만 받는다 — 명조는 본문에 쓰지 않으므로 400·500 이면 충분하고,
 * 한글 폰트는 굵기 하나가 파일 수십 조각이라 더 받으면 첫 화면이 느려진다.
 */
const notoSerifKr = Noto_Serif_KR({
  subsets: ['latin'],
  weight: ['400', '500'],
  variable: '--font-noto-serif-kr',
  display: 'swap',
});

export const metadata: Metadata = {
  metadataBase: new URL(process.env.NEXT_PUBLIC_SITE_URL ?? 'http://localhost:3000'),
  title: {
    default: '한성금거래소',
    template: '%s | 한성금거래소',
  },
  description: '금·은·귀금속 온라인 쇼핑몰. 매일 고시하는 시세로 골드바·주얼리를 판매합니다.',
  openGraph: {
    type: 'website',
    siteName: '한성금거래소',
    locale: 'ko_KR',
  },
};

export const viewport = {
  width: 'device-width',
  initialScale: 1,
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="ko" className={`${notoSansKr.variable} ${notoSerifKr.variable}`}>
      <head>
        {/*
          Pretendard — 한글 본문용 가변 폰트 (OFL-1.1, node_modules/pretendard 에서 복사).

          next/font 를 쓰지 않는 이유: 이 폰트는 unicode-range 로 92조각으로 쪼개져
          있고, 브라우저가 그 페이지에 실제로 쓰인 글자가 든 조각만 내려받는다.
          한글 전체를 담은 단일 파일은 2MB 라 첫 화면을 붙잡는다. next/font/local
          은 조각 하나에 선언 하나를 요구해서 92개를 손으로 적어야 한다.

          preconnect 가 필요 없다 — 같은 도메인에서 나간다.
        */}
        {/*
          아래 규칙을 끄는 이유 — no-css-tags 는 "next/font 를 쓰라"는 규칙인데
          여기서는 쓸 수 없다. 위에 적은 대로 92조각짜리 unicode-range 선언이라
          next/font/local 로 옮기면 선언 92개를 손으로 적어야 하고, 조각을
          하나 빠뜨리면 그 글자만 대체 글꼴로 빠진다.

          (주석 첫 낱말을 규칙 이름으로 시작하면 안 된다 — 그 자체가 설정
          지시문으로 읽혀 "JSON 파싱 실패"로 끝난다.)
        */}
        {/* eslint-disable-next-line @next/next/no-css-tags */}
        <link rel="stylesheet" href="/fonts/pretendard/pretendard.css" />
      </head>
      <body>
        {/* 키보드 사용자가 헤더 메뉴를 전부 지나치지 않고 본문으로 간다 */}
        <a className="skip-link" href="#main">
          본문 바로가기
        </a>
        <QueryProvider>{children}</QueryProvider>
      </body>
    </html>
  );
}
