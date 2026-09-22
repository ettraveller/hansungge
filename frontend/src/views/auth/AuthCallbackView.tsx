'use client';

import { useQuery } from '@tanstack/react-query';
import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { useEffect } from 'react';

import * as authApi from '@/api/auth';
import { Notice } from '@/components/auth/Notice';
import { useApplySignIn } from '@/hooks/useUserSession';
import { oauthMode, oauthState, pendingSignUp, safeNext } from '@/utils/authStorage';
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

/** 로그인하려던 것인가, 마이페이지에서 연결하려던 것인가. */
type CallbackOutcome = { kind: 'signin'; result: SignInResult } | { kind: 'linked' };

/**
 * 소셜 제공자가 되돌려 보낸 인가코드를 처리하는 중간 화면.
 *
 * <p>사용자에게는 잠깐 스쳐 지나가는 화면이지만, 여기서 하는 일이 인증의 핵심이다.
 * <ol>
 *   <li>URL 의 state 와 <b>이 브라우저가 보관한 state</b> 를 대조한다 — 서버 서명과
 *       독립적인 두 번째 방어선이고, 인가코드 주입(공격자 계정으로 로그인시키기)을
 *       실제로 막는 것은 이쪽이다.</li>
 *   <li>인가코드를 서버에 넘긴다. 로그인 중이었으면 로그인, 마이페이지에서 [연결하기]를
 *       눌렀으면 그 계정에 연결한다.</li>
 *   <li>처음 오는 소셜 계정이면 <b>약관 화면</b>으로 넘긴다. 약관에 동의하면 가입이 끝난다 —
 *       본인인증을 받지 않는다(2026-09-21 개편).</li>
 * </ol>
 *
 * <h2>왜 useMutation 이 아니라 useQuery 인가</h2>
 * 이 작업은 버튼을 눌러 일어나는 일이 아니라 <b>화면에 들어온 것 자체가 트리거</b>다.
 * 인가코드를 키에 넣어 두면 같은 코드로는 한 번만 나간다 — 인가코드는 일회용이라 두 번
 * 보내면 두 번째가 반드시 실패한다.
 */
export function AuthCallbackView({ provider }: { provider: OAuthProviderType }) {
  const router = useRouter();
  const searchParams = useSearchParams();
  const apply = useApplySignIn();

  const code = searchParams.get('code');
  const returnedState = searchParams.get('state');
  const denied = searchParams.get('error');

  const { data, error, isPending } = useQuery<CallbackOutcome>({
    queryKey: ['oauth-callback', provider, code, returnedState, denied],
    queryFn: async () => {
      const isLink = oauthMode.takeIsLink();

      // 사용자가 제공자 화면에서 동의하지 않은 경우
      if (denied) {
        throw new CallbackError(
          isLink ? '연결이 취소되었습니다' : '로그인이 취소되었습니다',
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
        throw new CallbackError('로그인 요청을 확인할 수 없습니다', '보안을 위해 처음부터 다시 시도해 주세요.');
      }

      if (isLink) {
        await authApi.linkSocial(provider, code, returnedState);
        return { kind: 'linked' };
      }
      return { kind: 'signin', result: await authApi.completeOAuth(provider, code, returnedState) };
    },
    // 인가코드는 일회용이다. 실패했다고 다시 보내면 무조건 또 실패한다.
    retry: false,
    staleTime: Infinity,
    refetchOnWindowFocus: false,
  });

  /**
   * 성공 후 이동. 렌더 중에는 라우터를 건드릴 수 없어 effect 에서 한다.
   */
  useEffect(() => {
    if (!data) return;

    if (data.kind === 'linked') {
      router.replace('/mypage?linked=1');
      return;
    }

    const next = safeNext(sessionStorage.getItem('hsg_after_login'));
    sessionStorage.removeItem('hsg_after_login');
    const { result } = data;

    if (result.signUpRequired) {
      // 처음 오는 소셜 계정 — 계정은 아직 없다. 약관 동의로 가입이 끝난다.
      pendingSignUp.save({
        signUpToken: result.signUpToken ?? '',
        agreements: result.agreements ?? [],
        suggestedEmail: result.suggestedEmail ?? null,
        next,
      });
      router.replace('/signup');
      return;
    }
    apply(result);
    router.replace(next);
  }, [data, router, apply]);

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
