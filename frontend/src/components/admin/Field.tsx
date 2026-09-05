'use client';

import * as React from 'react';
import styles from './admin.module.css';

/**
 * 라벨 + 입력 + 도움말/오류 한 묶음.
 *
 * 오류 문구는 백엔드가 준 필드 오류를 그대로 보여준다 — 프론트엔드가 검증 규칙을
 * 따로 구현하면 두 규칙이 갈라져서 "화면은 통과, 서버는 거절"이 생긴다.
 */
export function Field({
  label,
  htmlFor,
  required,
  hint,
  error,
  children,
}: {
  label: React.ReactNode;
  htmlFor?: string;
  required?: boolean;
  hint?: React.ReactNode;
  error?: string | null;
  children: React.ReactNode;
}) {
  return (
    <div className={styles.field}>
      <label className={styles.label} htmlFor={htmlFor}>
        {label}
        {required && (
          <span className={styles.required} aria-hidden="true">
            *
          </span>
        )}
      </label>
      {children}
      {error ? (
        <p className={styles.error}>{error}</p>
      ) : (
        hint && <p className={styles.hint}>{hint}</p>
      )}
    </div>
  );
}

export function TextInput({
  invalid,
  className,
  ...props
}: React.InputHTMLAttributes<HTMLInputElement> & { invalid?: boolean }) {
  return (
    <input
      {...props}
      className={[styles.input, invalid ? styles.inputInvalid : '', className]
        .filter(Boolean)
        .join(' ')}
    />
  );
}

/**
 * 금액·중량 입력.
 *
 * type="text" + inputMode="decimal" 이다. type="number" 는 브라우저가 값을 숫자로
 * 정규화하면서 "1.0000" 같은 유효자릿수를 지워버리고, 휠 스크롤로 값이 바뀐다.
 * 서버에는 문자열 그대로 보내 BigDecimal 이 받게 한다.
 */
export function DecimalInput({
  invalid,
  className,
  ...props
}: React.InputHTMLAttributes<HTMLInputElement> & { invalid?: boolean }) {
  return (
    <input
      {...props}
      type="text"
      inputMode="decimal"
      autoComplete="off"
      className={[styles.input, styles.numInput, invalid ? styles.inputInvalid : '', className]
        .filter(Boolean)
        .join(' ')}
    />
  );
}

export function SelectInput({
  invalid,
  className,
  children,
  ...props
}: React.SelectHTMLAttributes<HTMLSelectElement> & { invalid?: boolean }) {
  return (
    <select
      {...props}
      className={[styles.select, invalid ? styles.inputInvalid : '', className]
        .filter(Boolean)
        .join(' ')}
    >
      {children}
    </select>
  );
}

export function TextArea({
  invalid,
  className,
  ...props
}: React.TextareaHTMLAttributes<HTMLTextAreaElement> & { invalid?: boolean }) {
  return (
    <textarea
      {...props}
      className={[styles.textarea, invalid ? styles.inputInvalid : '', className]
        .filter(Boolean)
        .join(' ')}
    />
  );
}

export function CheckboxRow({
  label,
  ...props
}: React.InputHTMLAttributes<HTMLInputElement> & { label: React.ReactNode }) {
  return (
    <label className={styles.checkRow}>
      <input type="checkbox" {...props} />
      {label}
    </label>
  );
}

/* ------------------------------------------------------------------ */

