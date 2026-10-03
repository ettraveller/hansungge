'use client';

import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { useState, useSyncExternalStore } from 'react';

import { AgreementChecklist } from '@/components/auth/AgreementChecklist';
import { Notice } from '@/components/auth/Notice';
import {
  useAuthProviders,
  useSignUpSocial,
  useStartIdentity,
  useStartSignUp,
} from '@/hooks/useUserSession';
import { pendingSignUp, safeNext, type PendingSignUp } from '@/utils/authStorage';
import { pendingIdentity, submitIdentityForm } from '@/utils/identityReturn';
import { ApiError } from '@/types/api';
import type { AgreementCode, IdentityMethod } from '@/types/user';
import styles from '@/components/auth/auth.module.css';

/**
 * 회원가입 — 약관 동의.
 *
 * <p>두 경로가 이 화면으로 오고, <b>약관 다음이 다르다</b> (2026-09-21 개편).
 * <ul>
 *   <li><b>휴대폰으로 직접 가입</b>(로그인 화면의 [회원가입]) — 약관 → 간편인증(PASS·토스 등) →
 *       비밀번호. 약관이 먼저인 이유는 간편인증이 이름·생년월일·휴대폰을 받는 <b>개인정보 수집</b>이라서다
 *   <li><b>처음 오는 소셜 계정</b>(소셜 콜백이 진행 토큰을 남기고 여기로 보낸다) — 약관 동의로 가입이
 *       끝난다. 본인인증을 받지 않는다. 만 14세 확인은 필수 약관 동의로 갈음한다
 * </ul>
 *
 * <p>이름·생년월일·휴대폰 입력칸이 <b>없다.</b> 자체 가입은 간편인증 값을 그대로 쓴다 — 직접 입력하게
 * 두면 매입 대금을 보낼 때 예금주와 어긋난다. 이미 가입한 사람인지는 간편인증 직후에 알려준다.
 */
export function SignUpView() {
  const router = useRouter();
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

  /**
   * 어떻게 본인확인을 받을 것인가.
   *
   * <p>간편인증은 PASS·토스 <b>앱</b>이 깔려 있어야 한다. 그 앱이 없는 분을 인증창 앞에
   * 세워 두면 거기서 가입이 끝난다 — 되돌아올 길이 화면에 없기 때문이다.
   * 문자 인증은 휴대폰만 있으면 되므로, 고를 수 있게 둔다.
   */
  const [method, setMethod] = useState<IdentityMethod>('SIMPLE');
  const signUpSocial = useSignUpSocial();
  const [checked, setChecked] = useState<Record<string, boolean>>({});

  const next = social?.next ?? safeNext(searchParams.get('next'));
  const agreements = social?.agreements ?? config?.agreements ?? [];
  const failure = startSignUp.error ?? startIdentity.error ?? signUpSocial.error;
  const busy = startSignUp.isPending || startIdentity.isPending || signUpSocial.isPending;

  if (social === undefined || isLoading) {
    return (
      <div className={styles.page}>
        <p className={styles.loading}>불러오는 중…</p>
      </div>
    );
  }

  // 휴대폰 가입만 본인인증 공급자가 필요하다. 소셜 가입은 그대로 진행한다.
  if (!social && config && !config.signUpAvailable) {
    return (
      <div className={styles.page}>
        <header className={styles.head}>
          <h1 className={styles.title}>회원가입</h1>
        </header>
        <div className={styles.card}>
          <Notice tone="warn" title="지금은 휴대폰번호로 가입할 수 없습니다">
            본인인증 연동을 준비하고 있습니다. 로그인 화면에서 소셜 계정(네이버·카카오·구글)으로
            가입할 수 있습니다.
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

  // 체크하지 않은 선택 약관도 false 로 함께 보낸다 — "묻지 않은 것"과 "거절한 것"은 나중에 구분되어야 한다.
  const choices = () =>
    agreements.map((a) => ({ type: a.code as AgreementCode, agreed: Boolean(checked[a.code]) }));

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!canSubmit) return;

    if (social) {
      signUpSocial.mutate(
        { signUpToken: social.signUpToken, agreements: choices() },
        {
          onSuccess: () => {
            pendingSignUp.clear();
            router.replace(next);
          },
        },
      );
      return;
    }

    startSignUp.mutate(choices(), {
      onSuccess: ({ signUpToken }) =>
        startIdentity.mutate(
          { signUpToken, method },
          {
            onSuccess: (res) => {
              // 우리 화면을 떠난다. 돌아왔을 때 이어갈 것을 남긴다.
              pendingIdentity.save({ token: res.token, flow: 'signup', next });
              submitIdentityForm(res.form);
            },
          },
        ),
    });
  };

  return (
    <div className={styles.page}>
      <header className={styles.head}>
        <h1 className={styles.title}>회원가입</h1>
        <p className={styles.lead}>
          {social ? (
            <>약관에 동의하면 가입이 끝납니다.</>
          ) : (
            <>
              약관에 동의한 뒤 본인확인을 진행합니다.
              <br />
              이름·생년월일·휴대폰번호는 본인확인 결과로 채워지며 직접 입력하지 않습니다.
            </>
          )}
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

        {!social && (
          <fieldset className={styles.methods}>
            <legend className={styles.sectionTitle}>본인확인 방법</legend>

            <label className={method === 'SIMPLE' ? styles.methodOn : styles.method}>
              <input
                type="radio"
                name="identity-method"
                value="SIMPLE"
                checked={method === 'SIMPLE'}
                onChange={() => setMethod('SIMPLE')}
                disabled={busy}
              />
              <span>
                <strong>간편인증</strong>
                <span className={styles.methodNote}>
                  PASS · 토스 · 카카오 등 <b>앱</b>으로 확인합니다. 앱이 깔려 있어야 합니다.
                </span>
              </span>
            </label>

            <label className={method === 'PHONE' ? styles.methodOn : styles.method}>
              <input
                type="radio"
                name="identity-method"
                value="PHONE"
                checked={method === 'PHONE'}
                onChange={() => setMethod('PHONE')}
                disabled={busy}
              />
              <span>
                <strong>휴대폰 문자인증</strong>
                <span className={styles.methodNote}>
                  통신사가 보낸 <b>문자</b>의 인증번호를 입력합니다. 앱이 없어도 됩니다.
                </span>
              </span>
            </label>
          </fieldset>
        )}

        <div style={{ marginTop: 20 }}>
          <button type="submit" className={styles.primary} disabled={!canSubmit}>
            {social
              ? busy
                ? '가입 처리 중…'
                : '동의하고 가입하기'
              : busy
                ? '본인확인으로 이동 중…'
                : method === 'PHONE'
                  ? '동의하고 문자로 인증'
                  : '동의하고 간편인증'}
          </button>
          {missingRequired.length > 0 && (
            <p className={styles.error} style={{ marginTop: 8 }}>
              필수 항목이 남았습니다: {missingRequired.join(', ')}
            </p>
          )}
        </div>

        <p className={styles.foot}>
          만 14세 미만은 가입할 수 없습니다.
          {!social && ' 이미 가입하셨다면 본인인증 뒤 기존 계정으로 안내합니다.'}
          <br />
          이미 회원이신가요? <Link href="/login">로그인</Link>
        </p>
      </form>
    </div>
  );
}
