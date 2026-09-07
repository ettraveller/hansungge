import type { Metadata } from 'next';

import { AdminMembersView } from '@/views/admin/AdminMembersView';

export const metadata: Metadata = {
  title: '회원 관리',
  robots: { index: false, follow: false },
};

/** 회원 관리 (`/admin/members`). 가드와 데이터 로드는 화면 안에 있다. */
export default function AdminMembersPage() {
  return <AdminMembersView />;
}
