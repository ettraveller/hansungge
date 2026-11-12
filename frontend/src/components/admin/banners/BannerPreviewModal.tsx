'use client';

import { useState } from 'react';

import type { FooterInfo } from '@/api/footer';
import { Modal } from '../Modal';
import styles from '../admin.module.css';

/**
 * 미리보기 폭.
 *
 * <p>실제 쓰이는 두 경계값이다 — 히어로의 미디어 쿼리가 960px 에서 갈리므로, PC 는
 * 그보다 넉넉히 넓고 휴대폰은 그보다 좁아야 한다. 390px 은 아이폰 14·15 의 논리 폭이다.
 *
 * <p>높이는 정하지 않는다. 창에 남은 높이를 전부 준다 — 숫자로 박으면 관리자 화면이
 * 큰 사람에게도 그만큼만 보인다. 배너가 크게 보이는 것이 이 창의 목적이다.
 */
const DEVICES = [
  { key: 'pc', label: 'PC', width: 1280 },
  { key: 'mobile', label: '휴대폰', width: 390 },
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
  footer,
}: {
  open: boolean;
  onClose: () => void;
  /** 가로용 키. 이 순서가 슬라이드 순서다 */
  imageKeys: string[];
  /** 같은 자리의 휴대폰용 키. 없는 자리는 빈 문자열 */
  mobileImageKeys: string[];
  /**
   * 고치는 중인 푸터. 손대지 않았으면 null 이고, 그때는 미리보기가 저장된 값을 쓴다.
   *
   * <p>배너와 같은 이유로 넘긴다 — 저장한 뒤에만 볼 수 있으면 "일단 저장하고 확인하고
   * 되돌리는" 절차가 되고, 그 사이 확인되지 않은 주소가 손님 화면에 걸린다.
   */
  footer?: FooterInfo | null;
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
  // 손대지 않았으면 아무것도 붙이지 않는다 — 미리보기가 저장된 푸터를 그대로 쓴다.
  if (footer) {
    for (const [name, value] of Object.entries(footer)) query.set(name, value);
  }
  const src = `${basePath}/banner-preview?${query.toString()}`;

  return (
    <Modal
      open={open}
      title="메인 배너 미리보기"
      width="full"
      onClose={onClose}
      footer={
        <p className={styles.hint}>
          손님이 보는 <strong>메인 화면 그대로</strong>이고, 배너와 푸터만 지금 화면에 있는
          것으로 바꿔 그린다 — 저장하지 않은 것까지 보인다. 아래로 밀면 푸터까지 볼 수 있다.
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
            /*
             * 높이를 창에서 직접 계산한다.
             *
             * height:100% 는 여기서 듣지 않았다 — 부모(미리보기 자리)의 높이가 플렉스가
             * 정한 값이라 <b>확정 높이가 아니어서</b>, 백분율이 auto 로 떨어지고 iframe 의
             * 기본 높이 150px 만 남았다. 모달은 화면만큼 큰데 안에 보이는 것은 머리띠
             * 한 줄뿐인 그림이 그래서 나왔다 (2026-11-12).
             *
             * 240px 은 창 위아래로 빠지는 것의 합이다 — 제목줄·기기 단추·안쪽 여백·
             * 아래 설명.
             */
            style={{ width: current.width, height: 'calc(100vh - 240px)' }}
          />
        )}
      </div>
    </Modal>
  );
}
