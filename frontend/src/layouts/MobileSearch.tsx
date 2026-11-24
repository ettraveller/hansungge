'use client';

import Form from 'next/form';
import { usePathname, useSearchParams } from 'next/navigation';
import { useEffect, useRef, useState } from 'react';

import { SearchIcon } from '@/layouts/icons';
import styles from './ShopLayout.module.css';

/**
 * 좁은 화면의 상품 검색 — 헤더 오른쪽의 돋보기를 누르면 헤더 아래로 검색줄이 편다.
 *
 * <p>좁은 화면에서는 헤더의 검색창을 감추는데, 대신할 것이 없어서 휴대폰으로는
 * 상품을 검색할 길이 없었다 (2026-11-23 QA-092).
 *
 * <h2>검색줄은 헤더 위에 뜬다 (자리를 밀지 않는다)</h2>
 * 헤더는 화면 위에 붙어 다닌다. 그 안에 줄을 끼워 높이를 바꾸면 아래 본문이 통째로
 * 밀렸다 돌아온다. 헤더 바로 아래에 겹쳐 띄우면 아무것도 움직이지 않는다.
 *
 * <h2>폼은 그대로 폼이다</h2>
 * 넓은 화면의 검색창과 같은 {@code GET /shop?keyword=} 다 (next/form — basePath 가 붙는다). 주소가 곧 검색 결과라
 * 뒤로가기와 공유가 그대로 된다.
 */
export function MobileSearch() {
  const [open, setOpen] = useState(false);
  const input = useRef<HTMLInputElement>(null);
  const pathname = usePathname();
  const keyword = useSearchParams().get('keyword') ?? '';

  // 화면을 옮기면(검색을 보냈거나 다른 곳으로 갔으면) 접는다. 렌더 중에 보정한다 —
  // effect 로 하면 새 화면이 한 번 그려진 뒤에 접혀 깜빡인다.
  const here = `${pathname}?${keyword}`;
  const [last, setLast] = useState(here);
  if (here !== last) {
    setLast(here);
    setOpen(false);
  }

  // 펴면 바로 칠 수 있게 한다. 한 번 더 눌러야 하면 검색이 두 번 누르는 일이 된다.
  useEffect(() => {
    if (open) input.current?.focus();
  }, [open]);

  return (
    <>
      <button
        type="button"
        className={styles.mobileSearchToggle}
        aria-label={open ? '검색 닫기' : '상품 검색'}
        aria-expanded={open}
        aria-controls="mobile-search"
        onClick={() => setOpen((v) => !v)}
      >
        <SearchIcon size={22} />
      </button>

      {open && (
        <Form
          id="mobile-search"
          className={styles.mobileSearchBar}
          action="/shop"
          role="search"
          onKeyDown={(e) => {
            if (e.key === 'Escape') setOpen(false);
          }}
        >
          <input
            ref={input}
            type="search"
            name="keyword"
            defaultValue={keyword}
            placeholder="상품 검색"
            aria-label="상품 검색"
            enterKeyHint="search"
          />
          <button type="submit">검색</button>
        </Form>
      )}
    </>
  );
}
