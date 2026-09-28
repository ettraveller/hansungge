import type { Metadata } from 'next';

import { RecentlyViewedView } from '@/views/InterestViews';

export const metadata: Metadata = {
  title: '최근 본 상품',
  robots: { index: false, follow: false },
};

export default function RecentlyViewedPage() {
  return <RecentlyViewedView />;
}
