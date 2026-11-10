'use client';

import { useState } from 'react';

import { Modal } from '../Modal';
import styles from '../admin.module.css';

/**
 * 미리보기 창 크기.
 *
 * <p>폭은 실제 쓰이는 두 경계값이다 — 히어로의 미디어 쿼리가 960px 에서 갈리므로,
 * PC 는 그보다 넉넉히 넓고 휴대폰은 그보다 좁아야 한다. 390px 은 아이폰 14·15 의 논리 폭이다.
 *
 * <p>높이는 <b>그 기기의 화면 높이</b>다 (배너 높이가 아니다). 띄우는 것이 메인 전체라,
 * 배너만큼만 열면 그 아래 시세표와 맞닿는 자리를 볼 수 없다 — 사진 아래쪽 문구가
 * 시세표에 걸리는지가 정작 확인하려는 것이다. 아래로 밀어 메인 끝까지 볼 수 있다.
 */
const DEVICES = [
  { key: 'pc', label: 'PC', width: 1280, height: 760 },
  { key: 'mobile', label: '휴대폰', width: 390, height: 780 },
] as const;

type DeviceKey = (typeof DEVICES)[number]['key'];

/**
 * 메인 배너 미리보기.
 *
 * <h2>iframe 인 이유</h2>
 * 가로용·휴대폰용을 가르는 것은 <b>창 폭을 보는 미디어 쿼리</b>다. 모달 안에서 히어로를
 * 바로 그리면 그 창은 관리자가 쓰는 브라우저 창이라, 데스크톱에서는 휴대폰용을 영원히
 * 볼 수 없다. iframe 은 제 폭이 곧 제 뷰포트이므로 390px 안에서 모든 값이 휴대폰
 * 값으로 풀린다 — CSS 를 한 줄도 고치지 않는다.
 *
 * <h2>폭을 줄이지 않는다</h2>
 * PC 미리보기는 1280px 그대로 띄우고, 모달이 좁으면 좌우로 밀어 본다. 화면에 맞게
 * 줄이면 그 축소된 폭이 다시 미디어 쿼리를 건드려서, 정작 확인하려던 "PC 에서 보이는
 * 모양"이 아니게 된다.
 */
export function BannerPreviewModal({
  open,
  onClose,
  imageKeys,
  mobileImageKeys,
}: {
  open: boolean;
  onClose: () => void;
  /** 가로용 키. 이 순서가 슬라이드 순서다 */
  imageKeys: string[];
  /** 같은 자리의 휴대폰용 키. 없는 자리는 빈 문자열 */
  mobileImageKeys: string[];
}) {
  const [device, setDevice] = useState<DeviceKey>('pc');
  const current = DEVICES.find((d) => d.key === device) ?? DEVICES[0];

  // basePath 는 next/link 와 next/image 만 붙여 준다. iframe 주소는 직접 붙인다 —
  // 빼면 QA(/qa) 에서 운영 쪽 미리보기를 열게 된다.
  const basePath = process.env.NEXT_PUBLIC_BASE_PATH ?? '';
  const query = new URLSearchParams({
    w: imageKeys.join(','),
    m: mobileImageKeys.join(','),
  });
  const src = `${basePath}/banner-preview?${query.toString()}`;

  return (
    <Modal
      open={open}
      title="메인 배너 미리보기"
      width="wide"
      onClose={onClose}
      footer={
        <p className={styles.hint}>
          손님이 보는 <strong>메인 화면 그대로</strong>이고, 배너만 지금 올려 둔 사진으로
          바꿔 그린다 — 저장하지 않은 것까지 보인다. 아래로 밀면 메인 끝까지 볼 수 있다.
          <br />
          <strong>휴대폰</strong>으로 바꿔 보면 좌우가 얼마나 잘리는지 확인할 수 있다.
          휴대폰용을 올리지 않은 배너는 가로용 한 장을 잘라서 쓴다.
        </p>
      }
    >
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
            <span className={styles.previewDeviceSize}>{d.width}px</span>
          </button>
        ))}
      </div>

      {/* 열려 있을 때만 그린다 — 닫아 둔 iframe 이 바뀐 사진을 들고 있지 않게 한다.
          key 에 폭을 넣어 PC↔휴대폰을 바꿀 때 새로 띄운다. iframe 의 뷰포트 폭은
          띄울 때 정해지므로, 같은 iframe 의 width 만 바꾸면 미디어 쿼리가 따라오지
          않는 브라우저가 있다. */}
      <div className={styles.previewStage}>
        {open && (
          <iframe
            key={`${current.key}-${src}`}
            src={src}
            title="메인 배너 미리보기"
            className={styles.previewIframe}
            style={{ width: current.width, height: current.height }}
          />
        )}
      </div>
    </Modal>
  );
}
