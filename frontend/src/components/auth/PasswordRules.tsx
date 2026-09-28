'use client';

import styles from '@/components/auth/auth.module.css';

/**
 * 비밀번호 규칙을 치는 동안 바로 알려준다.
 *
 * <h2>왜 만드나</h2>
 * 전에는 "영문·숫자·특수문자를 모두 포함한 10자 이상"이라는 <b>한 줄 안내</b>뿐이었다.
 * 그래서 어디가 모자란지는 <b>제출해서 서버가 거절해야</b> 알 수 있었다 — 가입 마지막
 * 단계에서 그 일이 생기면 가입 토큰(15분)을 그만큼 까먹는다.
 *
 * <h2>빨강으로 몰아세우지 않는다</h2>
 * 아직 못 채운 조건은 <b>회색</b>이고 채운 것만 초록으로 바뀐다. 두 글자 쳤을 때
 * "10자 이상 아님"을 빨갛게 띄우는 것은 사실이지만 도움이 되지 않는다 — 아직 치는 중이다.
 * 빨강은 <b>정말 틀린 것</b>(72자 초과)에만 쓴다.
 *
 * <h2>규칙은 서버와 같아야 한다</h2>
 * 서버의 {@code UserPasswordPolicy.PATTERN} 과 같은 조건이다. 둘이 갈라지면 화면은
 * 통과라고 하는데 서버가 거절하는, 사용자가 이유를 알 수 없는 상태가 된다.
 */

/** BCrypt 가 72바이트를 넘는 입력을 조용히 잘라내므로 서버가 막는 상한. */
export const PASSWORD_MAX_LENGTH = 72;

const RULES = [
  { key: 'len', label: '10자 이상', test: (v: string) => v.length >= 10 },
  { key: 'alpha', label: '영문', test: (v: string) => /[A-Za-z]/.test(v) },
  { key: 'digit', label: '숫자', test: (v: string) => /\d/.test(v) },
  { key: 'symbol', label: '특수문자', test: (v: string) => /[^A-Za-z0-9]/.test(v) },
] as const;

/** 서버가 받아 줄 값인지. 제출 버튼을 여는 판단에 쓴다. */
export function isValidPassword(value: string): boolean {
  return value.length <= PASSWORD_MAX_LENGTH && RULES.every((rule) => rule.test(value));
}

export function PasswordRules({ value, id }: { value: string; id?: string }) {
  const tooLong = value.length > PASSWORD_MAX_LENGTH;
  const met = RULES.filter((rule) => rule.test(value)).length;

  return (
    <div id={id}>
      <ul className={styles.rules}>
        {RULES.map((rule) => {
          const ok = rule.test(value);
          return (
            <li key={rule.key} className={ok ? styles.ruleOk : styles.rule}>
              {/* 모양만으로 알리지 않는다 — 낭독기는 옆의 숨은 글자를 읽는다 */}
              <span aria-hidden="true">{ok ? '✓' : '○'}</span>
              {rule.label}
              <span className="sr-only">{ok ? ' 충족' : ' 아직'}</span>
            </li>
          );
        })}
      </ul>

      {/* 글자마다 네 줄을 다시 읽어주면 시끄럽다. 개수만 한 줄로 알린다 */}
      <span className="sr-only" aria-live="polite">
        비밀번호 조건 {RULES.length}개 중 {met}개 충족
      </span>

      {tooLong && (
        <span className={styles.error}>
          {PASSWORD_MAX_LENGTH}자를 넘을 수 없습니다. (현재 {value.length}자)
        </span>
      )}
    </div>
  );
}
