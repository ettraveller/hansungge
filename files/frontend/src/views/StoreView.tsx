'use client';

import { useMemo, useState } from 'react';
import { NaverMap } from '@/components/store/NaverMap';
import { SectionHead } from '@/components/ui/SectionHead';
import {
  ALL_BRANCHES,
  REGIONS,
  TOTAL_BRANCHES,
  naverDirectionsUrl,
  naverPlaceUrl,
  type FlatBranch,
} from '@/data/branches';
import styles from './StoreView.module.css';

/** 검색 대상. 레퍼런스와 같이 지점명/주소를 나눠 고를 수 있게 한다. */
type SearchField = 'all' | 'name' | 'address';

const ALL_REGIONS = '전체';

/**
 * 지점찾기 (`/store`).
 *
 * <p>레퍼런스(한국공인금거래소 지점안내)의 구성을 그대로 따른다.
 *
 * <pre>
 * 01. 주소/지점명 검색   ← 검색 대상 선택 + 검색어
 * 02. 지역별 지점안내     ← 지역 칩 (지점 수 표시)
 *     지점 목록          ← 선택하면 지도가 그 지점으로 바뀐다
 * </pre>
 *
 * <h2>클라이언트 컴포넌트지만 목록은 서버 HTML 에 들어 있다</h2>
 * 검색·필터 상태 때문에 클라이언트지만, 데이터는 빌드에 포함된 상수라 초기 HTML 에
 * 지점명과 주소가 그대로 실린다. <b>"○○ 지역 금거래소"는 이 업종에서 실제로 검색되는
 * 말이다</b> — 목록이 스크립트로만 그려지면 그 검색에 걸리지 않는다.
 *
 * <h2>지점이 하나여도 이 구조를 쓴다</h2>
 * 지금은 다산점 하나뿐이라 검색·필터가 과해 보인다. 다만 지점은 늘어날 예정이고,
 * 늘어날 때 <b>데이터만 추가하면</b> 화면은 그대로 동작한다.
 */
