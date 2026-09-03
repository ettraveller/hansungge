import type { Metadata } from 'next';

import { CartView } from '@/views/CartView';

export const metadata: Metadata = {
  title: '장바구니',
  // 개인 화면이라 색인하지 않는다.
  robots: { index: false, follow: false },
};

export default function CartPage() {
  return <CartView />;
}
