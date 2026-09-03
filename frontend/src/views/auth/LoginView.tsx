'use client';

import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { useEffect, useState } from 'react';

import { Notice } from '@/components/auth/Notice';
import { SocialButtons } from '@/components/auth/SocialButtons';
import { useAuthProviders, useStartOAuth, useUserSession } from '@/hooks/useUserSession';
import { oauthState, safeNext } from '@/utils/authStorage';
import { ApiError } from '@/types/api';
import type { OAuthProviderType } from '@/types/user';
import styles from '@/components/auth/auth.module.css';

/**
 * 로그인.
 *
 * <p>아이디·비밀번호 입력란이 없다. 자체 계정을 만들지 않기로 했기 때문이다
 * (개발계획 §7.1) — 보관하지 않는 비밀번호는 유출될 수도, 재사용될 수도 없다.
 *
 * <p>그래서 "로그인"과 "회원가입"이 같은 동작이다. 처음 오는 소셜 계정이면 약관 동의
 * 화면으로 이어지고, 이미 있으면 바로 들어온다.
 */
export function LoginView() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const next = safeNext(searchParams.get('next'));
  const reason = searchParams.get('reason');

  const { isAuthenticated, restored } = useUserSession();
  const { data, isLoading, error } = useAuthProviders();
  const startOAuth = useStartOAuth();
  const [pendingProvider, setPendingProvider] = useState<OAuthProviderType | null>(null);

  // 이미 로그인한 상태로 들어오면 되돌려 보낸다.
  useEffect(() => {
    if (restored && isAuthenticated) router.replace(next);
  }, [restored, isAuthenticated, next, router]);

  const select = (provider: OAuthProviderType) => {
    setPendingProvider(provider);
    startOAuth.mutate(provider, {
      onSuccess: (res) => {
        // state 를 이 브라우저에 남겨 두고 콜백에서 대조한다.
        // 서버 서명만으로는 "우리가 발급한 값"임은 알아도
        // "이 브라우저가 시작한 요청"임은 알 수 없다.
        oauthState.save(provider, res.state);
        sessionStorage.setItem('hsg_after_login', next);
        window.location.assign(res.authorizeUrl);
      },
      onError: () => setPendingProvider(null),
    });
  };

  const providers = data?.providers ?? [];
  const startError = startOAuth.error;

  return (
    <div className={styles.page}>
      <header className={styles.head}>
        <h1 className={styles.title}>로그인 · 회원가입</h1>
        <p className={styles.lead}>
          한성금거래소는 소셜 계정으로만 로그인합니다.
          <br />
          별도 비밀번호를 만들지 않으므로 비밀번호가 새어 나갈 일이 없습니다.
        </p>
      </header>

      <div className={styles.card}>
        {reason === 'required' && (
          <div style={{ marginBottom: 16 }}>
            <Notice tone="warn">로그인이 필요한 화면입니다.</Notice>
          </div>
        )}

        {startError && (
          <div style={{ marginBottom: 16 }}>
            <Notice tone="error" title="로그인을 시작하지 못했습니다">
              {startError instanceof ApiError
                ? startError.message
                : '잠시 후 다시 시도해 주세요.'}
            </Notice>
          </div>
        )}

        {isLoading ? (
          <p className={styles.loading}>불러오는 중…</p>
        ) : error ? (
          <Notice tone="error" title="로그인 수단을 불러오지 못했습니다">
            잠시 후 다시 시도해 주세요.
          </Notice>
        ) : providers.length === 0 ? (
          <Notice tone="warn" title="현재 이용할 수 있는 로그인 수단이 없습니다">
            소셜 로그인 연동을 준비 중입니다. 주문·문의는 고객센터로 도와드리겠습니다.
            <br />
            <Link href="/support">고객센터 바로가기</Link>
          </Notice>
        ) : (
          <SocialButtons
            providers={providers}
            pendingProvider={pendingProvider}
            onSelect={select}
          />
        )}

        <p className={styles.foot}>
          로그인하면 <Link href="/terms">이용약관</Link> 및{' '}
          <Link href="/privacy">개인정보 처리방침</Link>에 동의하는 것으로 봅니다.
          <br />
          처음 오셨다면 다음 화면에서 약관 동의를 거쳐 가입이 완료됩니다.
        </p>
      </div>
    </div>
  );
}
