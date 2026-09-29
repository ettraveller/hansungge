import type { Metadata } from 'next';

import { MyInquiryView } from '@/views/MyInquiryView';

export const metadata: Metadata = {
  title: '내 문의',
  robots: { index: false, follow: false },
};

export default function MyInquiriesPage() {
  return <MyInquiryView />;
}
