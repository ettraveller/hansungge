'use client';

import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { useQuery } from '@tanstack/react-query';
import { useEffect, useRef, useState } from 'react';

import { Notice } from '@/components/auth/Notice';
import { PasswordInput } from '@/components/ui/PasswordInput';
import * as authApi from '@/api/auth';
import {
  useApplySignIn,
  useCompleteSignUp,
  useLinkSocialAfterIdentity,
  useResetPassword,
  useSendSignUpEmailCode,
} from '@/hooks/useUserSession';
import { identityOutcome, pendingIdentity, type IdentityOutcome } from '@/utils/identityReturn';
import { formatPhone } from '@/utils/format';
import { ApiError } from '@/types/api';
import type { IdentityResult, PasswordResetGrant } from '@/types/user';
import styles from '@/components/auth/auth.module.css';

/**
 * 본인확인을 마치고 돌아오는 자리 (`/auth/identity?providerToken=…`).
 *
 * <p>이니시스가 결과를 백엔드 수신 주소로 보내면 백엔드가 사용자를 여기로 보낸다. 여기서 결과를
 * <b>한 번</b> 제출하고, 서버가 정해 준 다음 단계를 이 자리에서 이어서 보여준다.
 *
 * <ul>
 *   <li>가입 · 새 사람(자체)  → 비밀번호 + 선택 이메일
 *   <li>가입 · 새 사람(소셜)  → 가입 완료, 로그인
 *   <li>가입 · 이미 회원(자체) → 기존 계정 안내 + 바로 비밀번호 재설정
 *   <li>가입 · 이미 회원(소셜) → 이 소셜 계정을 연결할지 묻는다
 *   <li>비밀번호 찾기          → 새 비밀번호
 * </ul>
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
          '본인확인 결과를 받지 못했습니다',
          '본인확인 창에서 취소하셨거나 인증이 완료되지 않았습니다.',
        );
      }
      const saved = identityOutcome.readFor(providerToken);
      if (saved) return saved;

      const pending = pendingIdentity.read();
      if (!pending) {
        throw new CallbackError(
          '진행 중인 본인확인이 없습니다',
          '창을 새로 열었거나 시간이 오래 지났습니다. 처음부터 다시 진행해 주세요.',
        );
      }

      const result: IdentityOutcome =
        pending.flow === 'reset'
          ? {
              flow: 'reset',
              providerToken,
              next: pending.next,
              grant: await authApi.completePasswordResetIdentity(pending.token, providerToken),
            }
          : {
              flow: 'signup',
              providerToken,
              next: pending.next,
              result: await authApi.completeIdentity(pending.token, providerToken),
            };
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
        <p className={styles.loading}>본인확인 결과를 확인하는 중…</p>
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

  const { result, next } = outcome;
  switch (result.step) {
    case 'SIGNED_IN':
      return <SignedIn result={result} onDone={() => finish(next)} />;
    case 'SET_PASSWORD':
      return <SetPasswordStep result={result} onDone={() => finish(next)} />;
    case 'EXISTING_ACCOUNT':
      return <ExistingAccountStep result={result} onDone={() => finish('/login?reason=reset')} />;
    case 'CONFIRM_LINK':
      return (
        <ConfirmLinkStep result={result} onLinked={() => finish(next)} onCancel={() => finish('/login')} />
      );
  }
}

// =====================================================================
// 단계별 화면
// =====================================================================

/** 소셜 가입 완료 — 세션을 넣고 원래 가려던 곳으로. */
function SignedIn({ result, onDone }: { result: IdentityResult; onDone: () => void }) {
  const apply = useApplySignIn();
  // 세션 적용과 이동은 한 번만 — onDone 은 렌더마다 새 함수라 의존성만으로는 막을 수 없다.
  const done = useRef(false);
  useEffect(() => {
    if (done.current) return;
    done.current = true;
    apply(result.session);
    onDone();
  }, [apply, result.session, onDone]);

  return (
    <Shell>
      <p className={styles.loading}>가입을 마쳤습니다. 이동합니다…</p>
    </Shell>
  );
}

/**
 * 자체 가입 — 비밀번호와 선택 이메일.
 *
 * <p>입력칸은 둘뿐이다. 이름·휴대폰은 본인확인 값을 읽기 전용으로 보여준다.
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
  const canSubmit = password !== '' && password === passwordAgain && emailReady && !complete.isPending;

  const failure = complete.error ?? sendCode.error;

  return (
    <Shell title="회원가입" lead="마지막 단계입니다. 로그인에 쓸 비밀번호를 정해 주세요.">
      <div className={styles.verified}>
        본인확인 완료 — <strong>{result.name}</strong> · {formatPhone(result.phone)}
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
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="new-password"
            required
          />
          <span className={styles.hint}>영문·숫자·특수문자를 모두 포함한 10자 이상.</span>
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
 * <p>본인확인을 막 통과했으므로 비밀번호 찾기를 따로 거치지 않고 이 자리에서 새 비밀번호를 정할
 * 수 있다. 소셜로만 가입해 비밀번호가 없던 사람은 여기서 처음 정한다.
 */
