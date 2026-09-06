'use client';

import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { useState } from 'react';

import { Notice } from '@/components/auth/Notice';
import {
  useConfirmEmailCode,
  useSendEmailCode,
  useStartIdentity,
  useAuthProviders,
} from '@/hooks/useUserSession';
import { identityReturnUrl, pendingIdentity } from '@/utils/identityReturn';
import { safeNext } from '@/utils/authStorage';
import { ApiError } from '@/types/api';
import styles from '@/components/auth/auth.module.css';

/**
 * 자체 회원가입 — 이메일 인증 → 비밀번호 → 본인확인.
 *
 * <h2>왜 한 화면에서 단계로 나누는가</h2>
 * 페이지를 나누면 각 단계가 새로고침·뒤로가기에 견뎌야 하고, 그때마다 진행 상태를
 * 어딘가에 보관해야 한다. 한 화면에 두면 그 문제가 없다 — 다만 본인확인만은 우리
 * 화면을 떠나므로, 거기서만 상태를 sessionStorage 로 넘긴다.
 *
 * <h2>약관은 여기서 받지 않는다</h2>
 * 본인확인을 마쳐 봐야 <b>이미 가입한 사람인지</b> 알 수 있다. 이미 회원이면 약관을
 * 다시 받을 이유가 없고, 다 받아 놓고 "이미 회원이시네요"라고 되돌리는 것은 나쁘다.
 * 그래서 약관은 본인확인 뒤 {@code /signup} 에서 받는다.
 */
export function EmailSignUpView() {
  const searchParams = useSearchParams();
  const next = safeNext(searchParams.get('next'));

  const [step, setStep] = useState<'email' | 'code' | 'password'>('email');
  const [email, setEmail] = useState('');
  const [code, setCode] = useState('');
  const [password, setPassword] = useState('');
  const [passwordAgain, setPasswordAgain] = useState('');
  const [signUpToken, setSignUpToken] = useState('');

  const sendCode = useSendEmailCode();
  const confirmCode = useConfirmEmailCode();
  const startIdentity = useStartIdentity();
  const { data: config, isLoading: configLoading } = useAuthProviders();

  const failure = sendCode.error ?? confirmCode.error ?? startIdentity.error;
  const busy = sendCode.isPending || confirmCode.isPending || startIdentity.isPending;

  // 형식 검증을 여기서 하지 않는다. 백엔드가 최종 판단하고, 프론트가 규칙을 따로
  // 구현하면 두 규칙이 갈라져 "화면은 통과, 서버는 거절"이 생긴다.
  // 여기서 보는 것은 <b>사용자가 실수하기 쉬운 것</b>뿐이다.
  const passwordMismatch = passwordAgain !== '' && password !== passwordAgain;
  const canSubmitPassword = password !== '' && password === passwordAgain;

  const submitEmail = (e: React.FormEvent) => {
    e.preventDefault();
    sendCode.mutate(email.trim(), { onSuccess: () => setStep('code') });
  };

  const submitCode = (e: React.FormEvent) => {
    e.preventDefault();
    confirmCode.mutate(
      { email: email.trim(), code: code.trim() },
      {
        onSuccess: (res) => {
          setSignUpToken(res.signUpToken);
          setStep('password');
        },
      },
    );
  };

  const submitPassword = (e: React.FormEvent) => {
    e.preventDefault();
    if (!canSubmitPassword) return;

    // 본인확인 창으로 떠나기 전에, 돌아왔을 때 이어갈 것을 남긴다.
    // 비밀번호는 담지 않는다 — sessionStorage 는 같은 출처 스크립트가 다 읽는다.
    pendingIdentity.save({ signUpToken, origin: 'email', next });

    startIdentity.mutate(
      { signUpToken, returnUrl: identityReturnUrl() },
      { onSuccess: (res) => window.location.assign(res.startUrl) },
    );
  };

  return (
    <div className={styles.page}>
      <header className={styles.head}>
        <h1 className={styles.title}>회원가입</h1>
        <p className={styles.lead}>
          이메일 인증과 휴대폰 본인확인을 거쳐 가입합니다.
          <br />
          본인확인은 매입 신청과 주문에 그대로 쓰이므로 한 번만 하시면 됩니다.
        </p>
      </header>

      <div className={styles.card}>
        {/*
          자체 가입은 휴대폰 본인확인이 있어야 성립한다 — 이름도 생년월일도 그것으로만
          온다. 주소를 직접 치고 들어온 경우를 대비해 여기서도 막는다. 서버도 막지만
          화면이 폼을 다 보여준 뒤 마지막에 실패하는 것은 나쁜 안내다.
        */}
        {configLoading ? (
          <p className={styles.loading}>불러오는 중…</p>
        ) : config && !config.emailSignUpAvailable ? (
          <>
            <Notice tone="warn" title="이메일 회원가입은 준비 중입니다">
              휴대폰 본인확인 연동을 준비하고 있습니다. 지금은 소셜 계정으로 가입해
              주세요.
            </Notice>
            <p className={styles.foot}>
              <Link href="/login">로그인 화면으로</Link>
            </p>
          </>
        ) : (
          <>
            <StepMarker current={step} />

        {failure && (
          <div style={{ marginBottom: 16 }}>
            <Notice tone="error" title={titleOf(failure)}>
              {failure instanceof ApiError ? failure.message : '잠시 후 다시 시도해 주세요.'}
            </Notice>
          </div>
        )}

        {step === 'email' && (
          <form onSubmit={submitEmail}>
            <div className={styles.field}>
              <label className={styles.label} htmlFor="su-email">
                이메일
              </label>
              <input
                id="su-email"
                type="email"
                className={styles.input}
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="hong@example.com"
                autoComplete="email"
                required
              />
              <span className={styles.hint}>
                로그인 아이디로 쓰이고, 주문 안내가 이 주소로 갑니다.
              </span>
            </div>
            <div className={styles.actions}>
              <button type="submit" className={styles.primary} disabled={busy}>
                {sendCode.isPending ? '보내는 중…' : '인증번호 받기'}
              </button>
            </div>
          </form>
        )}

        {step === 'code' && (
          <form onSubmit={submitCode}>
            <Notice tone="info">
              <strong>{email}</strong> 으로 인증번호를 보냈습니다. 메일이 보이지 않으면
              스팸함도 확인해 주세요.
            </Notice>
            <div className={styles.field} style={{ marginTop: 16 }}>
              <label className={styles.label} htmlFor="su-code">
                인증번호
              </label>
              <input
                id="su-code"
                className={styles.input}
                value={code}
                onChange={(e) => setCode(e.target.value.replace(/\D/g, ''))}
                inputMode="numeric"
                maxLength={6}
                placeholder="6자리 숫자"
                autoComplete="one-time-code"
                required
              />
            </div>
            <div className={styles.actions}>
              <button type="submit" className={styles.primary} disabled={busy || code.length < 6}>
                {confirmCode.isPending ? '확인 중…' : '확인'}
              </button>
              <button
                type="button"
                className={styles.ghost}
                disabled={busy}
                onClick={() => {
                  setCode('');
                  setStep('email');
                  confirmCode.reset();
                }}
              >
                주소 바꾸기
              </button>
            </div>
          </form>
        )}

        {step === 'password' && (
          <form onSubmit={submitPassword}>
            <div className={styles.field}>
              <label className={styles.label} htmlFor="su-password">
                비밀번호
              </label>
              <input
                id="su-password"
                type="password"
                className={styles.input}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                autoComplete="new-password"
                required
              />
              <span className={styles.hint}>
                영문·숫자·특수문자를 모두 포함한 10자 이상.
              </span>
            </div>
            <div className={styles.field}>
              <label className={styles.label} htmlFor="su-password2">
                비밀번호 확인
              </label>
              <input
                id="su-password2"
                type="password"
                className={styles.input}
                value={passwordAgain}
                onChange={(e) => setPasswordAgain(e.target.value)}
                autoComplete="new-password"
                required
              />
              {passwordMismatch && (
                <span className={styles.error}>비밀번호가 서로 다릅니다.</span>
              )}
            </div>
            <div className={styles.actions}>
              <button
                type="submit"
                className={styles.primary}
                disabled={busy || !canSubmitPassword}
              >
                {startIdentity.isPending ? '이동 중…' : '휴대폰 본인확인'}
              </button>
            </div>
            <p className={styles.foot}>
              다음 화면에서 통신사 본인확인을 진행합니다. 이름·생년월일·휴대폰 번호는
              본인확인 결과로 채워지며 직접 입력하지 않습니다.
            </p>
          </form>
        )}

            <p className={styles.foot}>
              이미 회원이신가요? <Link href="/login">로그인</Link>
            </p>
          </>
        )}
      </div>
    </div>
  );
}

