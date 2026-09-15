'use client';

import { TextInput } from '@/components/admin/Field';
import styles from './otp.module.css';

export const OTP_DIGITS = 6;

/** 복구 코드는 8글자 + 가운데 하이픈 */
const RECOVERY_MAX = 9;

/**
 * 6자리 입력칸.
 *
 * <p>휴대폰에서는 숫자 자판이 뜨고, 문자로 받은 코드처럼 자동 채움 제안이 뜨게
 * {@code autoComplete="one-time-code"} 를 준다. 숫자 말고 다른 글자는 받지 않는다 —
 * 인증 앱에서 복사하면 "123 456" 처럼 공백이 섞여 온다.
 *
 * <p>{@code recovery} 면 복구 코드({@code ABCD-EFGH})를 받는다.
 */
export function OtpCodeInput({
  id,
  value,
  onChange,
  onComplete,
  recovery = false,
  autoFocus,
  invalid,
  disabled,
}: {
  id: string;
  value: string;
  onChange: (value: string) => void;
  /** 6자리를 다 채운 순간 한 번 불린다. 복구 코드에는 부르지 않는다 */
  onComplete?: (code: string) => void;
  recovery?: boolean;
  autoFocus?: boolean;
  invalid?: boolean;
  disabled?: boolean;
}) {
  const handle = (raw: string) => {
    if (recovery) {
      onChange(raw.toUpperCase().replace(/[^A-Z0-9-]/g, '').slice(0, RECOVERY_MAX));
      return;
    }
    const digits = raw.replace(/\D/g, '').slice(0, OTP_DIGITS);
    onChange(digits);
    if (digits.length === OTP_DIGITS && value.length !== OTP_DIGITS) onComplete?.(digits);
  };

  return (
    <TextInput
      id={id}
      type="text"
      inputMode={recovery ? 'text' : 'numeric'}
      autoComplete={recovery ? 'off' : 'one-time-code'}
      autoCapitalize={recovery ? 'characters' : 'off'}
      spellCheck={false}
      autoFocus={autoFocus}
      disabled={disabled}
      invalid={invalid}
      placeholder={recovery ? 'ABCD-EFGH' : '000000'}
      aria-label={recovery ? '복구 코드' : '인증 코드 6자리'}
      className={[styles.codeInput, recovery ? styles.codeInputRecovery : ''].join(' ')}
      value={value}
      onChange={(e) => handle(e.target.value)}
    />
  );
}

export function isCompleteOtp(code: string, recovery = false): boolean {
  return recovery ? code.replace(/-/g, '').length === 8 : code.length === OTP_DIGITS;
}
