import type { Metadata } from 'next';

import { SignUpView } from '@/views/auth/SignUpView';

export const metadata: Metadata = {
  title: '회원가입',
  robots: { index: false, follow: true },
};

export default function SignUpPage() {
  return <SignUpView />;
}
