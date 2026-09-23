'use client';

import { useEffect, useState } from 'react';

import { Notice } from '@/components/auth/Notice';
import { ApiError } from '@/types/api';
import type { VerificationChannel } from '@/types/user';
import styles from '@/components/auth/auth.module.css';

/** 재발송까지 기다릴 시간. 서버 쿨다운(60초)과 같아야 버튼이 서버보다 먼저 풀리지 않는다. */
const RESEND_SECONDS = 60;

const FIELD: Record<VerificationChannel, { label: string; placeholder: string; autoComplete: string }> = {
  SMS: { label: '휴대폰번호', placeholder: '010-1234-5678', autoComplete: 'tel' },
  EMAIL: { label: '이메일', placeholder: 'hong@example.com', autoComplete: 'email' },
};

/**
 * 인증번호 받기 → 입력 → 확인. 인증번호 로그인과 비밀번호 찾기가 함께 쓴다.
 *
 * <p>두 화면이 같은 규칙(5분·5회·재발송 60초)을 따르고 실수하는 지점도 같다 — 재발송 버튼이
 * 서버 쿨다운보다 먼저 열리는 것, 대상을 고친 뒤에도 옛 번호로 확인을 시도하는 것. 한곳에 둔다.
 *
 * <p><b>가입 여부는 화면에서도 알려주지 않는다.</b> 보낸 뒤 안내는 회원이든 아니든 같다 —
 * 서버도 같은 응답을 준다. "가입되지 않은 번호입니다"라고 알려주면 회원 명단을 훑을 수 있다.
 */
export function CodeVerifyForm({
  channels,
  sending,
  verifying,
  onSend,
  onVerify,
  submitLabel,
  sentNotice,
}: {
  /** 쓸 수 있는 방법. 하나뿐이면 선택 버튼을 그리지 않는다 */
  channels: VerificationChannel[];
  sending: boolean;
  verifying: boolean;
  onSend: (channel: VerificationChannel, target: string, onSent: () => void) => void;
  onVerify: (channel: VerificationChannel, target: string, code: string) => void;
  submitLabel: string;
  sentNotice: string;
}) {
  const [channel, setChannel] = useState<VerificationChannel>(channels[0] ?? 'SMS');
  const [target, setTarget] = useState('');
  const [code, setCode] = useState('');
  const [sent, setSent] = useState(false);
  const [remaining, setRemaining] = useState(0);

  // 재발송 카운트다운. 화면이 서버보다 먼저 "다시 받기"를 열면 429 만 받는다.
  useEffect(() => {
    if (remaining <= 0) return;
    const timer = setTimeout(() => setRemaining((s) => s - 1), 1000);
    return () => clearTimeout(timer);
  }, [remaining]);

  const request = () =>
    onSend(channel, target.trim(), () => {
      setSent(true);
      setCode('');
      setRemaining(RESEND_SECONDS);
    });

  const field = FIELD[channel];

  return (
    <form
      onSubmit={(e) => {
        e.preventDefault();
        if (!sent) {
          request();
          return;
        }
        onVerify(channel, target.trim(), code);
      }}
    >
      {channels.length > 1 && (
        <div className={styles.field}>
          <span className={styles.label}>인증번호 받을 방법</span>
          <div className={styles.choiceRow}>
            {channels.map((item) => (
              <button
                key={item}
                type="button"
                className={`${styles.choice} ${item === channel ? styles.choiceOn : ''}`}
                aria-pressed={item === channel}
                onClick={() => {
                  // 방법을 바꾸면 처음부터. 옛 번호로 확인을 시도하면 틀렸다는 응답만 받는다.
                  setChannel(item);
                  setTarget('');
                  setCode('');
                  setSent(false);
                  setRemaining(0);
                }}
              >
                {item === 'SMS' ? '문자' : '이메일'}
              </button>
            ))}
          </div>
        </div>
      )}

      <div className={styles.field}>
        <label className={styles.label} htmlFor="code-target">
          {field.label}
        </label>
        <input
          id="code-target"
          type={channel === 'SMS' ? 'tel' : 'email'}
          inputMode={channel === 'SMS' ? 'numeric' : 'email'}
          className={styles.input}
          value={target}
          onChange={(e) => {
            setTarget(e.target.value);
            setSent(false);
          }}
          placeholder={field.placeholder}
          autoComplete={field.autoComplete}
          required
        />
      </div>

      {sent && (
        <>
          <Notice tone="info">{sentNotice}</Notice>
          <div className={styles.field} style={{ marginTop: 16 }}>
            <label className={styles.label} htmlFor="code-value">
              인증번호
            </label>
            <input
              id="code-value"
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
            <button type="submit" className={styles.primary} disabled={verifying || code.length < 6}>
              {verifying ? '확인 중…' : submitLabel}
            </button>
            <button
              type="button"
              className={styles.ghost}
              disabled={sending || remaining > 0}
              onClick={request}
            >
              {remaining > 0 ? `다시 받기 (${remaining}초)` : '다시 받기'}
            </button>
          </>
        ) : (
          <button type="submit" className={styles.primary} disabled={sending || target.trim() === ''}>
            {sending ? '보내는 중…' : '인증번호 받기'}
          </button>
        )}
      </div>
    </form>
  );
}

/** 인증번호 관련 오류를 사용자 말로 옮긴다. 두 화면이 같은 오류를 받는다. */
export function codeErrorTitle(error: unknown, fallback: string): string {
  if (!(error instanceof ApiError)) return fallback;
  switch (error.code) {
    case 'VERIFICATION_CODE_INVALID':
      return '인증번호를 확인해 주세요';
    case 'VERIFICATION_ATTEMPTS_EXCEEDED':
      return '입력 횟수를 넘었습니다';
    case 'TOO_MANY_REQUESTS':
      return '잠시 후 다시 받아 주세요';
    case 'SMS_SEND_FAILED':
      return '문자를 보내지 못했습니다';
    case 'MAIL_SEND_FAILED':
      return '메일을 보내지 못했습니다';
    case 'INVALID_INPUT':
      return '입력한 값을 확인해 주세요';
    default:
      return fallback;
  }
}
