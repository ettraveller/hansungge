import type { Metadata } from 'next';
import Link from 'next/link';

import { SectionHead } from '@/components/ui/SectionHead';

export const metadata: Metadata = {
  title: '이용약관',
  // 준비 중인 문서를 검색 결과에 올리지 않는다.
  robots: { index: false, follow: false },
};

/**
 * 이용약관 (`/terms`).
 *
 * <h2>왜 빈 안내인가</h2>
 * 푸터가 이 주소를 링크하고 있어 <b>운영 사이트에서 404 가 나고 있었다.</b> 깨진 링크를
 * 그대로 두는 것보다는 준비 중임을 밝히는 편이 낫다.
 *
 * <p>내용을 지어내지 않는 이유 — 이용약관은 거래 조건·청약철회·반품 규정처럼
 * <b>사업 정책이 먼저 정해져야</b> 쓸 수 있는 문서다. 개인정보처리방침이 "우리가
 * 무엇을 하는지"의 서술이라 코드에서 사실을 뽑아 쓸 수 있었던 것과 다르다.
 * 정해지지 않은 것을 적어 두면 그대로 효력을 갖는 약속이 된다 (TODO A-6).
 */
export default function TermsPage() {
  return (
    <div style={{ padding: '32px 0 64px' }}>
      <div className="wrap">
        <SectionHead title="이용약관" />
        <p style={{ maxWidth: '78ch', fontSize: 15, lineHeight: 1.85, color: '#333' }}>
          이용약관을 준비하고 있습니다. 거래 조건과 청약철회·반품 규정을 확정한 뒤
          공개할 예정입니다.
        </p>
        <p style={{ maxWidth: '78ch', fontSize: 15, lineHeight: 1.85, color: '#333' }}>
          개인정보의 수집·이용에 관한 사항은{' '}
          <Link href="/privacy">개인정보처리방침</Link>에서 확인하실 수 있습니다.
        </p>
      </div>
    </div>
  );
}