/**
 * 금액 입력 — 천 단위로 끊어 보여준다.
 *
 * <h2>왜 따로 두는가</h2>
 * {@link DecimalInput} 은 중량(g)·비율(%)·배수에도 쓰인다. 3.75g 이나 -7% 에 콤마를
 * 넣으면 오히려 읽기 나빠지므로, <b>금액에만</b> 이 컴포넌트를 쓴다.
 *
 * <h2>부모는 콤마 없는 값을 갖는다</h2>
 * 화면에만 콤마를 넣고, {@link onValueChange} 로는 {@code "147000"} 처럼 <b>원본</b>을
 * 돌려준다. 상태에 콤마가 섞이면 검증 정규식과 서버로 보내는 값을 전부 손봐야 하고,
 * 한 곳이라도 빠뜨리면 <b>1,470,000 원이 1 원으로 전송된다.</b>
 *
 * <h2>커서가 튀지 않게 한다</h2>
 * 콤마는 글자 수를 바꾸므로, 입력 위치를 그대로 두면 커서가 엉뚱한 자리로 간다.
 * 커서 <b>앞의 숫자 개수</b>는 콤마가 늘거나 줄어도 변하지 않는다 — 그 개수를 세어
 * 다시 잡는다. 가운데를 고칠 때 커서가 맨 뒤로 튀는 일이 없다.
 */
export function MoneyInput({
  value,
  onValueChange,
  invalid,
  className,
  ...props
}: Omit<React.InputHTMLAttributes<HTMLInputElement>, 'value' | 'onChange' | 'type'> & {
  /** 콤마가 없는 원본 문자열 */
  value: string;
  onValueChange: (raw: string) => void;
  invalid?: boolean;
}) {
  const ref = React.useRef<HTMLInputElement>(null);
  const caret = React.useRef<number | null>(null);

  // 값이 화면에 그려진 <b>뒤</b>에 커서를 잡아야 한다. useEffect 로 하면 한 프레임
  // 늦어 커서가 맨 뒤로 갔다가 돌아오는 것이 보인다.
  React.useLayoutEffect(() => {
    if (caret.current == null || !ref.current) return;
    ref.current.setSelectionRange(caret.current, caret.current);
    caret.current = null;
  });

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const typed = e.target.value;
    const at = e.target.selectionStart ?? typed.length;
    const digitsBefore = countDigits(typed.slice(0, at));
    const raw = sanitizeAmount(typed);
    caret.current = caretAfterDigits(groupThousands(raw), digitsBefore);
    onValueChange(raw);
  };

  return (
    <input
      {...props}
      ref={ref}
      type="text"
      inputMode="decimal"
      autoComplete="off"
      value={groupThousands(value)}
      onChange={handleChange}
      className={[styles.input, styles.numInput, invalid ? styles.inputInvalid : '', className]
        .filter(Boolean)
        .join(' ')}
    />
  );
}

/** 숫자·소수점·맨 앞 음수 부호만 남긴다. 정액 보정은 음수를 받는다 (-3000). */
function sanitizeAmount(input: string): string {
  const negative = input.trimStart().startsWith('-');
  const kept = input.replace(/[^\d.]/g, '');
  const [head, ...rest] = kept.split('.');
  // 소수점을 두 번 찍어도 하나만 남긴다
  const body = rest.length > 0 ? `${head}.${rest.join('')}` : head;
  return negative ? `-${body}` : body;
}

/** 정수부에만 콤마를 넣는다. 소수부에 넣으면 값이 달라 보인다. */
function groupThousands(raw: string): string {
  if (raw === '' || raw === '-') return raw;
  const negative = raw.startsWith('-');
  const [int, dec] = (negative ? raw.slice(1) : raw).split('.');
  const grouped = int.replace(/\B(?=(\d{3})+(?!\d))/g, ',');
  const joined = dec === undefined ? grouped : `${grouped}.${dec}`;
  return negative ? `-${joined}` : joined;
}

function countDigits(s: string): number {
  return (s.match(/\d/g) ?? []).length;
}

/** 앞에서부터 숫자를 n 개 지난 위치. 콤마 개수가 달라져도 같은 자리를 가리킨다. */
function caretAfterDigits(formatted: string, n: number): number {
  if (n <= 0) return formatted.startsWith('-') ? 1 : 0;
  let seen = 0;
  for (let i = 0; i < formatted.length; i += 1) {
    if (formatted[i] >= '0' && formatted[i] <= '9') {
      seen += 1;
      if (seen === n) return i + 1;
    }
  }
  return formatted.length;
}
