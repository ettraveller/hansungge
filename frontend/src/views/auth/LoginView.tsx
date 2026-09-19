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
 * 로그인 — 휴대폰번호 + 비밀번호, 인증번호, 소셜.
 *
 * <p>로그인 ID 는 휴대폰번호다. 가입 때 본인확인으로 명의가 확인된 번호라, 비밀번호를
 * 잊었어도 문자 인증번호로 들어올 수 있다. 어느 문으로 들어와도 같은 계정이다.
 *
 * <p>실패 사유를 세분해 보여주지 않는다 — "없는 번호"와 "틀린 비밀번호"를 구분해 주면
 * 가입 여부를 확인하는 도구가 된다. 다만 <b>비밀번호가 없는 계정</b>과 <b>잠긴 계정</b>은
 * 알려준다. 그 둘은 사용자가 다음에 할 일이 다르다.
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

  const [phone, setPhone] = useState('');
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
  const nextQuery = next === '/' ? '' : `?next=${encodeURIComponent(next)}`;

  return (
    <div className={styles.page}>
      <header className={styles.head}>
        <h1 className={styles.title}>로그인</h1>
        <p className={styles.lead}>휴대폰번호 또는 소셜 계정으로 로그인합니다.</p>
      </header>

      <div className={styles.card}>
        {reason === 'required' && (
          <div style={{ marginBottom: 16 }}>
            <Notice tone="warn">로그인이 필요한 화면입니다.</Notice>
          </div>
        )}
        {reason === 'reset' && (
          <div style={{ marginBottom: 16 }}>
            <Notice tone="info" title="비밀번호를 바꿨습니다">
              새 비밀번호로 로그인해 주세요. 다른 기기에서는 모두 로그아웃됐습니다.
            </Notice>
          </div>
        )}

        {startError && (
          <div style={{ marginBottom: 16 }}>
            <Notice tone="error" title="로그인을 시작하지 못했습니다">
              {startError instanceof ApiError ? startError.message : '잠시 후 다시 시도해 주세요.'}
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
              { phone: phone.trim(), password },
              { onSuccess: () => router.replace(next) },
            );
          }}
        >
          <div className={styles.field}>
            <label className={styles.label} htmlFor="login-phone">
              휴대폰번호
            </label>
            <input
              id="login-phone"
              type="tel"
              inputMode="numeric"
              className={styles.input}
              value={phone}
              onChange={(e) => setPhone(e.target.value)}
              placeholder="010-1234-5678"
              autoComplete="tel"
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
            <button type="submit" className={styles.primary} disabled={signInWithPassword.isPending}>
              {signInWithPassword.isPending ? '로그인 중…' : '로그인'}
            </button>
          </div>
        </form>

        <p className={styles.subLinks}>
          {data?.smsLoginAvailable && (
            <Link href={`/login/sms${nextQuery}`}>인증번호로 로그인 ›</Link>
          )}
          <Link href="/password/reset">비밀번호 찾기</Link>
          {/*
            가입 링크는 본인확인이 준비됐을 때만 그린다. 로그인 폼은 늘 둔다 — 공급자
            설정이 잘못돼도 이미 가입한 사람은 들어올 수 있어야 한다.
          */}
          {data?.signUpAvailable && <Link href={`/signup${nextQuery}`}>회원가입</Link>}
        </p>

        <p className={styles.divider}>또는</p>

        {isLoading ? (
          <p className={styles.loading}>불러오는 중…</p>
        ) : error ? (
          <Notice tone="error" title="로그인 수단을 불러오지 못했습니다">
            잠시 후 다시 시도해 주세요.
          </Notice>
        ) : providers.length === 0 ? (
          <Notice tone="warn" title="현재 이용할 수 있는 소셜 로그인이 없습니다">
            휴대폰번호로 로그인해 주세요. 도움이 필요하면 <Link href="/support">고객센터</Link>로
            문의해 주세요.
          </Notice>
        ) : (
          <SocialButtons providers={providers} pendingProvider={pendingProvider} onSelect={select} />
        )}

        <p className={styles.foot}>
          로그인하면 <Link href="/terms">이용약관</Link> 및{' '}
          <Link href="/privacy">개인정보 처리방침</Link>에 동의하는 것으로 봅니다.
          {data && !data.signUpAvailable && (
            <>
              <br />
              지금은 새 회원가입을 받지 않습니다. 이미 가입하신 분은 로그인할 수 있습니다.
            </>
          )}
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
      return '비밀번호를 정하지 않은 계정입니다';
    case 'USER_ACCOUNT_LOCKED':
      return '계정이 잠겼습니다';
    default:
      return '로그인하지 못했습니다';
  }
}
