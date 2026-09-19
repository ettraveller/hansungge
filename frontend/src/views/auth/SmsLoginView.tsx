'use client';

import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { useEffect, useState } from 'react';

import { Notice } from '@/components/auth/Notice';
import {
  useAuthProviders,
  useSendLoginCode,
  useSignInWithCode,
  useUserSession,
} from '@/hooks/useUserSession';
import { safeNext } from '@/utils/authStorage';
import { ApiError } from '@/types/api';
import styles from '@/components/auth/auth.module.css';

/** 재발송까지 기다릴 시간. 서버 쿨다운(60초)과 같아야 버튼이 서버보다 먼저 풀리지 않는다. */
const RESEND_SECONDS = 60;

/**
 * 인증번호 로그인 (`/login/sms`).
 *
 * <p>가입 때 본인확인으로 명의가 확인된 번호라, 여기서는 <b>그 번호를 지금 가지고 있는지</b>만
 * 확인한다. 비밀번호를 잊은 사람도 비밀번호 찾기(본인확인, 건당 과금) 없이 들어온다.
 *
 * <p>문자를 보낸 뒤에는 가입 여부와 상관없이 같은 안내를 보여준다 — 서버도 같은 응답을 준다.
 * "가입되지 않은 번호입니다"라고 알려주면 번호로 회원 명단을 훑을 수 있다.
 */
export function SmsLoginView() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const next = safeNext(searchParams.get('next'));

  const { isAuthenticated, restored } = useUserSession();
  const { data: config } = useAuthProviders();
  const send = useSendLoginCode();
  const signIn = useSignInWithCode();

  const [phone, setPhone] = useState('');
  const [code, setCode] = useState('');
  const [sent, setSent] = useState(false);
  const [remaining, setRemaining] = useState(0);

  useEffect(() => {
    if (restored && isAuthenticated) router.replace(next);
  }, [restored, isAuthenticated, next, router]);

  // 재발송 카운트다운. 화면이 서버보다 먼저 "다시 받기"를 열면 429 만 받는다.
  useEffect(() => {
    if (remaining <= 0) return;
    const timer = setTimeout(() => setRemaining((s) => s - 1), 1000);
    return () => clearTimeout(timer);
  }, [remaining]);

  const requestCode = () => {
    send.mutate(phone.trim(), {
      onSuccess: () => {
        setSent(true);
        setCode('');
        setRemaining(RESEND_SECONDS);
        signIn.reset();
      },
    });
  };

  const failure = send.error ?? signIn.error;

  if (config && !config.smsLoginAvailable) {
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
        <p className={styles.lead}>가입한 휴대폰번호로 받은 6자리 번호로 로그인합니다.</p>
      </header>

      <div className={styles.card}>
        {failure && (
          <div style={{ marginBottom: 16 }}>
            <Notice tone="error" title={titleOf(failure)}>
              {failure instanceof ApiError ? failure.message : '잠시 후 다시 시도해 주세요.'}
            </Notice>
          </div>
        )}

        <form
          onSubmit={(e) => {
            e.preventDefault();
            if (!sent) {
              requestCode();
              return;
            }
            signIn.mutate({ phone: phone.trim(), code }, { onSuccess: () => router.replace(next) });
          }}
        >
          <div className={styles.field}>
            <label className={styles.label} htmlFor="sms-phone">
              휴대폰번호
            </label>
            <input
              id="sms-phone"
              type="tel"
              inputMode="numeric"
              className={styles.input}
              value={phone}
              onChange={(e) => {
                setPhone(e.target.value);
                setSent(false);
              }}
              placeholder="010-1234-5678"
              autoComplete="tel"
              required
            />
          </div>

          {sent && (
            <>
              <Notice tone="info">
                가입된 번호라면 인증번호를 보냈습니다. 5분 안에 입력해 주세요.
              </Notice>
              <div className={styles.field} style={{ marginTop: 16 }}>
                <label className={styles.label} htmlFor="sms-code">
                  인증번호
                </label>
                <input
                  id="sms-code"
                  className={`${styles.input} ${styles.codeInput}`}
                  value={code}
                  onChange={(e) => setCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
                  inputMode="numeric"
                  maxLength={6}
                  placeholder="6자리 숫자"
                  autoComplete="one-time-code"
                  autoFocus
                  required
                />
              </div>
            </>
          )}

          <div className={styles.actions}>
            {sent ? (
              <>
                <button
                  type="submit"
                  className={styles.primary}
                  disabled={signIn.isPending || code.length < 6}
                >
                  {signIn.isPending ? '확인 중…' : '로그인'}
                </button>
                <button
                  type="button"
                  className={styles.ghost}
                  disabled={send.isPending || remaining > 0}
                  onClick={requestCode}
                >
                  {remaining > 0 ? `다시 받기 (${remaining}초)` : '다시 받기'}
                </button>
              </>
            ) : (
              <button type="submit" className={styles.primary} disabled={send.isPending}>
                {send.isPending ? '보내는 중…' : '인증번호 받기'}
              </button>
            )}
          </div>
        </form>

        <p className={styles.foot}>
          인증번호는 누구에게도 알려주지 마세요. 한성금거래소는 전화로 인증번호를 묻지 않습니다.
          <br />
          <Link href="/login">비밀번호로 로그인</Link>
        </p>
      </div>
    </div>
  );
}

function titleOf(error: unknown): string {
  if (!(error instanceof ApiError)) return '로그인하지 못했습니다';
  switch (error.code) {
    case 'VERIFICATION_CODE_INVALID':
      return '인증번호를 확인해 주세요';
    case 'VERIFICATION_ATTEMPTS_EXCEEDED':
      return '입력 횟수를 넘었습니다';
    case 'TOO_MANY_REQUESTS':
      return '잠시 후 다시 받아 주세요';
    case 'SMS_SEND_FAILED':
      return '문자를 보내지 못했습니다';
    default:
      return '로그인하지 못했습니다';
  }
}
