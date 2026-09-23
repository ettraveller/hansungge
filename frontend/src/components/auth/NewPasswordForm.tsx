'use client';

import { useState } from 'react';

import { Notice } from '@/components/auth/Notice';
import { PasswordInput } from '@/components/ui/PasswordInput';
import { useResetPassword } from '@/hooks/useUserSession';
import { ApiError } from '@/types/api';
import type { PasswordResetGrant } from '@/types/user';
import styles from '@/components/auth/auth.module.css';

/**
 * 새 비밀번호 — 비밀번호 찾기와 "이미 가입된 계정"이 같이 쓴다.
 *
 * <p>임시 비밀번호를 문자로 보내지 않는다. 문자는 단말에 평문으로 남고 잠금화면에 드러난다.
 * 본인인증이 끝났으므로 그 자리에서 정한다. 재설정 권한은 5분이다.
 */
export function NewPasswordForm({
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
          본인인증 완료 — <AccountSummary email={grant.accountEmail} phone={grant.accountPhone} />
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

/** 무엇에 연결되는지·무엇을 바꾸는지 사용자가 확인할 수 있게 가린 값을 보여준다. */
export function AccountSummary({ email, phone }: { email?: string | null; phone?: string | null }) {
  return (
    <>
      {phone && <strong>{phone}</strong>}
      {phone && email && ' · '}
      {email && <strong>{email}</strong>}
      {!phone && !email && '가입된 계정'}
    </>
  );
}
