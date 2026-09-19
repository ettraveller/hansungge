import type { Metadata } from 'next';

import { PasswordResetView } from '@/views/auth/PasswordResetView';

export const metadata: Metadata = {
  title: '비밀번호 찾기',
  robots: { index: false, follow: true },
};

export default function PasswordResetPage() {
  return <PasswordResetView />;
}
