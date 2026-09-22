'use client';

import Link from 'next/link';

import { Notice } from '@/components/auth/Notice';
import { useAuthProviders, useStartPasswordReset } from '@/hooks/useUserSession';
import { pendingIdentity, submitIdentityForm } from '@/utils/identityReturn';
import { ApiError } from '@/types/api';
import styles from '@/components/auth/auth.module.css';

/**
 * 비밀번호 찾기 (`/password/reset`) — 간편인증 → 그 자리에서 새 비밀번호.
 *
 * <p>이메일로 하지 않는다. 이메일은 선택 항목이라 없는 회원이 있다. 본인확인은 모든 회원이
 * 가입 때 거쳤으므로 누구나 할 수 있고, 번호를 바꾼 사람이면 여기서 로그인 번호도 새 번호로
 * 맞춰진다.
 *
 * <p>계정 선택 화면이 없다 — 같은 사람의 계정이 여럿이어도 휴대폰 로그인이 되는 쪽 하나를 서버가 고른다.
 * 본인인증을 받은 적 없는 소셜 가입자는 찾을 수 없다 — 소셜로 로그인하면 된다.
 *
 * <p>비밀번호만 잊었고 휴대폰은 그대로라면 <b>인증번호 로그인</b>이 더 빠르다(본인확인은 건당
 * 과금). 그래서 그 길을 먼저 안내한다.
 */
export function PasswordResetView() {
  const start = useStartPasswordReset();
  const { data: config } = useAuthProviders();

  const begin = () =>
    start.mutate(undefined, {
      onSuccess: (res) => {
        pendingIdentity.save({ token: res.token, flow: 'reset', next: '/login' });
        submitIdentityForm(res.form);
      },
    });

  return (
    <div className={styles.page}>
      <header className={styles.head}>
        <h1 className={styles.title}>비밀번호 찾기</h1>
        <p className={styles.lead}>
          본인인증(PASS·토스 등)으로 가입한 계정을 찾고, 그 자리에서 새 비밀번호를 정합니다.
        </p>
      </header>

      <div className={styles.card}>
        {config?.smsLoginAvailable && (
          <div style={{ marginBottom: 16 }}>
            <Notice tone="info" title="휴대폰번호가 그대로라면">
              <Link href="/login/sms">인증번호로 로그인</Link>한 뒤 마이페이지에서 비밀번호를
              바꾸는 편이 더 빠릅니다.
            </Notice>
          </div>
        )}

        {start.error && (
          <div style={{ marginBottom: 16 }}>
            <Notice tone="error" title="본인인증을 시작하지 못했습니다">
              {start.error instanceof ApiError ? start.error.message : '잠시 후 다시 시도해 주세요.'}
            </Notice>
          </div>
        )}

        <div className={styles.actions}>
          <button type="button" className={styles.primary} disabled={start.isPending} onClick={begin}>
            {start.isPending ? '본인인증으로 이동 중…' : '본인인증하기'}
          </button>
        </div>

        <p className={styles.foot}>
          임시 비밀번호를 문자로 보내지 않습니다. 새 비밀번호를 정하면 다른 기기에서는 모두
          로그아웃됩니다.
          <br />
          <Link href="/login">로그인 화면으로</Link>
        </p>
      </div>
    </div>
  );
}
