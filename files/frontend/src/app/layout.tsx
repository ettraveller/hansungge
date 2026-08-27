import type { Metadata } from 'next';
import { Noto_Sans_KR } from 'next/font/google';
import { QueryProvider } from '@/app/providers';
import '@/styles/globals.css';

const notoSansKr = Noto_Sans_KR({
  subsets: ['latin'],
  weight: ['300', '400', '500', '700'],
  variable: '--font-noto-sans-kr',
  display: 'swap',
});

export const metadata: Metadata = {
  metadataBase: new URL(process.env.NEXT_PUBLIC_SITE_URL ?? 'http://localhost:3000'),
  title: {
    default: '한성금거래소',
    template: '%s | 한성금거래소',
  },
  description: '금·은·귀금속 온라인 쇼핑몰. 실시간 시세 기반 골드바·주얼리 판매.',
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
    <html lang="ko" className={notoSansKr.variable}>
      <body>
        <QueryProvider>{children}</QueryProvider>
      </body>
    </html>
  );
}
