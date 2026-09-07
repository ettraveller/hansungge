'use client';

import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { useEffect, useState } from 'react';

import { Notice } from '@/components/auth/Notice';
import { SocialButtons } from '@/components/auth/SocialButtons';
import {
  useAuthProviders,
  useSignInWithPassword,
  useStartOAuth,
  useUserSession,
} from '@/hooks/useUserSession';
import { oauthState, safeNext } from '@/utils/authStorage';
import { ApiError } from '@/types/api';
import { PasswordInput } from '@/components/ui/PasswordInput';
import type { OAuthProviderType } from '@/types/user';
import styles from '@/components/auth/auth.module.css';

/**
 * 로그인.
 *
 * <p>이메일 로그인과 소셜 로그인을 함께 둔다. 소셜 계정이 없거나 쓰기 싫은 고객이
 * 있고, 귀금속 매입 고객층은 특히 그렇다.
 *
 * <p>실패 사유를 세분해 보여주지 않는다 — "없는 계정"과 "틀린 비밀번호"를 구분해
 * 주면 가입 여부를 확인하는 도구가 된다. 다만 <b>비밀번호가 없는 계정</b>과
 * <b>잠긴 계정</b>은 알려준다. 그 둘은 사용자가 다음에 할 일이 다르고, 알려주지
 * 않으면 맞는 비밀번호를 계속 넣어보게 된다.
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

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const signInWithPassword = useSignInWithPassword();

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
          이메일 또는 소셜 계정으로 로그인합니다.
          <br />
          회원가입에는 휴대폰 본인확인이 필요하며, 매입 신청에 그대로 쓰입니다.
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

        {signInWithPassword.error && (
          <div style={{ marginBottom: 16 }}>
            <Notice tone="error" title={loginErrorTitle(signInWithPassword.error)}>
              {signInWithPassword.error instanceof ApiError
                ? signInWithPassword.error.message
                : '잠시 후 다시 시도해 주세요.'}
            </Notice>
          </div>
        )}

        <form
          onSubmit={(e) => {
            e.preventDefault();
            signInWithPassword.mutate(
              { email: email.trim(), password },
              { onSuccess: () => router.replace(next) },
            );
          }}
        >
          <div className={styles.field}>
            <label className={styles.label} htmlFor="login-email">
              이메일
            </label>
            <input
              id="login-email"
              type="email"
              className={styles.input}
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              autoComplete="email"
              required
            />
          </div>
          <div className={styles.field}>
            <label className={styles.label} htmlFor="login-password">
              비밀번호
            </label>
            <PasswordInput
              id="login-password"
              inputClassName={styles.input}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              autoComplete="current-password"
              required
            />
          </div>
          <div className={styles.actions}>
            <button
              type="submit"
              className={styles.primary}
              disabled={signInWithPassword.isPending}
            >
              {signInWithPassword.isPending ? '로그인 중…' : '로그인'}
            </button>
          </div>
        </form>

        {/*
          로그인 폼은 늘 두고 가입 링크만 감춘다.

          이미 비밀번호가 있는 회원은 <b>본인확인 공급자 상태와 무관하게</b> 들어올 수
          있어야 한다. 공급자 설정이 잘못되거나 계약이 끊긴 순간 그 사람들이 통째로
          로그인하지 못하면, 그건 가입이 막히는 것보다 훨씬 나쁘다.

          반대로 가입은 본인확인 없이 성립하지 않으므로, 쓸 수 없을 때는 링크를 아예
          그리지 않는다 — 눌러야만 실패를 아는 화면을 만들지 않는다.
        */}
        {data?.emailSignUpAvailable && (
          <p className={styles.foot}>
            계정이 없으신가요? <Link href="/signup/email">이메일로 회원가입</Link>
          </p>
        )}

        <h2 className={styles.sectionTitle}>소셜 계정으로 계속하기</h2>

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
          처음 오셨다면 휴대폰 본인확인과 약관 동의를 거쳐 가입이 완료됩니다.
        </p>
      </div>
    </div>
  );
}

/** 로그인 실패의 결이 다른 두 경우만 제목을 나눈다. */
function loginErrorTitle(error: unknown): string {
  if (!(error instanceof ApiError)) return '로그인하지 못했습니다';
  switch (error.code) {
    case 'PASSWORD_NOT_SET':
      return '소셜 계정으로 가입하신 회원입니다';
    case 'USER_ACCOUNT_LOCKED':
      return '계정이 잠겼습니다';
    default:
      return '로그인하지 못했습니다';
  }
}