/** 지금 몇 단계인지. 남은 단계가 보이지 않으면 사용자는 언제 끝나는지 모른다. */
function StepMarker({ current }: { current: 'email' | 'code' | 'password' }) {
  const steps = [
    { key: 'email', label: '이메일' },
    { key: 'code', label: '인증번호' },
    { key: 'password', label: '비밀번호' },
    { key: 'identity', label: '본인확인' },
  ];
  const index = steps.findIndex((s) => s.key === current);

  return (
    <ol className={styles.rows} aria-label="가입 단계">
      <li className={styles.row}>
        <span className={styles.rowLabel}>진행</span>
        <span className={styles.rowValue}>
          {steps.map((s, i) => (
            <span key={s.key} style={{ opacity: i <= index ? 1 : 0.4 }}>
              {i > 0 && ' › '}
              {s.label}
            </span>
          ))}
        </span>
      </li>
    </ol>
  );
}

function titleOf(error: unknown): string {
  if (!(error instanceof ApiError)) return '문제가 생겼습니다';
  switch (error.code) {
    case 'EMAIL_VERIFICATION_ATTEMPTS_EXCEEDED':
      return '인증 시도 횟수를 초과했습니다';
    case 'EMAIL_VERIFICATION_INVALID':
      return '인증번호를 확인해 주세요';
    case 'MAIL_SEND_FAILED':
      return '인증 메일을 보내지 못했습니다';
    case 'IDENTITY_PROVIDER_UNAVAILABLE':
      return '본인확인을 준비 중입니다';
    default:
      return '문제가 생겼습니다';
  }
}
