'use client';

import { useQuery } from '@tanstack/react-query';
import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { useEffect } from 'react';

import * as authApi from '@/api/auth';
import { Notice } from '@/components/auth/Notice';
import { useUserAuthStore } from '@/stores/useUserAuthStore';
import { useStartIdentity } from '@/hooks/useUserSession';
import { oauthState, pendingSignUp, safeNext } from '@/utils/authStorage';
import { identityReturnUrl, pendingIdentity } from '@/utils/identityReturn';
import { ApiError } from '@/types/api';
import type { OAuthProviderType, SignInResult } from '@/types/user';
import styles from '@/components/auth/auth.module.css';

/** 사용자에게 그대로 보여줄 실패 사유. 원인마다 다음에 할 일이 다르다. */
class CallbackError extends Error {
  constructor(
    readonly title: string,
    readonly detail: string,
  ) {
    super(title);
    this.name = 'CallbackError';
  }
}

/**
 * 소셜 제공자가 되돌려 보낸 인가코드를 처리하는 중간 화면.
 *
 * <p>사용자에게는 잠깐 스쳐 지나가는 화면이지만, 여기서 하는 일이 인증의 핵심이다.
 * <ol>
 *   <li>URL 의 state 와 <b>이 브라우저가 보관한 state</b> 를 대조한다 — 서버 서명과
 *       독립적인 두 번째 방어선이고, 인가코드 주입(공격자 계정으로 로그인시키기)을
 *       실제로 막는 것은 이쪽이다.</li>
 *   <li>인가코드를 서버에 넘겨 신원을 확인한다.</li>
 *   <li>기존 회원이면 로그인, 처음이면 약관 동의 화면으로 넘긴다.</li>
 * </ol>
 *
 * <h2>왜 useMutation 이 아니라 useQuery 인가</h2>
 * 이 작업은 버튼을 눌러 일어나는 일이 아니라 <b>화면에 들어온 것 자체가 트리거</b>다.
 * 쿼리는 마운트 시 스스로 실행되므로 "effect 에서 mutate() 를 부르고 ref 로 중복을
 * 막는" 구조가 통째로 필요 없다. 인가코드를 키에 넣어 두면 같은 코드로는 한 번만
 * 나간다 — 인가코드는 일회용이라 두 번 보내면 두 번째가 반드시 실패한다.
 *
 * <p>검증과 교환을 한 함수에 넣은 이유: 오류가 두 갈래(사전 검증 / 서버 응답)로
 * 나뉘면 화면에서 두 번 처리해야 한다.
 */
export function AuthCallbackView({ provider }: { provider: OAuthProviderType }) {
  const router = useRouter();
  const searchParams = useSearchParams();
  const signIn = useUserAuthStore((s) => s.signIn);
  const startIdentity = useStartIdentity();

  const code = searchParams.get('code');
  const returnedState = searchParams.get('state');
  const denied = searchParams.get('error');

  const { data, error, isPending } = useQuery<SignInResult>({
    queryKey: ['oauth-callback', provider, code, returnedState, denied],
    queryFn: async () => {
      // 사용자가 제공자 화면에서 동의하지 않은 경우
      if (denied) {
        throw new CallbackError(
          '로그인이 취소되었습니다',
          '소셜 로그인 창에서 동의하지 않으면 계정을 확인할 수 없습니다.',
        );
      }
      if (!code || !returnedState) {
        throw new CallbackError(
          '로그인 정보가 올바르지 않습니다',
          '주소를 직접 입력하셨다면 로그인 화면부터 다시 시작해 주세요.',
        );
      }

      // 한 번 쓰면 지운다. 재사용을 허용하면 대조의 의미가 없다.
      const savedState = oauthState.take(provider);
      if (!savedState || savedState !== returnedState) {
        // 이 브라우저가 시작한 요청이 아니다. 조용히 넘기면 공격자가 심어 둔
        // 인가코드로 로그인되는 경로가 열린다.
        throw new CallbackError(
          '로그인 요청을 확인할 수 없습니다',
          '보안을 위해 처음부터 다시 시도해 주세요.',
        );
      }

      return authApi.completeOAuth(provider, code, returnedState);
    },
    // 인가코드는 일회용이다. 실패했다고 다시 보내면 무조건 또 실패한다.
    retry: false,
    staleTime: Infinity,
    refetchOnWindowFocus: false,
  });

  /**
   * 성공 후 이동.
   *
   * <p>렌더 중에는 라우터를 건드릴 수 없어 effect 에서 한다. 여기서 하는 일은 화면
   * 상태 갱신이 아니라 <b>브라우저 주소 변경</b>이라 effect 가 맞는 자리다.
   */
  useEffect(() => {
    if (!data) return;

    const next = safeNext(sessionStorage.getItem('hsg_after_login'));
    sessionStorage.removeItem('hsg_after_login');

    // 처음 오는 소셜 계정이면 <b>본인확인이 먼저다.</b> 소셜만 면제하면 "본인확인을
    // 안 한 회원"이 생기고, 그 예외가 주문·매입 코드 전체로 번진다.
    //
    // 약관을 여기서 받지 않는 이유 — 본인확인을 마쳐 봐야 이미 가입한 사람인지 알 수
    // 있다. 이미 회원이면 서버가 그 자리에서 소셜만 붙이고 들여보낸다.
    if (data.identityRequired) {
      pendingIdentity.save({
        signUpToken: data.signUpToken ?? '',
        origin: 'social',
        next,
      });
      startIdentity.mutate(
        { signUpToken: data.signUpToken ?? '', returnUrl: identityReturnUrl() },
        { onSuccess: (res) => window.location.assign(res.startUrl) },
      );
      return;
    }

    if (data.signUpRequired) {
      // 본인확인까지 끝났고 약관만 남았다.
      pendingSignUp.save({
        signUpToken: data.signUpToken ?? '',
        agreements: data.agreements ?? [],
        suggestedName: data.suggestedName ?? null,
        suggestedEmail: data.suggestedEmail ?? null,
        next,
      });
      router.replace('/signup');
      return;
    }
    if (data.accessToken && data.user) {
      signIn({ accessToken: data.accessToken, user: data.user });
    }
    router.replace(next);
  }, [data, router, signIn, startIdentity]);

  if (error) {
    const title = error instanceof CallbackError ? error.title : '로그인에 실패했습니다';
    const detail =
      error instanceof CallbackError
        ? error.detail
        : error instanceof ApiError
          ? error.message
          : '잠시 후 다시 시도해 주세요.';

    return (
      <div className={styles.page}>
        <header className={styles.head}>
          <h1 className={styles.title}>로그인</h1>
        </header>
        <div className={styles.card}>
          <Notice tone="error" title={title}>
            {detail}
          </Notice>
          <div style={{ marginTop: 18 }}>
            <Link
              className={styles.primary}
              href="/login"
              style={{ display: 'block', textAlign: 'center', lineHeight: '48px' }}
            >
              로그인 화면으로
            </Link>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className={styles.page}>
      <p className={styles.loading}>{isPending ? '로그인 처리 중입니다…' : '이동 중입니다…'}</p>
    </div>
  );
}
