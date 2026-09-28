'use client';

import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useRef, useState } from 'react';

import { AccountSummary, NewPasswordForm } from '@/components/auth/NewPasswordForm';
import { Notice } from '@/components/auth/Notice';
import { PasswordRules, isValidPassword } from '@/components/auth/PasswordRules';
import { PasswordInput } from '@/components/ui/PasswordInput';
import * as authApi from '@/api/auth';
import { useCompleteSignUp, useSendSignUpEmailCode, userKeys } from '@/hooks/useUserSession';
import { useUserAuthStore } from '@/stores/useUserAuthStore';
import { identityOutcome, pendingIdentity, type IdentityOutcome } from '@/utils/identityReturn';
import { formatPhone } from '@/utils/format';
import { ApiError } from '@/types/api';
import type { IdentityResult, User } from '@/types/user';
import styles from '@/components/auth/auth.module.css';

/**
 * 본인인증(간편인증)을 마치고 돌아오는 자리 (`/auth/identity?providerToken=…`).
 *
 * <p>이니시스가 결과를 백엔드 수신 주소로 보내면 백엔드가 사용자를 여기로 보낸다. 여기서 결과를
 * <b>한 번</b> 제출하고, 서버가 정해 준 다음 단계를 이 자리에서 이어서 보여준다.
 *
 * <ul>
 *   <li>자체 가입 · 새 사람    → 비밀번호 + 선택 이메일
 *   <li>자체 가입 · 이미 회원  → 기존 계정 안내 + 바로 비밀번호 재설정
 *   <li>비밀번호 찾기          → 새 비밀번호
 *   <li>마이페이지 본인인증    → 신원 반영 후 마이페이지로 (매입·정산계좌 앞, 소셜 회원의 번호 등록)
 * </ul>
 * 소셜 가입은 본인인증을 받지 않으므로 여기 오지 않는다 (2026-09-21 개편).
 *
 * <h2>새로고침해도 다시 제출하지 않는다</h2>
 * 같은 본인확인 결과를 두 번 내면 서버가 "이미 사용된 본인확인"으로 거부한다. 비밀번호를 입력하다
 * 새로고침하면 그 일이 생기므로, 받은 결과를 sessionStorage 에 두고 같은 providerToken 이면
 * 그것을 쓴다.
 */
export function IdentityCallbackView() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const providerToken = searchParams.get('providerToken');

  const { data: outcome, error, isPending } = useQuery<IdentityOutcome>({
    queryKey: ['identity-callback', providerToken],
    retry: false,
    staleTime: Infinity,
    refetchOnWindowFocus: false,
    queryFn: async () => {
      if (!providerToken) {
        throw new CallbackError(
          '본인인증 결과를 받지 못했습니다',
          '본인인증 창에서 취소하셨거나 인증이 완료되지 않았습니다.',
        );
      }
      const saved = identityOutcome.readFor(providerToken);
      if (saved) return saved;

      const pending = pendingIdentity.read();
      if (!pending) {
        throw new CallbackError(
          '진행 중인 본인인증이 없습니다',
          '창을 새로 열었거나 시간이 오래 지났습니다. 처음부터 다시 진행해 주세요.',
        );
      }

      let result: IdentityOutcome;
      switch (pending.flow) {
        case 'reset':
          result = {
            flow: 'reset',
            providerToken,
            next: pending.next,
            grant: await authApi.completePasswordResetIdentity(pending.token, providerToken),
          };
          break;
        case 'verify':
          result = {
            flow: 'verify',
            providerToken,
            next: pending.next,
            user: await authApi.completeMemberIdentity(pending.token, providerToken),
          };
          break;
        default:
          result = {
            flow: 'signup',
            providerToken,
            next: pending.next,
            result: await authApi.completeIdentity(pending.token, providerToken),
          };
      }
      identityOutcome.save(result);
      pendingIdentity.clear();
      return result;
    },
  });

  const finish = (to: string) => {
    identityOutcome.clear();
    router.replace(to);
  };

  if (error) {
    return <ErrorScreen error={error} />;
  }
  if (isPending || !outcome) {
    return (
      <Shell>
        <p className={styles.loading}>본인인증 결과를 확인하는 중…</p>
      </Shell>
    );
  }

  if (outcome.flow === 'reset') {
    return (
      <Shell title="비밀번호 재설정">
        <NewPasswordForm
          grant={outcome.grant}
          onDone={() => finish('/login?reason=reset')}
        />
      </Shell>
    );
  }

  if (outcome.flow === 'verify') {
    return <MemberVerified user={outcome.user} onDone={() => finish(outcome.next)} />;
  }

  const { result, next } = outcome;
  switch (result.step) {
    case 'SET_PASSWORD':
      return <SetPasswordStep result={result} onDone={() => finish(next)} />;
    case 'EXISTING_ACCOUNT':
      return <ExistingAccountStep result={result} onDone={() => finish('/login?reason=reset')} />;
  }
}

