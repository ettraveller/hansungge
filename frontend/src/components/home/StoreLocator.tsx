import type { Region } from '@/data/branches';
import styles from './StoreLocator.module.css';

/**
 * 지점안내 — 지역별 아코디언 (레퍼런스와 같은 형태).
 *
 * <h2>JS 를 쓰지 않았다</h2>
 * {@code <details>/<summary>} 가 펼침/접힘을 브라우저 기본 동작으로 처리한다.
 * 키보드 조작·스크린리더 지원이 공짜로 따라오고, <b>접힌 내용도 초기 HTML 에 들어 있어</b>
 * 검색엔진이 지점 주소를 읽는다. "○○ 지역 금거래소"는 이 업종에서 실제로 검색되는 말이다.
 *
 * <p>지점 데이터는 아직 하드코딩이다. 관리자 화면(STEP 15~16)에서 관리하게 되면
 * 이 컴포넌트는 props 만 서버 데이터로 바뀐다.
 */
export function StoreLocator({ regions }: { regions: Region[] }) {
  return (
    <div className={styles.list}>
      {regions.map((region, index) => (
        <details key={region.name} className={styles.region} open={index === 0}>
          <summary className={styles.summary}>
            <span className={styles.regionName}>{region.name}</span>
            <span className={styles.count}>{region.branches.length}개 지점</span>
            <span className={styles.caret} aria-hidden="true" />
          </summary>

          <ul className={styles.branches}>
            {region.branches.map((branch) => (
              <li key={branch.name} className={styles.branch}>
                <p className={styles.branchName}>{branch.name}</p>
                <p className={styles.address}>{branch.address}</p>
                <a href={`tel:${branch.tel.replace(/-/g, '')}`} className={styles.tel}>
                  {branch.tel}
                </a>
              </li>
            ))}
          </ul>
        </details>
      ))}
    </div>
  );
}
