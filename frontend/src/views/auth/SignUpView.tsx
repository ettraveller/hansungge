'use client';

import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { useState, useSyncExternalStore } from 'react';

import { AgreementChecklist } from '@/components/auth/AgreementChecklist';
import { Notice } from '@/components/auth/Notice';
import { useAuthProviders, useStartIdentity, useStartSignUp } from '@/hooks/useUserSession';
import { pendingSignUp, safeNext, type PendingSignUp } from '@/utils/authStorage';
import { pendingIdentity, submitIdentityForm } from '@/utils/identityReturn';
import { ApiError } from '@/types/api';
import type { AgreementCode } from '@/types/user';
import styles from '@/components/auth/auth.module.css';

/**
 * 회원가입 1단계 — 약관 동의 → 휴대폰 본인확인.
 *
 * <p>순서가 약관 먼저인 이유: 본인확인은 이름·생년월일·휴대폰을 받는 <b>개인정보 수집</b>이다.
 * 수집 전에 동의를 받아야 한다. 본인확인 창에 뜨는 약관은 통신사·인증기관의 것이지 우리
 * 서비스 약관이 아니다.
 *
 * <p>두 경로가 이 화면으로 온다.
 * <ul>
 *   <li>휴대폰으로 직접 가입 — 로그인 화면의 [회원가입]
 *   <li>처음 오는 소셜 계정 — 소셜 콜백이 진행 토큰을 남기고 여기로 보낸다
 * </ul>
 *
 * <p>이름·생년월일·휴대폰 입력칸이 <b>없다.</b> 본인확인 값을 그대로 쓴다 — 직접 입력하게 두면
 * 매입 대금을 보낼 때 예금주와 어긋난다. 이미 가입한 사람인지는 본인확인 직후에 알려준다.
 */
export function SignUpView() {
  const searchParams = useSearchParams();

  /**
   * 소셜 콜백이 남긴 진행 정보. sessionStorage 라 서버·하이드레이션 단계에서는 undefined
   * (= 아직 모름), 그 뒤에 실제 값이다. {@code useSyncExternalStore} 가 그 순서를 지킨다.
   */
  const social = useSyncExternalStore<PendingSignUp | null | undefined>(
    pendingSignUp.subscribe,
    pendingSignUp.read,
    () => undefined,
  );
  const { data: config, isLoading } = useAuthProviders();

  const startSignUp = useStartSignUp();
  const startIdentity = useStartIdentity();
  const [checked, setChecked] = useState<Record<string, boolean>>({});

  const next = social?.next ?? safeNext(searchParams.get('next'));
  const agreements = social?.agreements ?? config?.agreements ?? [];
  const failure = startSignUp.error ?? startIdentity.error;
  const busy = startSignUp.isPending || startIdentity.isPending;

  if (social === undefined || isLoading) {
    return (
      <div className={styles.page}>
        <p className={styles.loading}>불러오는 중…</p>
      </div>
    );
  }

  if (config && !config.signUpAvailable) {
    return (
      <div className={styles.page}>
        <header className={styles.head}>
          <h1 className={styles.title}>회원가입</h1>
        </header>
        <div className={styles.card}>
          <Notice tone="warn" title="지금은 새로 가입할 수 없습니다">
            휴대폰 본인확인 연동을 준비하고 있습니다. 이미 가입하신 분은 로그인할 수 있습니다.
          </Notice>
          <p className={styles.foot}>
            <Link href="/login">로그인 화면으로</Link>
          </p>
        </div>
      </div>
    );
  }

  const missingRequired = agreements.filter((a) => a.required && !checked[a.code]).map((a) => a.name);
  const canSubmit = agreements.length > 0 && missingRequired.length === 0 && !busy;

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!canSubmit) return;
    startSignUp.mutate(
      {
        // 체크하지 않은 선택 약관도 false 로 함께 보낸다 —
        // "묻지 않은 것"과 "거절한 것"은 나중에 구분되어야 한다.
        agreements: agreements.map((a) => ({
          type: a.code as AgreementCode,
          agreed: Boolean(checked[a.code]),
        })),
        signUpToken: social?.signUpToken,
      },
      {
        onSuccess: ({ signUpToken }) =>
          startIdentity.mutate(signUpToken, {
            onSuccess: (res) => {
              // 우리 화면을 떠난다. 돌아왔을 때 이어갈 것을 남기고, 소셜 진행 정보는
              // 이제 진행 토큰 안에 들어 있으니 지운다.
              pendingIdentity.save({ token: res.token, flow: 'signup', next });
              pendingSignUp.clear();
              submitIdentityForm(res.form);
            },
          }),
      },
    );
  };

  return (
    <div className={styles.page}>
      <header className={styles.head}>
        <h1 className={styles.title}>회원가입</h1>
        <p className={styles.lead}>
          약관에 동의한 뒤 휴대폰 본인확인을 진행합니다.
          <br />
          이름·생년월일·휴대폰번호는 본인확인 결과로 채워지며 직접 입력하지 않습니다.
        </p>
      </header>

      <form className={styles.card} onSubmit={submit}>
        {social?.suggestedEmail && (
          <div style={{ marginBottom: 16 }}>
            <Notice tone="info">
              <strong>{social.suggestedEmail}</strong> 소셜 계정으로 가입을 진행합니다.
            </Notice>
          </div>
        )}

        {failure && (
          <div style={{ marginBottom: 16 }}>
            <Notice tone="error" title="진행하지 못했습니다">
              {failure instanceof ApiError ? failure.message : '잠시 후 다시 시도해 주세요.'}
              {failure instanceof ApiError && failure.fieldErrors && (
                <ul style={{ marginTop: 6, paddingLeft: 16 }}>
                  {Object.entries(failure.fieldErrors).map(([key, message]) => (
                    <li key={key}>{message}</li>
                  ))}
                </ul>
              )}
            </Notice>
          </div>
        )}

        <h2 className={styles.sectionTitle}>약관 동의</h2>
        <p className={styles.sectionNote}>
          필수 항목에 모두 동의해야 가입할 수 있습니다. 선택 항목은 나중에 마이페이지에서 바꿀 수
          있습니다.
        </p>

        <AgreementChecklist
          agreements={agreements}
          checked={checked}
          disabled={busy}
          onChange={setChecked}
        />

        <div style={{ marginTop: 20 }}>
          <button type="submit" className={styles.primary} disabled={!canSubmit}>
            {busy ? '본인확인으로 이동 중…' : '동의하고 휴대폰 본인확인'}
          </button>
          {missingRequired.length > 0 && (
            <p className={styles.error} style={{ marginTop: 8 }}>
              필수 항목이 남았습니다: {missingRequired.join(', ')}
            </p>
          )}
        </div>

        <p className={styles.foot}>
          만 14세 미만은 가입할 수 없습니다. 이미 가입하셨다면 본인확인 뒤 기존 계정으로
          안내합니다.
          <br />
          이미 회원이신가요? <Link href="/login">로그인</Link>
        </p>
      </form>
    </div>
  );
}
