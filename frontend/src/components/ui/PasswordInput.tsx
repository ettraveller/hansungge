'use client';

import * as React from 'react';

import styles from './PasswordInput.module.css';

type Props = Omit<React.InputHTMLAttributes<HTMLInputElement>, 'type'> & {
  /** 입력칸에 붙일 클래스. 관리자(admin.module.css)와 쇼핑몰(auth.module.css)이 서로 다르다 */
  inputClassName?: string;
  /** 검증 실패 표시용 클래스. 위 클래스와 함께 붙는다 */
  invalidClassName?: string;
  invalid?: boolean;
};

/**
 * 비밀번호 입력 + 보기 토글.
 *
 * <h2>왜 필요한가</h2>
 * 관리자 비밀번호는 <b>영문·숫자·특수문자 10자 이상</b>이고 대개 생성기로 만든 값이다.
 * 그런 문자열을 가려진 칸에 손으로 옮겨 적으면 틀리기 쉬운데, 이 시스템은 <b>5회 틀리면
 * 계정이 잠기고 자동으로 풀리지 않는다.</b> 오타 한 번의 대가가 크므로 눈으로 확인할
 * 수단이 있어야 한다.
 *
 * <p>계정 생성·초기화 화면에서는 더 중요하다 — 저장되는 것이 해시라 그 값은
 * <b>그 화면을 벗어나면 다시 볼 수 없다.</b> 잘못 적어 두면 새 계정에 아무도 못 들어간다.
 *
 * <h2>기본은 가려 둔다</h2>
 * 어깨너머로 보이는 위험은 실재한다. 그래서 켜는 것은 <b>사용자가 매번 선택</b>하게 하고,
 * 상태를 저장하지 않는다 — 다음에 열면 다시 가려진 채로 시작한다.
 *
 * <h2>버튼이 폼을 제출하지 않게 한다</h2>
 * {@code type="button"} 이 없으면 form 안의 button 은 기본이 submit 이라, 눈 아이콘을
 * 누르는 순간 로그인이 시도된다 — 그것도 <b>비밀번호를 다 치기 전에.</b> 실패 횟수가
 * 올라가고, 5회면 잠긴다.
 */
export function PasswordInput({
  inputClassName,
  invalidClassName,
  invalid,
  ...props
}: Props) {
  const [shown, setShown] = React.useState(false);

  return (
    <div className={styles.wrap}>
      <input
        {...props}
        type={shown ? 'text' : 'password'}
        className={[inputClassName, invalid ? invalidClassName : '', styles.input]
          .filter(Boolean)
          .join(' ')}
      />
      <button
        type="button"
        className={styles.toggle}
        // 색·모양만으로 상태를 알리지 않는다. 낭독기는 이 라벨을 읽는다
        aria-label={shown ? '비밀번호 숨기기' : '비밀번호 표시'}
        aria-pressed={shown}
        // 탭 순서에서 비밀번호칸과 다음 칸 사이에 끼어들지 않게 한다.
        // 키보드로 폼을 채우는 사람에게 눈 아이콘은 지나가는 길목이 아니다
        tabIndex={-1}
        onClick={() => setShown((v) => !v)}
      >
        {shown ? <EyeOffIcon /> : <EyeIcon />}
      </button>
    </div>
  );
}

function EyeIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path
        d="M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12Z"
        stroke="currentColor"
        strokeWidth="1.5"
        strokeLinejoin="round"
      />
      <circle cx="12" cy="12" r="3" stroke="currentColor" strokeWidth="1.5" />
    </svg>
  );
}

function EyeOffIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path
        d="M2.5 12S6 5.5 12 5.5c1.7 0 3.2.5 4.4 1.2M21.5 12s-1.3 2.5-3.8 4.2M12 18.5c-6 0-9.5-6.5-9.5-6.5"
        stroke="currentColor"
        strokeWidth="1.5"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <path d="M9.9 9.9a3 3 0 0 0 4.2 4.2" stroke="currentColor" strokeWidth="1.5" />
      <path d="M4 20 20 4" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
    </svg>
  );
}