function ExistingAccountStep({ result, onDone }: { result: IdentityResult; onDone: () => void }) {
  const [resetting, setResetting] = useState(false);
  const hasPassword = Boolean(result.accountHasPassword);

  return (
    <Shell title="이미 가입된 계정이 있습니다">
      <Notice tone="info" title="본인확인한 정보로 가입된 계정입니다">
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

/**
 * 이미 가입한 사람이 소셜로 왔다 — 연결할지 묻는다.
 *
 * <p>계정 병합이 아니라 <b>연결</b>이다. 새 계정은 아직 없으니 합칠 것이 없고, 소셜 로그인 수단을
 * 하나 더할 뿐이다. [취소] 하면 아무것도 만들지 않는다 — 새 계정을 만들면 한 사람이 두 계정을
 * 갖게 된다.
 */
function ConfirmLinkStep({
  result,
  onLinked,
  onCancel,
}: {
  result: IdentityResult;
  onLinked: () => void;
  onCancel: () => void;
}) {
  const link = useLinkSocialAfterIdentity();

  return (
    <Shell title="이미 가입된 계정이 있습니다">
      <Notice tone="info" title="이 계정에 소셜 로그인을 연결할까요?">
        <AccountSummary email={result.accountEmail} phone={result.accountPhone} />
        <br />
        연결하면 다음부터 이 소셜 계정으로도 같은 계정에 로그인됩니다.
      </Notice>

      {link.error && (
        <div style={{ marginTop: 12 }}>
          <Notice tone="error">
            {link.error instanceof ApiError ? link.error.message : '연결하지 못했습니다.'}
          </Notice>
        </div>
      )}

      <div className={styles.actions} style={{ marginTop: 16 }}>
        <button
          type="button"
          className={styles.primary}
          disabled={link.isPending}
          onClick={() => link.mutate(result.signUpToken ?? '', { onSuccess: onLinked })}
        >
          {link.isPending ? '연결 중…' : '연결하기'}
        </button>
        <button type="button" className={styles.ghost} disabled={link.isPending} onClick={onCancel}>
          취소
        </button>
      </div>
    </Shell>
  );
}

/**
 * 새 비밀번호 — 비밀번호 찾기와 "이미 가입된 계정"이 같이 쓴다.
 *
 * <p>임시 비밀번호를 문자로 보내지 않는다. 문자는 단말에 평문으로 남고 잠금화면에 드러난다.
 * 본인확인이 끝났으므로 그 자리에서 정한다. 재설정 권한은 5분이다.
 */
function NewPasswordForm({
  grant,
  onDone,
  hideSummary,
}: {
  grant: PasswordResetGrant;
  onDone: () => void;
  hideSummary?: boolean;
}) {
  const reset = useResetPassword();
  const [password, setPassword] = useState('');
  const [passwordAgain, setPasswordAgain] = useState('');
  const mismatch = passwordAgain !== '' && password !== passwordAgain;

  return (
    <form
      onSubmit={(e) => {
        e.preventDefault();
        if (password === '' || password !== passwordAgain) return;
        reset.mutate({ resetToken: grant.resetToken, newPassword: password }, { onSuccess: onDone });
      }}
    >
      {!hideSummary && (
        <div className={styles.verified}>
          본인확인 완료 — <AccountSummary email={grant.accountEmail} phone={grant.accountPhone} />
        </div>
      )}

      {reset.error && (
        <div style={{ marginBottom: 16 }}>
          <Notice tone="error" title="바꾸지 못했습니다">
            {reset.error instanceof ApiError ? reset.error.message : '잠시 후 다시 시도해 주세요.'}
          </Notice>
        </div>
      )}

      <div className={styles.field}>
        <label className={styles.label} htmlFor="np-password">
          새 비밀번호
        </label>
        <PasswordInput
          id="np-password"
          inputClassName={styles.input}
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          autoComplete="new-password"
          required
        />
        <span className={styles.hint}>영문·숫자·특수문자를 모두 포함한 10자 이상.</span>
      </div>
      <div className={styles.field}>
        <label className={styles.label} htmlFor="np-password2">
          새 비밀번호 확인
        </label>
        <PasswordInput
          id="np-password2"
          inputClassName={styles.input}
          value={passwordAgain}
          onChange={(e) => setPasswordAgain(e.target.value)}
          autoComplete="new-password"
          required
        />
        {mismatch && <span className={styles.error}>비밀번호가 서로 다릅니다.</span>}
      </div>
      <div className={styles.actions}>
        <button
          type="submit"
          className={styles.primary}
          disabled={reset.isPending || password === '' || password !== passwordAgain}
        >
          {reset.isPending ? '바꾸는 중…' : '비밀번호 바꾸기'}
        </button>
      </div>
      <p className={styles.foot}>바꾸면 다른 기기에서는 모두 로그아웃됩니다.</p>
    </form>
  );
}

// =====================================================================
// 공통
// =====================================================================

/** 무엇에 연결되는지·무엇을 바꾸는지 사용자가 확인할 수 있게 가린 값을 보여준다. */
function AccountSummary({ email, phone }: { email?: string | null; phone?: string | null }) {
  return (
    <>
      {phone && <strong>{phone}</strong>}
      {phone && email && ' · '}
      {email && <strong>{email}</strong>}
      {!phone && !email && '가입된 계정'}
    </>
  );
}

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
        : '본인확인을 마치지 못했습니다';
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
