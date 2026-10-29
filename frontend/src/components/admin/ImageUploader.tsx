'use client';

import { useId, useRef, useState } from 'react';

import { uploadMedia, type MediaFolder } from '@/api/admin/media';
import { ApiError } from '@/types/api';
import { mediaUrl } from '@/utils/media';
import styles from './ImageUploader.module.css';

/**
 * 이미지 업로드 — 파일 선택 · 드래그 · 붙여넣기.
 *
 * <h2>붙여넣기를 넣은 이유</h2>
 * 상품 사진은 상당수가 <b>화면 캡처</b>로 온다(거래소 시세표, 카톡으로 받은 사진).
 * 붙여넣기가 없으면 그 사람은 캡처를 파일로 저장한 뒤 그 파일을 찾아서 올려야 한다.
 * 클립보드에 이미 이미지가 있는데 두 단계를 더 걷는 셈이다.
 *
 * <h2>왜 키가 아니라 사진을 보여주는가</h2>
 * 이전 화면은 Object Storage 키를 <b>손으로 타이핑</b>하게 했다. 올바른 키를 적어도
 * 그 키가 어떤 사진인지 화면에서 확인할 방법이 없어서, 사실상 쓸 수 없는 입력이었다.
 *
 * <h2>상태의 주인</h2>
 * 키 목록은 <b>부모가 갖는다.</b> 이 컴포넌트는 올린 결과를 알려줄 뿐이고, 저장은
 * 부모의 "저장" 버튼이 한다 — 올리자마자 상품에 반영하면 실수로 올린 사진을
 * 되돌릴 수 없다.
 */
