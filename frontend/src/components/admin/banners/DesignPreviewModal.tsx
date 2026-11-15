'use client';

import { useState } from 'react';

import { Modal } from '../Modal';
import styles from '../admin.module.css';

/**
 * 미리보기 폭.
 *
 * <p>히어로의 미디어 쿼리가 960px 에서 갈린다. 휴대폰은 그보다 좁아야 하므로 390px
 * 로 박는다 — 아이폰 14·15 의 논리 폭이다. PC 는 창을 꽉 채운다.
 */
const DEVICES = [
  { key: 'pc', label: 'PC', width: '100%', note: '창 폭' },
  { key: 'mobile', label: '휴대폰', width: 390, note: '390px' },
] as const;

type DeviceKey = (typeof DEVICES)[number]['key'];

/**
 * 꾸민 배너 미리보기 — 저장하기 전에 손님이 보는 모양으로 본다.
 *
 * <h2>iframe 인 이유</h2>
 * 넓은 화면과 좁은 화면을 가르는 것은 <b>창 폭을 보는 미디어 쿼리</b>다. 모달 안에서
 * 바로 그리면 그 창은 관리자가 쓰는 브라우저 창이라, 끌어다 둔 자리가 휴대폰에서
 * 어떻게 쌓이는지를 영원히 볼 수 없다. iframe 은 제 폭이 곧 제 뷰포트다.
 *
 * <p>내용은 주소가 아니라 {@code sessionStorage} 로 건넨다 — 부모가 열기 전에 담고
 * 이 안의 화면이 꺼내 쓴다.
 */
export function DesignPreviewModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const [device, setDevice] = useState<DeviceKey>('pc');
  const current = DEVICES.find((d) => d.key === device) ?? DEVICES[0];

  // basePath 는 next/link 만 붙여 준다. iframe 주소는 직접 붙인다 — 빼면 QA(/qa) 에서
  // 운영 쪽 미리보기를 열게 된다.
  const src = `${process.env.NEXT_PUBLIC_BASE_PATH ?? ''}/banner-draft-preview`;

  return (
    <Modal open={open} title="꾸민 배너 미리보기" width="full" onClose={onClose}>
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

      {/* 열려 있을 때만 그린다 — 닫아 둔 iframe 이 낡은 배너를 들고 있지 않게 한다.
          key 에 폭을 넣어 PC↔휴대폰을 바꿀 때 새로 띄운다. iframe 의 뷰포트 폭은
          띄울 때 정해지므로, 같은 iframe 의 width 만 바꾸면 미디어 쿼리가 따라오지
          않는 브라우저가 있다. */}
      <div className={styles.previewStage}>
        {open && (
          <iframe
            key={current.key}
            src={src}
            title="꾸민 배너 미리보기"
            className={styles.previewIframe}
            // 높이를 창에서 직접 계산한다. height:100% 는 부모 높이가 플렉스가 정한
            // 값(=확정 높이가 아님)이라 듣지 않고 iframe 기본 150px 만 남는다.
            style={{ width: current.width, height: 'calc(100vh - 240px)' }}
          />
        )}
      </div>
    </Modal>
  );
}
