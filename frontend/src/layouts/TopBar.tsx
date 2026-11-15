import type { ReactNode } from 'react';

import type { FooterInfo } from '@/api/footer';
import { telHref } from '@/data/company';
import styles from './ShopLayout.module.css';

/**
 * 헤더 위의 가는 유틸 띠 — 좌측에 대표전화와 스마트스토어 링크 (2026-11-18).
 *
 * <p>값은 푸터와 같은 관리자 설정({@code GET /api/v1/footer})에서 온다. 대표전화를
 * 푸터에서 고치면 이 띠도 같이 바뀐다 — 같은 번호가 두 곳에 두 벌로 살지 않는다.
 *
 * <p>{@code renderText} 는 관리자 「메인 배너 & 푸터」 편집기가 꽂는다. 푸터와 같은
 * 방식 — 고치는 자리가 곧 손님이 보는 자리다.
 */
export function TopBar({
  info,
  renderText,
}: {
  info: FooterInfo;
  renderText?: (name: keyof FooterInfo, value: string) => ReactNode;
}) {
  const storeUrl = info.storeUrl?.trim() ?? '';

  return (
    <div className={styles.topbar}>
      <div className={`wrap ${styles.topbarRow}`}>
        <span className={styles.topbarItem}>
          대표전화{' '}
          {renderText ? (
            renderText('tel', info.tel)
          ) : (
            <a className={styles.topbarTel} href={telHref(info.tel)}>
              {info.tel}
            </a>
          )}
        </span>

        {/* 주소가 비어 있으면 링크를 그리지 않는다 — 눌러도 갈 곳 없는 글자를 두지
            않는다. 고치는 중에는 비어 있어도 그린다. 안 그리면 적을 자리가 없다. */}
        {(storeUrl !== '' || renderText) && (
          <span className={styles.topbarItem}>
            {renderText ? (
              <>스마트스토어 주소: {renderText('storeUrl', info.storeUrl)}</>
            ) : (
              <a
                className={styles.topbarStore}
                href={storeUrl}
                target="_blank"
                rel="noopener noreferrer"
              >
                스마트스토어
                <span aria-hidden="true"> ↗</span>
              </a>
            )}
          </span>
        )}
      </div>
    </div>
  );
}