export function StoreView() {
  const [field, setField] = useState<SearchField>('all');
  const [keyword, setKeyword] = useState('');
  const [region, setRegion] = useState<string>(ALL_REGIONS);
  const [selected, setSelected] = useState<FlatBranch>(ALL_BRANCHES[0]);

  const results = useMemo(() => {
    const q = keyword.trim().toLowerCase();
    return ALL_BRANCHES.filter((b) => {
      if (region !== ALL_REGIONS && b.region !== region) return false;
      if (!q) return true;
      const name = b.name.toLowerCase();
      const address = b.address.toLowerCase();
      if (field === 'name') return name.includes(q);
      if (field === 'address') return address.includes(q);
      return name.includes(q) || address.includes(q);
    });
  }, [field, keyword, region]);

  return (
    <div className={styles.page}>
      <div className="wrap">
        <SectionHead
          as="h1"
          title="지점찾기"
          desc="가까운 지점에서 실물 확인과 매입 상담을 받으실 수 있습니다"
        />

        {/* ---------- 01. 검색 ---------- */}
        <section className={styles.block} aria-labelledby="store-search">
          <h2 id="store-search" className={styles.blockTitle}>
            <span className={styles.num}>01</span>
            주소 / 지점명 검색
          </h2>

          <form className={styles.searchRow} role="search" onSubmit={(e) => e.preventDefault()}>
            <label className="sr-only" htmlFor="store-field">
              검색 대상
            </label>
            <select
              id="store-field"
              className={styles.select}
              value={field}
              onChange={(e) => setField(e.target.value as SearchField)}
            >
              <option value="all">전체</option>
              <option value="name">지점명</option>
              <option value="address">주소</option>
            </select>

            <label className="sr-only" htmlFor="store-keyword">
              검색어
            </label>
            <input
              id="store-keyword"
              className={styles.input}
              type="search"
              placeholder="지점명 또는 주소를 입력하세요"
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
            />

            {keyword && (
              <button type="button" className={styles.clear} onClick={() => setKeyword('')}>
                지우기
              </button>
            )}
          </form>
        </section>

        {/* ---------- 02. 지역별 ---------- */}
        <section className={styles.block} aria-labelledby="store-region">
          <h2 id="store-region" className={styles.blockTitle}>
            <span className={styles.num}>02</span>
            지역별 지점안내
          </h2>

          <div className={styles.chips}>
            <Chip
              label={ALL_REGIONS}
              count={TOTAL_BRANCHES}
              on={region === ALL_REGIONS}
              onClick={() => setRegion(ALL_REGIONS)}
            />
            {REGIONS.map((r) => (
              <Chip
                key={r.name}
                label={r.name}
                count={r.branches.length}
                on={region === r.name}
                onClick={() => setRegion(r.name)}
              />
            ))}
          </div>
        </section>

        {/* ---------- 목록 + 지도 ---------- */}
        <div className={styles.layout}>
          <div className={styles.listCol}>
            <p className={styles.count}>
              검색 결과 <strong className="tabular">{results.length}</strong>개
            </p>

            {results.length === 0 ? (
              <p className={styles.empty}>
                조건에 맞는 지점이 없습니다. 검색어나 지역을 바꿔 보세요.
              </p>
            ) : (
              <ul className={styles.list}>
                {results.map((b) => (
                  <li key={b.naverPlaceId}>
                    <button
                      type="button"
                      className={`${styles.item} ${
                        b.naverPlaceId === selected?.naverPlaceId ? styles.itemOn : ''
                      }`}
                      onClick={() => setSelected(b)}
                      aria-pressed={b.naverPlaceId === selected?.naverPlaceId}
                    >
                      <span className={styles.itemRegion}>{b.region}</span>
                      <span className={styles.itemName}>{b.name}</span>
                      <span className={styles.itemAddress}>{b.address}</span>
                      <span className={`${styles.itemTel} tabular`}>{b.tel}</span>
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </div>

          <div className={styles.mapCol}>
            {selected && (
              <>
                <NaverMap branch={selected} />

                <div className={styles.detail}>
                  <p className={styles.detailName}>{selected.name}</p>

                  <dl className={styles.detailRows}>
                    <div>
                      <dt>도로명</dt>
                      <dd>
                        {selected.address}
                        {selected.postalCode && (
                          <span className={`${styles.postal} tabular`}>
                            ({selected.postalCode})
                          </span>
                        )}
                      </dd>
                    </div>
                    {selected.addressJibun && (
                      <div>
                        <dt>지번</dt>
                        <dd>{selected.addressJibun}</dd>
                      </div>
                    )}
                    <div>
                      <dt>전화</dt>
                      <dd className="tabular">{selected.tel}</dd>
                    </div>
                    {selected.hours && (
                      <div>
                        <dt>영업시간</dt>
                        <dd>{selected.hours}</dd>
                      </div>
                    )}
                  </dl>

                  <div className={styles.actions}>
                    {/* 전화는 모바일에서 바로 걸린다 */}
                    <a className={styles.callBtn} href={`tel:${selected.tel.replace(/[^0-9]/g, '')}`}>
                      전화 걸기
                    </a>
                    <a
                      className={styles.mapBtn}
                      href={naverDirectionsUrl(selected)}
                      target="_blank"
                      rel="noopener noreferrer"
                    >
                      길찾기
                    </a>
                    <a
                      className={styles.mapBtn}
                      href={naverPlaceUrl(selected)}
                      target="_blank"
                      rel="noopener noreferrer"
                    >
                      상세보기
                    </a>
                  </div>
                </div>
              </>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

function Chip({
  label,
  count,
  on,
  onClick,
}: {
  label: string;
  count: number;
  on: boolean;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      className={`${styles.chip} ${on ? styles.chipOn : ''}`}
      onClick={onClick}
      aria-pressed={on}
    >
      {label}
      <span className={`${styles.chipCount} tabular`}>{count}</span>
    </button>
  );
}
