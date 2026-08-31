'use client';

import { useCartCount } from '@/hooks/useCart';
import styles from './ShopLayout.module.css';

/**
 * 헤더 장바구니 개수.
 *
 * <p>비로그인 상태에서는 0 이다 — 장바구니는 서버에만 있고 회원당 하나다. 그래서
 * 조회 자체가 나가지 않는다(훅이 enabled 로 막는다).
 *
 * <p>헤더에서 이 숫자만 클라이언트 컴포넌트로 떼어낸 이유는 카테고리 링크를 서버
 * HTML 에 남겨 두기 위해서다 — 헤더를 통째로 'use client' 로 만들면 검색엔진이
 * 사이트 구조를 읽지 못한다.
 */
export function HeaderCartBadge() {
  const { data } = useCartCount();
  const count = data?.count ?? 0;

  // 0 은 그리지 않는다. 빈 장바구니에 붙은 배지는 알려주는 것이 없으면서
  // 뭔가 담긴 것처럼 시선을 끈다.
  if (count === 0) return null;

  return (
    <span className={styles.cartBadge} aria-label={`장바구니 ${count}건`}>
      {count}
    </span>
  );
}
