'use client';

import Link from 'next/link';

import type { AgreementCode, AgreementItem } from '@/types/user';
import styles from './auth.module.css';

/** 약관 전문 위치. 문서 페이지가 준비되면 여기만 바꾸면 된다. */
const DOCUMENT_PATH: Partial<Record<AgreementCode, string>> = {
  TERMS: '/terms',
  PRIVACY: '/privacy',
};

/**
 * 가입 약관 동의 목록.
 *
 * <p>"전체 동의"는 편의일 뿐이고, <b>필수와 선택을 한 덩어리로 묶지 않는다.</b>
 * 선택 항목(마케팅 수신)까지 한 번에 켜지는 UI 는 동의를 받았다고 보기 어렵다 —
 * 사용자가 무엇에 동의했는지 알 수 없는 동의는 나중에 분쟁의 근거가 된다.
 * 그래서 전체 동의를 눌러도 각 항목의 체크 상태가 그대로 보이고 개별로 끌 수 있다.
 */
export function AgreementChecklist({
  agreements,
  checked,
  onChange,
  disabled,
}: {
  agreements: AgreementItem[];
  checked: Record<string, boolean>;
  onChange: (next: Record<string, boolean>) => void;
  disabled?: boolean;
}) {
  const allChecked = agreements.length > 0 && agreements.every((a) => checked[a.code]);

  const toggleAll = (value: boolean) => {
    const next: Record<string, boolean> = {};
    agreements.forEach((a) => {
      next[a.code] = value;
    });
    onChange(next);
  };

  return (
    <div>
      <label className={styles.agreeAll}>
        <input
          type="checkbox"
          checked={allChecked}
          disabled={disabled}
          onChange={(e) => toggleAll(e.target.checked)}
        />
        전체 동의 (선택 항목 포함)
      </label>

      {agreements.map((agreement) => (
        <label key={agreement.code} className={styles.agreeItem}>
          <input
            type="checkbox"
            checked={Boolean(checked[agreement.code])}
            disabled={disabled}
            onChange={(e) => onChange({ ...checked, [agreement.code]: e.target.checked })}
          />
          <span className={styles.agreeText}>
            <span className={agreement.required ? styles.tagRequired : styles.tagOptional}>
              [{agreement.required ? '필수' : '선택'}]
            </span>
            {agreement.name}
            {DOCUMENT_PATH[agreement.code] && (
              <>
                {' '}
                <Link href={DOCUMENT_PATH[agreement.code] as string} target="_blank">
                  전문 보기
                </Link>
              </>
            )}
          </span>
        </label>
      ))}
    </div>
  );
}
