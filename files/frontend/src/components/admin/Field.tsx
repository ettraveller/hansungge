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
