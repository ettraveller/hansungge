'use client';

import { useEffect, useRef, useState } from 'react';

import styles from '../admin.module.css';

const DEVICES = [
  { key: 'pc', label: 'PC', width: 1280, note: '1280px' },
  { key: 'mobile', label: '휴대폰', width: 390, note: '390px' },
] as const;

type DeviceKey = (typeof DEVICES)[number]['key'];

/** 고치는 손을 멈춘 뒤 이만큼 지나야 다시 띄운다. 글자 하나마다 띄우면 계속 깜빡인다. */
const SETTLE_MS = 500;

/**
 * 옆에 붙은 미리보기 — 손님 화면 그대로.
 *
 * <h2>iframe 인 이유</h2>
 * PC·휴대폰을 가르는 것은 창 폭을 보는 미디어 쿼리다. 같은 문서 안에 그리면 그 폭은
 * 관리자 브라우저의 폭이라 휴대폰 화면을 볼 수 없다. iframe 은 제 폭이 곧 제
 * 뷰포트다.
 *
 * <h2>줄여서 보여 준다</h2>
 * PC 화면은 1280px 로 그린 뒤 자리에 맞게 통째로 줄인다. 자리 폭 그대로(600px쯤)
 * 그리면 휴대폰 배치가 나와 버려서 "PC 미리보기"가 아니게 된다.
 *
 * <h2>주소를 손으로 꽂는다</h2>
 * 고칠 때마다 src 를 바꾸면 글자 하나에 한 번씩 화면이 새로 뜬다. 손을 멈춘 뒤에
 * 한 번만 꽂는다. 스크롤 자리는 미리보기 쪽이 기억했다가 되돌린다
 * (banner-preview 의 ScrollKeeper).
 */
export function LivePreview({ src }: { src: string }) {
  const [device, setDevice] = useState<DeviceKey>('pc');
  const [scale, setScale] = useState(0.5);
  const stage = useRef<HTMLDivElement>(null);
  const frame = useRef<HTMLIFrameElement>(null);

  const current = DEVICES.find((d) => d.key === device) ?? DEVICES[0];

  // 자리 폭에 맞춰 줄이는 배율. 휴대폰은 자리가 더 넓으면 그대로(1배) 둔다.
  useEffect(() => {
    const el = stage.current;
    if (!el) return;
    const observer = new ResizeObserver(([entry]) => {
      setScale(Math.min(1, entry.contentRect.width / current.width));
    });
    observer.observe(el);
    return () => observer.disconnect();
  }, [current.width]);

  // 손을 멈춘 뒤에 주소를 꽂는다. 폭을 바꾼 직후(새 iframe)에는 바로 꽂는다.
  useEffect(() => {
    const el = frame.current;
    if (!el) return;
    if (!el.getAttribute('src')) {
      el.src = src;
      return;
    }
    const timer = setTimeout(() => {
      if (frame.current && frame.current.getAttribute('src') !== src) {
        frame.current.src = src;
      }
    }, SETTLE_MS);
    return () => clearTimeout(timer);
  }, [src, device]);

  return (
    <aside className={styles.livePreview} aria-label="미리보기">
      <div className={styles.livePreviewBar}>
        <strong>미리보기</strong>
        <div className={styles.previewDevices} role="group" aria-label="미리보기 폭">
          {DEVICES.map((d) => (
            <button
              key={d.key}
              type="button"
              aria-pressed={d.key === device}
              className={d.key === device ? styles.previewDeviceOn : styles.previewDevice}
              onClick={() => setDevice(d.key)}
            >
              {d.label}
              <span className={styles.previewDeviceSize}>{d.note}</span>
            </button>
          ))}
        </div>
        <span className={styles.hint}>저장하지 않은 것까지 보인다</span>
      </div>

      <div ref={stage} className={styles.livePreviewStage}>
        {/* 줄인 뒤의 크기만큼만 자리를 차지하게 한다. transform 은 자리를 줄이지
            않아서, 감싸지 않으면 줄인 화면 옆과 아래에 빈 스크롤이 남는다. */}
        <div
          className={styles.livePreviewFit}
          style={{ width: current.width * scale, height: `calc(100% )` }}
        >
          <iframe
            key={current.key}
            ref={frame}
            title="손님 화면 미리보기"
            className={styles.livePreviewFrame}
            style={{
              width: current.width,
              height: `${100 / scale}%`,
              transform: `scale(${scale})`,
            }}
          />
        </div>
      </div>
    </aside>
  );
}
