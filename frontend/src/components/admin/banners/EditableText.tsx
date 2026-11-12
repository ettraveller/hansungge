'use client';

import { useEffect, useRef } from 'react';

import styles from '../admin.module.css';

/**
 * 손님 화면 위에서 그 자리의 글자를 고치는 칸.
 *
 * <h2>왜 입력칸이 아니라 글자 자리인가</h2>
 * 입력칸을 늘어놓은 폼은 <b>고칠 때 보는 모양</b>과 <b>손님이 보는 모양</b>이 다르다.
 * 주소를 한 줄 늘렸을 때 푸터에서 줄이 어떻게 접히는지는 푸터 안에서만 보인다.
 * 그래서 진짜 푸터를 그리고 그 글자를 직접 고친다.
 *
 * <h2>React 와 contentEditable</h2>
 * 글자를 value 로 묶어 다시 그리면 <b>칠 때마다 커서가 맨 앞으로 튄다.</b> 그래서
 * 안쪽 글자는 React 가 아니라 이 칸이 직접 들고, 밖에서 온 값이 지금 적힌 것과 다를
 * 때만 덮어쓴다 — 치는 동안에는 둘이 같으므로 DOM 을 건드리지 않는다.
 */
export function EditableText({
  value,
  label,
  maxLength,
  onChange,
}: {
  value: string;
  /** 비어 있을 때 자리에 띄울 이름. 빈 칸은 눌러야 할 곳인지 보이지 않는다. */
  label: string;
  maxLength: number;
  onChange: (value: string) => void;
}) {
  const ref = useRef<HTMLSpanElement>(null);

  useEffect(() => {
    const el = ref.current;
    if (el && el.textContent !== value) {
      el.textContent = value;
    }
  }, [value]);

  return (
    <span
      ref={ref}
      role="textbox"
      tabIndex={0}
      aria-label={label}
      data-label={label}
      contentEditable
      suppressContentEditableWarning
      spellCheck={false}
      className={value ? styles.editable : `${styles.editable} ${styles.editableEmpty}`}
      onInput={(e) => onChange((e.currentTarget.textContent ?? '').slice(0, maxLength))}
      onPaste={(e) => {
        // 복사해 온 글자에 묻어 오는 서식과 줄바꿈을 떼어 낸다. 그대로 두면 푸터 한
        // 줄 안에 <b> 나 색이 섞여 들어가 손님 화면에서 그 줄만 다르게 보인다.
        e.preventDefault();
        const text = e.clipboardData.getData('text/plain').replace(/\s+/g, ' ');
        // execCommand 는 낡았지만, 커서 자리에 글자만 끼워 넣는 일을 모든 브라우저가
        // 아직 이것으로 한다. 되돌리기(Ctrl+Z)도 함께 따라온다.
        document.execCommand('insertText', false, text);
      }}
      onKeyDown={(e) => {
        // 한 줄짜리 자리다. Enter 로 줄을 늘리면 푸터 한 칸에 <div> 가 생겨 모양이 깨진다.
        if (e.key === 'Enter') {
          e.preventDefault();
          e.currentTarget.blur();
        }
      }}
    />
  );
}
