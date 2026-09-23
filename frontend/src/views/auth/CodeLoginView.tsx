'use client';

import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { useEffect } from 'react';

import { CodeVerifyForm, codeErrorTitle } from '@/components/auth/CodeVerifyForm';
import { Notice } from '@/components/auth/Notice';
import {
  useAuthProviders,
  useSendLoginCode,
  useSignInWithCode,
  useUserSession,
} from '@/hooks/useUserSession';
import { safeNext } from '@/utils/authStorage';
import { ApiError } from '@/types/api';
import type { VerificationChannel } from '@/types/user';
import styles from '@/components/auth/auth.module.css';

/**
 * 인증번호 로그인 (`/login/sms`) — 문자 또는 이메일.
 *
 * <p>휴대폰번호는 가입 때 본인인증으로 명의가 확인된 값이라, 여기서는 <b>그 번호를 지금 가지고
 * 있는지</b>만 확인한다. 이메일은 가입할 때 인증번호로 소유를 확인한 주소만 통한다.
 *
 * <p>비밀번호를 잊은 사람도 비밀번호 찾기(본인인증, 건당 과금) 없이 들어온다. 이메일 쪽은
 * 문자비도 들지 않는다.
 */
export function CodeLoginView() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const next = safeNext(searchParams.get('next'));

  const { isAuthenticated, restored } = useUserSession();
  const { data: config, isLoading } = useAuthProviders();
  const send = useSendLoginCode();
  const signIn = useSignInWithCode();

  useEffect(() => {
    if (restored && isAuthenticated) router.replace(next);
  }, [restored, isAuthenticated, next, router]);

  const channels: VerificationChannel[] = [
    ...(config?.smsLoginAvailable ? (['SMS'] as const) : []),
    ...(config?.emailCodeAvailable ? (['EMAIL'] as const) : []),
  ];
  const failure = send.error ?? signIn.error;

  if (isLoading) {
    return (
      <div className={styles.page}>
        <p className={styles.loading}>불러오는 중…</p>
      </div>
    );
  }

  if (channels.length === 0) {
    return (
      <div className={styles.page}>
        <header className={styles.head}>
          <h1 className={styles.title}>인증번호 로그인</h1>
        </header>
        <div className={styles.card}>
          <Notice tone="warn" title="지금은 인증번호 로그인을 쓸 수 없습니다">
            비밀번호나 소셜 계정으로 로그인해 주세요.
          </Notice>
          <p className={styles.foot}>
            <Link href="/login">로그인 화면으로</Link>
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className={styles.page}>
      <header className={styles.head}>
        <h1 className={styles.title}>인증번호 로그인</h1>
        <p className={styles.lead}>
          {channels.length > 1
            ? '가입한 휴대폰번호나 이메일로 받은 6자리 번호로 로그인합니다.'
            : '가입한 휴대폰번호로 받은 6자리 번호로 로그인합니다.'}
        </p>
      </header>

      <div className={styles.card}>
        {failure && (
          <div style={{ marginBottom: 16 }}>
            <Notice tone="error" title={codeErrorTitle(failure, '로그인하지 못했습니다')}>
              {failure instanceof ApiError ? failure.message : '잠시 후 다시 시도해 주세요.'}
            </Notice>
          </div>
        )}

        <CodeVerifyForm
          channels={channels}
          sending={send.isPending}
          verifying={signIn.isPending}
          submitLabel="로그인"
          sentNotice="가입된 곳이라면 인증번호를 보냈습니다. 5분 안에 입력해 주세요."
          onSend={(channel, target, onSent) => {
            signIn.reset();
            send.mutate({ channel, target }, { onSuccess: onSent });
          }}
          onVerify={(channel, target, code) =>
            signIn.mutate({ channel, target, code }, { onSuccess: () => router.replace(next) })
          }
        />

        <p className={styles.foot}>
          인증번호는 누구에게도 알려주지 마세요. 한성금거래소는 전화로 인증번호를 묻지 않습니다.
          <br />
          <Link href="/login">비밀번호로 로그인</Link>
        </p>
      </div>
    </div>
  );
}
