import type { Metadata } from 'next';

import { RequireMember } from '@/components/auth/RequireMember';
import { PageHead } from '@/components/ui/PageHead';
import { CheckoutView } from '@/views/CheckoutView';

export const metadata: Metadata = {
  title: '주문서 작성',
  robots: { index: false, follow: false },
};

/**
 * 바깥 여백은 <b>여기</b>서 준다.
 *
 * <p>다른 화면은 뷰 안에서 {@code .wrap} 을 쓴다. 주문서·결제 계열만 페이지에서 감싸는
 * 이유는 그 뷰들이 상황마다 다른 것을 돌려주기 때문이다 — 불러오는 중, 주문할 것이 없음,
 * 결제할 수 없는 상태, 본문. 뷰 안에서 감싸면 그중 하나를 빠뜨리기 쉽고, 빠진 분기는
 * 그 상황이 실제로 날 때까지 아무도 모른다. 실제로 네 화면 모두 여백이 없어
 * 1920px 화면에서 입력칸이 끝에서 끝까지 늘어나 있었다.
 */
export default function CheckoutPage() {
  return (
    <div className="wrap">
      {/* 금액 확정 안내는 오른쪽 주문 요약이 이미 한다 — 한 화면에서 같은 말을 두 번 하지 않는다. */}
      <PageHead title="주문서 작성" lead="받으실 분과 주소를 확인해 주세요." />
      <RequireMember><CheckoutView /></RequireMember>
    </div>
  );
}
