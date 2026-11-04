'use client';

import Link from 'next/link';
import { useState, useSyncExternalStore } from 'react';
import { useQuery } from '@tanstack/react-query';

import { popupApi } from '@/api/popups';
import { BuybackCalculator } from '@/components/price/BuybackCalculator';
import { mediaUrl } from '@/utils/media';
import {
  getHiddenServerSnapshot,
  getHiddenSnapshot,
  hidePopupToday,
  subscribeHidden,
} from './hiddenPopups';
import styles from './PopupLayer.module.css';

/**
 * 메인 팝업.
 *
 * <h2>첫 칠에는 띄우지 않는다</h2>
 * 서버가 그린 HTML 에 팝업이 섞여 있으면 검색엔진이 그 글을 본문으로 읽고, 자바스크립트가
 * 느린 회선에서는 <b>팝업만 먼저 떠 있는 화면</b>이 된다. 화면이 자리를 잡은 뒤 얹는다.
 *
 * <h2>「오늘 하루 보지 않기」는 이 브라우저에만 남는다</h2>
 * 로그인 전에도 떠야 하므로 서버에 기록할 곳이 없다 ({@code hiddenPopups.ts}).
 *
 * <h2>하나씩 보여 준다</h2>
 * 여러 개가 동시에 살아 있어도 한 번에 하나만 띄운다. 알림창 두 개가 겹쳐 뜨는 화면은
 * 그 자체로 신뢰를 깎는다. 닫으면 다음 것이 올라온다.
 */
export function PopupLayer() {
  const { data } = useQuery({
    queryKey: ['popups', 'live'],
    queryFn: popupApi.live,
    // 첫 화면의 짐을 늘리지 않는다. 팝업은 늦게 떠도 되는 것이다.
    staleTime: 5 * 60 * 1000,
  });

  /** 이번 방문에서 닫은 것 */
  const [closed, setClosed] = useState<number[]>([]);

  // 오늘 하루 숨긴 것. 서버에서는 null 이고, 그동안 아무것도 띄우지 않는다 —
  // 숨겨 둔 팝업이 한 번 번쩍였다가 사라지는 것이 제일 거슬린다.
  const hiddenToday = useSyncExternalStore(
    subscribeHidden,
    getHiddenSnapshot,
    getHiddenServerSnapshot,
  );

  const ready = hiddenToday !== null;
  const queue = ready
    ? (data ?? []).filter((p) => !closed.includes(p.id) && !hiddenToday.includes(p.id))
    : [];
  const popup = queue[0];

  if (!popup) return null;

  const close = () => setClosed((ids) => [...ids, popup.id]);

  const hideToday = () => {
    hidePopupToday(popup.id);
    close();
  };

  return (
    <div className={styles.backdrop} role="dialog" aria-modal="true" aria-label="안내">
      {/* 바깥을 눌러도 닫힌다. 닫는 길이 ✕ 하나뿐이면 손가락으로 찾기 어렵다 */}
      <button
        type="button"
        className={styles.backdropHit}
        aria-label="닫기"
        onClick={close}
      />

      <div className={styles.panel}>
        <button type="button" className={styles.close} onClick={close} aria-label="닫기">
          ✕
        </button>

        {popup.kind === 'CALCULATOR' ? (
          <div className={styles.calc}>
            <p className={styles.eyebrow}>보유하신 금, 얼마에 사드릴까요</p>
            <h2 className={styles.title}>매입 계산기</h2>
            <BuybackCalculator />
          </div>
        ) : (
          <div className={styles.content}>
            {popup.imageKey && (
              // eslint-disable-next-line @next/next/no-img-element
              <img className={styles.image} src={mediaUrl(popup.imageKey) ?? ''} alt="" />
            )}
            <h2 className={styles.title}>{popup.title}</h2>
            {popup.body && <p className={styles.body}>{popup.body}</p>}
          </div>
        )}

        {popup.linkHref && (
          <Link href={popup.linkHref} className={styles.cta} onClick={close}>
            {popup.linkLabel ?? '자세히 보기'}
          </Link>
        )}

        <div className={styles.foot}>
          {popup.dismissible && (
            <button type="button" className={styles.footBtn} onClick={hideToday}>
              오늘 하루 보지 않기
            </button>
          )}
          <button type="button" className={styles.footBtn} onClick={close}>
            닫기
          </button>
        </div>
      </div>
    </div>
  );
}
