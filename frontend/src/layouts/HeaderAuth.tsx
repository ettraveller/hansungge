'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';

import { useSignOut, useUserSession } from '@/hooks/useUserSession';
import styles from './ShopLayout.module.css';

/**
 * 상단 유틸 바의 로그인 영역.
 *
 * <p>헤더 전체를 클라이언트 컴포넌트로 만들지 않고 이 조각만 떼어냈다. 헤더에는
 * 카테고리 링크가 들어 있는데, 통째로 {@code 'use client'} 로 만들면 그 링크가
 * <b>서버 HTML 에서 빠져</b> 검색엔진이 사이트 구조를 읽지 못한다.
 *
 * <p>세션 복구가 끝나기 전(restored=false)에는 로그인 링크를 그대로 둔다. 서버가 그린
 * HTML 과 같은 모양이라 하이드레이션 불일치가 없고, 잠깐 "로그인"이 보였다가 이름으로
 * 바뀌는 편이 그 반대보다 낫다.
 */
export function HeaderAuth() {
  const router = useRouter();
  const { user, isAuthenticated } = useUserSession();
  const signOut = useSignOut();

  if (!isAuthenticated) {
    return (
      <>
        <Link href="/login">로그인</Link>
        <Link href="/signup/email">회원가입</Link>
      </>
    );
  }

  return (
    <>
      <Link href="/mypage">
        <strong>{user?.name ?? '고객'}</strong>님
      </Link>
      <button
        type="button"
        className={styles.linkButton}
        disabled={signOut.isPending}
        onClick={() => signOut.mutate(undefined, { onSuccess: () => router.replace('/') })}
      >
        {signOut.isPending ? '로그아웃 중…' : '로그아웃'}
      </button>
    </>
  );
}
