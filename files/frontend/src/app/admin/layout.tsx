import type { Metadata } from 'next';
import { AdminLayout } from '@/layouts/AdminLayout';

/**
 * 관리자 영역은 검색엔진에 절대 노출되지 않는다 (설계서 §7, §15).
 * 이 metadata 는 /admin 이하 전체 경로에 상속된다.
 */
export const metadata: Metadata = {
  title: '관리자',
  robots: { index: false, follow: false, nocache: true },
};

export default function AdminSegmentLayout({ children }: { children: React.ReactNode }) {
  return <AdminLayout>{children}</AdminLayout>;
}
