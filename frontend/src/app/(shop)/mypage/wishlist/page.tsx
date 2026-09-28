import type { Metadata } from 'next';

import { WishlistView } from '@/views/InterestViews';

export const metadata: Metadata = {
  title: '찜한 상품',
  robots: { index: false, follow: false },
};

export default function WishlistPage() {
  return <WishlistView />;
}
