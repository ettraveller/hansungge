'use client';

import { useIsWishlisted, useToggleWishlist } from '@/hooks/useInterest';
import { useUserSession } from '@/hooks/useUserSession';
import styles from '@/components/product/WishlistHeart.module.css';

/**
 * 찜하기 하트.
 *
 * <h2>비회원에게는 그리지 않는다</h2>
 * 찜은 서버에 저장한다. 로그인 전에 누르면 로그인 화면으로 보내야 하는데, 상품을 훑는
 * 중에 갑자기 로그인 화면이 나오면 고객은 하던 일을 잃는다. 차라리 <b>하트를 보여주지
 * 않는 쪽</b>이 낫다.
 *
 * <h2>누른 즉시 바뀐다</h2>
 * 서버 응답을 기다리면 연타했을 때 하트가 깜빡이고, 고객은 눌렸는지 알 수 없다.
 * 실패하면 되돌린다 ({@code useToggleWishlist}).
 *
 * <p>카드 전체가 링크인 자리에 놓이므로 <b>링크 바깥에</b> 두어야 한다 —
 * 링크 안의 버튼은 누르면 링크가 함께 열린다.
 */
export function WishlistHeart({ productId, productName }: { productId: number; productName: string }) {
  const { isAuthenticated } = useUserSession();
  const { data: wishlisted } = useIsWishlisted(isAuthenticated ? productId : null);
  const toggle = useToggleWishlist();

  if (!isAuthenticated) {
    return null;
  }

  const on = wishlisted === true;

  return (
    <button
      type="button"
      className={on ? `${styles.heart} ${styles.on}` : styles.heart}
      aria-pressed={on}
      aria-label={on ? `${productName} 찜 해제` : `${productName} 찜하기`}
      onClick={(e) => {
        // 카드 전체가 링크인 자리에서도 상세로 넘어가지 않게 한다.
        e.preventDefault();
        e.stopPropagation();
        toggle.mutate({ productId, next: !on });
      }}
    >
      <svg viewBox="0 0 24 24" aria-hidden="true">
        <path d="M12 20.4S4.5 15.7 4.5 10.6A3.9 3.9 0 0 1 12 8.3a3.9 3.9 0 0 1 7.5 2.3c0 5.1-7.5 9.8-7.5 9.8z" />
      </svg>
    </button>
  );
}