export function ImageUploader({
  value,
  onChange,
  folder = 'products',
  max = 10,
  disabled,
  emptyLabel = '사진을 끌어다 놓거나 클릭해서 선택하세요',
  preview = 'square',
}: {
  value: string[];
  onChange: (keys: string[]) => void;
  folder?: MediaFolder;
  /** 보관할 장수 상한. 1 이면 새로 올린 사진이 기존 것을 대체한다. */
  max?: number;
  disabled?: boolean;
  emptyLabel?: string;
  /**
   * 미리보기 모양.
   *
   * - `square` — 상품 사진. 화면에 정사각형으로 잘려 나가므로 잘림을 그대로 보여준다.
   * - `tall` — 상세페이지 이미지. 860×9000 처럼 세로로 긴 그림을 정사각형에 넣으면
   *   맨 위 한 조각만 보여서 어느 장이 어느 것인지 구분할 수 없다. 전체를 담아 보여준다.
   */
  preview?: 'square' | 'tall';
}) {
  const inputId = useId();
  const inputRef = useRef<HTMLInputElement>(null);
  const [busy, setBusy] = useState(false);
  const [dragging, setDragging] = useState(false);
  // 포커스를 받으면 "이제 Ctrl+V" 로 문구를 바꾼다. 붙여넣기는 눈에 보이지 않는
  // 동작이라, 준비가 됐다는 표시가 없으면 눌러도 되는지 알 수 없다.
  const [focused, setFocused] = useState(false);
  // 「교체」로 파일 창을 연 경우 그 자리 번호. null 이면 평소처럼 뒤에 덧붙인다.
  //
  // state 가 아니라 ref 다 — 파일 창이 열려 있는 동안 화면을 다시 그릴 이유가 없고,
  // 다시 그리면 그 사이 input 이 새로 만들어져 선택 결과를 놓칠 여지가 생긴다.
  const replaceIndexRef = useRef<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  const single = max === 1;
  const full = value.length >= max;

  async function upload(files: File[]) {
    if (disabled || files.length === 0) return;

    // 「교체」로 들어왔는가. 먼저 읽고 비운다 — 아래에서 중간에 빠져나가도
    // 다음 업로드가 교체로 잘못 처리되지 않아야 한다.
    const replaceTarget = replaceIndexRef.current;
    replaceIndexRef.current = null;

    // 이미지가 아닌 파일은 보내기 전에 걸러낸다. 서버도 같은 검사를 하지만(매직 넘버),
    // 폴더를 통째로 끌어다 놓은 경우 10MB 짜리 PDF 를 올려놓고 거절당하는 것보다
    // 여기서 조용히 빼는 쪽이 낫다.
    const images = files.filter((file) => file.type.startsWith('image/'));
    if (images.length === 0) {
      setError('이미지 파일만 올릴 수 있습니다.');
      return;
    }

    // 교체는 한 자리를 바꾸는 것이므로 여러 장을 골라도 첫 장만 쓴다.
    // 상한을 넘는 몫은 보내지 않는다 — 보내고 나서 버리면 그 파일들이 서버 디스크에
    // 올라간 뒤 아무 상품에도 연결되지 않은 채 남는다.
    const room = replaceTarget != null || single ? 1 : max - value.length;
    const picked = images.slice(0, room);
    const dropped = images.length - picked.length;

    setBusy(true);
    setError(null);
    try {
      const uploaded = await uploadMedia(picked, folder);
      const keys = uploaded.map((it) => it.objectKey);

      if (replaceTarget != null) {
        // 자리를 지킨다. 지우고 새로 올리면 맨 뒤로 붙어서 순서를 다시 맞춰야 하고,
        // 첫 장을 바꾸려던 경우에는 그 사이 대표 사진이 다른 것으로 바뀐다.
        onChange(value.map((old, i) => (i === replaceTarget ? keys[0] : old)));
      } else {
        onChange(single ? keys.slice(0, 1) : [...value, ...keys]);
      }

      if (dropped > 0 && replaceTarget == null) {
        setError(`${max}장까지만 올릴 수 있어 ${dropped}장은 올리지 않았습니다.`);
      }
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '업로드에 실패했습니다.');
    } finally {
      setBusy(false);
      // 같은 파일을 다시 선택할 수 있게 비운다 — 비우지 않으면 값이 바뀌지 않아
      // change 이벤트가 오지 않고, 실패 후 재시도가 먹지 않는 것처럼 보인다.
      if (inputRef.current) inputRef.current.value = '';
    }
  }

  /** 그 자리의 사진만 바꾼다. 파일을 고르면 {@link upload} 가 이 번호를 보고 처리한다. */
  function pickReplacement(index: number) {
    if (disabled) return;
    replaceIndexRef.current = index;
    inputRef.current?.click();
  }

  function move(from: number, to: number) {
    if (to < 0 || to >= value.length) return;
    const next = [...value];
    const [moved] = next.splice(from, 1);
    next.splice(to, 0, moved);
    onChange(next);
  }

  return (
    <div className={styles.root}>
      {/*
        붙여넣기는 <b>포커스된 요소</b>에서 일어난다. div 에 tabIndex 를 줘서
        클릭·탭으로 포커스를 받을 수 있게 하고, 거기에 onPaste 를 붙인다.
        window 에 전역으로 달면 화면에 업로더가 둘 있을 때 어느 쪽에 붙을지
        알 수 없어진다 — 대표이미지와 상세이미지가 한 화면에 있으므로 실제로 문제다.

        ⚠️ 여기를 눌러도 파일 선택창이 열리지 않는다. 열리게 두면 <b>붙여넣으려고
        클릭하는 순간 창이 뜬다</b> — 포커스를 주는 동작과 파일을 고르는 동작이 같은
        클릭에 묶여 있어서다. 파일 선택은 아래 버튼이 맡는다.
      */}
      <div
        className={[
          styles.drop,
          dragging ? styles.dropActive : '',
          focused ? styles.dropFocused : '',
          disabled ? styles.dropDisabled : '',
        ]
          .filter(Boolean)
          .join(' ')}
        tabIndex={disabled ? -1 : 0}
        aria-busy={busy}
        aria-label="이미지 붙여넣기 영역"
        onFocus={() => setFocused(true)}
        onBlur={() => setFocused(false)}
        onPaste={(e) => {
          const files = Array.from(e.clipboardData.files);
          if (files.length === 0) return;
          e.preventDefault();
          void upload(files);
        }}
        onDragOver={(e) => {
          if (disabled) return;
          e.preventDefault();
          setDragging(true);
        }}
        onDragLeave={() => setDragging(false)}
        onDrop={(e) => {
          if (disabled) return;
          e.preventDefault();
          setDragging(false);
          void upload(Array.from(e.dataTransfer.files));
        }}
      >
        <input
          ref={inputRef}
          id={inputId}
          type="file"
          accept="image/jpeg,image/png,image/webp,image/gif"
          multiple={!single}
          hidden
          disabled={disabled}
          onChange={(e) => void upload(Array.from(e.target.files ?? []))}
        />
        <p className={styles.dropTitle}>
          {busy ? '올리는 중…' : focused ? '이제 Ctrl+V 를 누르세요' : emptyLabel}
        </p>
        <p className={styles.dropHint}>
          {full && !single
            ? `${max}장을 모두 채웠습니다. 지우고 다시 올려 주세요.`
            : focused
              ? '캡처한 이미지가 그대로 올라갑니다. 끌어다 놓아도 됩니다.'
              : '끌어다 놓거나, 이 칸을 한 번 클릭한 뒤 Ctrl+V 로 붙여넣으세요.'}
        </p>

        {/*
          파일 선택은 버튼이 맡는다. 드롭존 전체를 누르면 열리게 두면 붙여넣으려고
          클릭하는 순간 파일 창이 떠서 붙여넣을 수가 없다.

          ⚠️ 버블링을 막는다. 막지 않으면 이 클릭이 드롭존까지 올라가 포커스가
          옮겨 다니고, 파일 창을 닫은 뒤 Ctrl+V 가 먹지 않는다.
        */}
        <button
          type="button"
          className={styles.pickButton}
          disabled={disabled || busy}
          onClick={(e) => {
            e.stopPropagation();
            inputRef.current?.click();
          }}
        >
          파일 선택
        </button>

        <p className={styles.dropMeta}>JPG · PNG · WEBP · GIF / 한 장 10MB 까지</p>
      </div>

      {error && <p className={styles.error}>{error}</p>}

      {value.length > 0 && (
        <ul className={single ? styles.single : styles.grid}>
          {value.map((key, index) => (
            // key 에 index 를 섞지 않는다. 같은 사진을 두 번 올리면 키가 같아지는데,
            // 그 경우에도 순서를 바꿀 때 React 가 엉뚱한 칸을 지우지 않게 하려면
            // 키가 위치를 담아야 한다.
            <li className={styles.item} key={`${key}-${index}`}>
              {/*
                next/image 를 쓰지 않는다. 그쪽은 허용 도메인을 next.config 에 미리
                등록해야 하고, 관리자 미리보기는 최적화가 필요한 자리가 아니다.
              */}
              {/* eslint-disable-next-line @next/next/no-img-element */}
              <img
                className={preview === 'tall' ? styles.thumbTall : styles.thumb}
                src={mediaUrl(key) ?? ''}
                alt=""
              />
              <div className={styles.itemBar}>
                {!single && (
                  <>
                    <button
                      type="button"
                      className={styles.iconButton}
                      disabled={disabled || index === 0}
                      title="앞으로"
                      aria-label="앞으로 옮기기"
                      onClick={() => move(index, index - 1)}
                    >
                      ←
                    </button>
                    <span className={styles.order}>{index + 1}</span>
                    <button
                      type="button"
                      className={styles.iconButton}
                      disabled={disabled || index === value.length - 1}
                      title="뒤로"
                      aria-label="뒤로 옮기기"
                      onClick={() => move(index, index + 1)}
                    >
                      →
                    </button>
                  </>
                )}
                {/*
                  자리를 지키며 바꾼다. 「삭제 후 다시 업로드」로도 되지만 새 사진이
                  맨 뒤에 붙어서 순서를 다시 맞춰야 하고, 첫 장을 바꾸려던 경우에는
                  그 사이 대표 사진이 다른 것으로 바뀐다.
                */}
                <button
                  type="button"
                  className={styles.iconButton}
                  disabled={disabled || busy}
                  title="이 자리의 사진만 다른 것으로 바꿉니다"
                  onClick={() => pickReplacement(index)}
                >
                  교체
                </button>
                <button
                  type="button"
                  className={styles.remove}
                  disabled={disabled}
                  onClick={() => onChange(value.filter((_, i) => i !== index))}
                >
                  삭제
                </button>
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
