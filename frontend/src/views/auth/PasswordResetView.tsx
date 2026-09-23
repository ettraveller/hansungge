'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useState } from 'react';

import { CodeVerifyForm, codeErrorTitle } from '@/components/auth/CodeVerifyForm';
import { NewPasswordForm } from '@/components/auth/NewPasswordForm';
import { Notice } from '@/components/auth/Notice';
import {
  useAuthProviders,
  useSendPasswordResetCode,
  useStartPasswordReset,
  useVerifyPasswordResetCode,
} from '@/hooks/useUserSession';
import { pendingIdentity, submitIdentityForm } from '@/utils/identityReturn';
import { ApiError } from '@/types/api';
import type { PasswordResetGrant, VerificationChannel } from '@/types/user';
import styles from '@/components/auth/auth.module.css';

/**
 * 비밀번호 찾기 (`/password/reset`) — 인증번호 또는 본인인증 → 그 자리에서 새 비밀번호.
 *
 * <p>수단이 셋이다 (2026-09-23).
 * <ul>
 *   <li><b>문자 인증번호</b> — 기본. 가입 때 본인인증으로 명의가 확인된 번호다
 *   <li><b>이메일 인증번호</b> — 가입할 때 인증한 주소가 있는 회원. 문자비가 들지 않는다
 *   <li><b>본인인증</b> — 휴대폰번호가 바뀌었거나 위 둘을 못 쓸 때. 번호도 새 번호로 맞춰진다
 * </ul>
 *
 * <p>이메일 경로가 상대적으로 약한 것(메일 계정이 털리면 같이 털린다)은 알고 받아들인다. 돈이
 * 나가는 일(매입 신청·정산계좌 등록)은 비밀번호가 아니라 <b>본인인증</b>을 따로 요구하고,
 * 비밀번호를 바꾸면 다른 기기 세션이 전부 끊긴다.
 *
 * <p>임시 비밀번호를 문자로 보내지 않는다 — 문자는 단말에 평문으로 남고 잠금화면에 드러난다.
 */
export function PasswordResetView() {
  const router = useRouter();
  const { data: config, isLoading } = useAuthProviders();
  const send = useSendPasswordResetCode();
  const verify = useVerifyPasswordResetCode();
  const startIdentity = useStartPasswordReset();

  /** 인증번호를 통과하면 그 자리에서 새 비밀번호를 받는다. */
  const [grant, setGrant] = useState<PasswordResetGrant | null>(null);

  const channels: VerificationChannel[] = [
    ...(config?.smsLoginAvailable ? (['SMS'] as const) : []),
    ...(config?.emailCodeAvailable ? (['EMAIL'] as const) : []),
  ];
  const identityAvailable = Boolean(config?.signUpAvailable);
  const failure = send.error ?? verify.error ?? startIdentity.error;

  const beginIdentity = () =>
    startIdentity.mutate(undefined, {
      onSuccess: (res) => {
        pendingIdentity.save({ token: res.token, flow: 'reset', next: '/login' });
        submitIdentityForm(res.form);
      },
    });

  if (isLoading) {
    return (
      <div className={styles.page}>
        <p className={styles.loading}>불러오는 중…</p>
      </div>
    );
  }

  if (grant) {
    return (
      <div className={styles.page}>
        <header className={styles.head}>
          <h1 className={styles.title}>새 비밀번호</h1>
        </header>
        <div className={styles.card}>
          <NewPasswordForm grant={grant} onDone={() => router.replace('/login?reason=reset')} />
        </div>
      </div>
    );
  }

  return (
    <div className={styles.page}>
      <header className={styles.head}>
        <h1 className={styles.title}>비밀번호 찾기</h1>
        <p className={styles.lead}>
          {channels.length > 0
            ? '가입한 휴대폰번호나 이메일로 인증번호를 받아 새 비밀번호를 정합니다.'
            : '본인인증(PASS·토스 등)으로 가입한 계정을 찾고, 그 자리에서 새 비밀번호를 정합니다.'}
        </p>
      </header>

      <div className={styles.card}>
        {failure && (
          <div style={{ marginBottom: 16 }}>
            <Notice tone="error" title={codeErrorTitle(failure, '진행하지 못했습니다')}>
              {failure instanceof ApiError ? failure.message : '잠시 후 다시 시도해 주세요.'}
            </Notice>
          </div>
        )}

        {channels.length > 0 ? (
          <CodeVerifyForm
            channels={channels}
            sending={send.isPending}
            verifying={verify.isPending}
            submitLabel="확인"
            sentNotice="가입된 곳이라면 인증번호를 보냈습니다. 5분 안에 입력해 주세요."
            onSend={(channel, target, onSent) => {
              verify.reset();
              send.mutate({ channel, target }, { onSuccess: onSent });
            }}
            onVerify={(channel, target, code) =>
              verify.mutate({ channel, target, code }, { onSuccess: setGrant })
            }
          />
        ) : (
          <Notice tone="warn" title="지금은 인증번호를 보낼 수 없습니다">
            문자·메일 발송 설정이 준비되지 않았습니다. 아래 본인인증으로 진행해 주세요.
          </Notice>
        )}

        {identityAvailable && (
          <div style={{ marginTop: channels.length > 0 ? 22 : 16 }}>
            {channels.length > 0 && <p className={styles.divider}>또는</p>}
            <p className={styles.sectionNote}>
              휴대폰번호가 바뀌었거나 위 방법을 쓸 수 없다면 본인인증으로 찾을 수 있습니다.
              인증한 번호가 새 로그인 번호가 됩니다.
            </p>
            <div className={styles.actions}>
              <button
                type="button"
                className={styles.ghost}
                disabled={startIdentity.isPending}
                onClick={beginIdentity}
              >
                {startIdentity.isPending ? '본인인증으로 이동 중…' : '본인인증으로 찾기'}
              </button>
            </div>
          </div>
        )}

        <p className={styles.foot}>
          임시 비밀번호를 문자로 보내지 않습니다. 새 비밀번호를 정하면 다른 기기에서는 모두
          로그아웃됩니다.
          <br />
          <Link href="/login">로그인 화면으로</Link>
          {channels.length > 0 && (
            <>
              {' · '}
              <Link href="/login/sms">인증번호로 로그인</Link>
            </>
          )}
        </p>
      </div>
    </div>
  );
}