// =====================================================================
// 단계별 화면
// =====================================================================

/** 마이페이지 본인인증 완료 — 내 정보를 새 값으로 바꾸고 원래 화면으로. */
function MemberVerified({ user, onDone }: { user: User; onDone: () => void }) {
  const queryClient = useQueryClient();
  const setUser = useUserAuthStore((s) => s.setUser);
  // 반영과 이동은 한 번만 — onDone 은 렌더마다 새 함수라 의존성만으로는 막을 수 없다.
  const done = useRef(false);
  useEffect(() => {
    if (done.current) return;
    done.current = true;
    queryClient.setQueryData(userKeys.me(), user);
    setUser(user);
    onDone();
  }, [queryClient, setUser, user, onDone]);

  return (
    <Shell>
      <p className={styles.loading}>본인인증을 마쳤습니다. 이동합니다…</p>
    </Shell>
  );
}

/**
 * 자체 가입 — 비밀번호와 선택 이메일.
 *
 * <p>입력칸은 둘뿐이다. 이름·휴대폰은 본인인증 값을 읽기 전용으로 보여준다.
 */
function SetPasswordStep({ result, onDone }: { result: IdentityResult; onDone: () => void }) {
  const signUpToken = result.signUpToken ?? '';
  const complete = useCompleteSignUp();
  const sendCode = useSendSignUpEmailCode();

  const [password, setPassword] = useState('');
  const [passwordAgain, setPasswordAgain] = useState('');
  const [email, setEmail] = useState('');
  const [emailCode, setEmailCode] = useState('');
  const [codeSentTo, setCodeSentTo] = useState<string | null>(null);

  // 형식 규칙은 서버가 판단한다. 여기서는 실수하기 쉬운 것만 본다.
  const mismatch = passwordAgain !== '' && password !== passwordAgain;
  const trimmedEmail = email.trim();
  // 인증번호를 받은 뒤 주소를 바꾸면 다시 받아야 한다
  const emailReady = trimmedEmail === '' || (codeSentTo === trimmedEmail && emailCode.length === 6);
  // 규칙을 못 채운 채로 보내지 않는다. 가입 마지막 단계에서 서버가 거절하면
  // 그만큼 가입 토큰(15분)을 까먹는다.
  const canSubmit =
    isValidPassword(password) && password === passwordAgain && emailReady && !complete.isPending;

  const failure = complete.error ?? sendCode.error;

  return (
    <Shell title="회원가입" lead="마지막 단계입니다. 로그인에 쓸 비밀번호를 정해 주세요.">
      <div className={styles.verified}>
        본인인증 완료 — <strong>{result.name}</strong> · {formatPhone(result.phone)}
        <br />
        이 휴대폰번호가 로그인 아이디가 됩니다.
      </div>

      {failure && (
        <div style={{ marginBottom: 16 }}>
          <Notice tone="error" title="가입하지 못했습니다">
            {failure instanceof ApiError ? failure.message : '잠시 후 다시 시도해 주세요.'}
          </Notice>
        </div>
      )}

      <form
        onSubmit={(e) => {
          e.preventDefault();
          if (!canSubmit) return;
          complete.mutate(
            {
              signUpToken,
              password,
              ...(trimmedEmail ? { email: trimmedEmail, emailCode } : {}),
            },
            { onSuccess: onDone },
          );
        }}
      >
        <div className={styles.field}>
          <label className={styles.label} htmlFor="su-password">
            비밀번호
          </label>
          <PasswordInput
            id="su-password"
            inputClassName={styles.input}
            aria-describedby="su-password-rules"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="new-password"
            required
          />
          <PasswordRules value={password} id="su-password-rules" />
        </div>
        <div className={styles.field}>
          <label className={styles.label} htmlFor="su-password2">
            비밀번호 확인
          </label>
          <PasswordInput
            id="su-password2"
            inputClassName={styles.input}
            value={passwordAgain}
            onChange={(e) => setPasswordAgain(e.target.value)}
            autoComplete="new-password"
            required
          />
          {mismatch && <span className={styles.error}>비밀번호가 서로 다릅니다.</span>}
        </div>

        <div className={styles.field}>
          <label className={styles.label} htmlFor="su-email">
            이메일 <span className={styles.hint}>(선택)</span>
          </label>
          <div className={styles.actions}>
            <input
              id="su-email"
              type="email"
              className={styles.input}
              style={{ flex: 1, minWidth: 0 }}
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="hong@example.com"
              autoComplete="email"
            />
            <button
              type="button"
              className={styles.ghost}
              disabled={trimmedEmail === '' || sendCode.isPending}
              onClick={() =>
                sendCode.mutate(
                  { signUpToken, email: trimmedEmail },
                  { onSuccess: () => setCodeSentTo(trimmedEmail) },
                )
              }
            >
              {sendCode.isPending ? '보내는 중…' : codeSentTo ? '다시 받기' : '인증번호 받기'}
            </button>
          </div>
          <span className={styles.hint}>
            세금계산서·거래명세서를 받을 주소입니다. 건너뛰고 나중에 넣어도 됩니다.
          </span>
        </div>

        {codeSentTo && codeSentTo === trimmedEmail && (
          <div className={styles.field}>
            <label className={styles.label} htmlFor="su-email-code">
              이메일 인증번호
            </label>
            <input
              id="su-email-code"
              className={`${styles.input} ${styles.codeInput}`}
              value={emailCode}
              onChange={(e) => setEmailCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
              inputMode="numeric"
              maxLength={6}
              placeholder="6자리 숫자"
              autoComplete="one-time-code"
            />
            <span className={styles.hint}>메일이 보이지 않으면 스팸함도 확인해 주세요.</span>
          </div>
        )}

        <div className={styles.actions}>
          <button type="submit" className={styles.primary} disabled={!canSubmit}>
            {complete.isPending ? '가입 처리 중…' : '가입 완료'}
          </button>
        </div>
      </form>
    </Shell>
  );
}

/**
 * 이미 가입한 사람 — 새 계정을 만들지 않는다.
 *
 * <p>본인인증을 막 통과했으므로 비밀번호 찾기를 따로 거치지 않고 이 자리에서 새 비밀번호를 정할
 * 수 있다. 소셜로 가입해 본인인증만 받아 두었던 사람은 여기서 비밀번호를 처음 정한다 — 이번 인증으로
 * 번호가 채워져 휴대폰 로그인이 된다.
 */
function ExistingAccountStep({ result, onDone }: { result: IdentityResult; onDone: () => void }) {
  const [resetting, setResetting] = useState(false);
  const hasPassword = Boolean(result.accountHasPassword);

  return (
    <Shell title="이미 가입된 계정이 있습니다">
      <Notice tone="info" title="본인인증한 정보로 가입된 계정입니다">
        <AccountSummary email={result.accountEmail} phone={result.accountPhone} />
        {!hasPassword && (
          <>
            <br />
            소셜 계정으로 가입하셔서 비밀번호가 없습니다.
          </>
        )}
      </Notice>

      {resetting ? (
        <div style={{ marginTop: 16 }}>
          <NewPasswordForm
            grant={{
              resetToken: result.resetToken ?? '',
              accountEmail: result.accountEmail,
              accountPhone: result.accountPhone,
            }}
            onDone={onDone}
            hideSummary
          />
        </div>
      ) : (
        <div className={styles.actions} style={{ marginTop: 16 }}>
          <Link className={styles.primary} href="/login" style={centered}>
            로그인하기
          </Link>
          <button type="button" className={styles.ghost} onClick={() => setResetting(true)}>
            {hasPassword ? '비밀번호 다시 정하기' : '비밀번호 정하기'}
          </button>
        </div>
      )}
    </Shell>
  );
}

// =====================================================================
// 공통
// =====================================================================

function Shell({ title, lead, children }: { title?: string; lead?: string; children: React.ReactNode }) {
  return (
    <div className={styles.page}>
      {title && (
        <header className={styles.head}>
          <h1 className={styles.title}>{title}</h1>
          {lead && <p className={styles.lead}>{lead}</p>}
        </header>
      )}
      <div className={styles.card}>{children}</div>
    </div>
  );
}

function ErrorScreen({ error }: { error: unknown }) {
  const notRegistered = error instanceof ApiError && error.code === 'IDENTITY_NOT_REGISTERED';
  const title =
    error instanceof CallbackError
      ? error.title
      : notRegistered
        ? '가입된 계정이 없습니다'
        : '본인인증을 마치지 못했습니다';
  const detail =
    error instanceof CallbackError
      ? error.detail
      : error instanceof ApiError
        ? error.message
        : '잠시 후 다시 시도해 주세요.';

  return (
    <Shell>
      <Notice tone={notRegistered ? 'warn' : 'error'} title={title}>
        {detail}
      </Notice>
      <p className={styles.foot}>
        <Link href="/signup">회원가입</Link>
        {' · '}
        <Link href="/login">로그인</Link>
        {' · '}
        <Link href="/mypage">마이페이지</Link>
      </p>
    </Shell>
  );
}

const centered: React.CSSProperties = { display: 'inline-block', textAlign: 'center', lineHeight: '48px' };

/** 제목과 설명을 나눠 담는다. 한 줄 메시지로는 무엇을 해야 하는지 전할 수 없다. */
class CallbackError extends Error {
  readonly title: string;
  readonly detail: string;

  constructor(title: string, detail: string) {
    super(title);
    this.title = title;
    this.detail = detail;
  }
}
