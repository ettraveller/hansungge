'use client';

import Script from 'next/script';
import { useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';

import { useIsBrowser } from '@/hooks/useIsBrowser';
import styles from './PostcodeSearch.module.css';

const SCRIPT_SRC = 'https://t1.daumcdn.net/mapjsapi/bundle/postcode/prod/postcode.v2.js';

export interface PickedAddress {
  /** 5자리 우편번호 */
  zipcode: string;
  /** 도로명 또는 지번 — 사용자가 고른 쪽. 건물명·법정동이 괄호로 붙는다 */
  address: string;
}

interface Props {
  onPick: (picked: PickedAddress) => void;
  /** 스크립트를 못 불러왔을 때 바깥(폼)이 직접 입력을 열어 줄 수 있게 알린다. */
  onUnavailable?: () => void;
}

/**
 * 우편번호 찾기 — 카카오(다음) 우편번호 서비스.
 *
 * <h2>왜 이것인가</h2>
 * 한국 쇼핑몰의 사실상 표준이고, <b>가입도 키도 없이</b> 무료로 쓴다. 행정안전부
 * 도로명주소 API 는 키를 받아야 하고, 네이버 지도의 지오코딩은 "주소를 좌표로" 바꾸는
 * 것이지 "주소를 찾아 주는" 화면이 아니다 — 목록·동이름 검색·건물명 검색을 우리가
 * 다시 만들어야 한다.
 *
 * <h2>왜 주소를 손으로 못 쓰게 하나</h2>
 * 우편번호와 주소는 <b>짝이 맞아야</b> 한다. 손으로 적으면 우편번호만 틀린 주문이
 * 들어오고, 그건 배송이 실패한 뒤에야 드러난다. 그래서 검색이 되는 동안에는 두 칸을
 * 읽기 전용으로 두고, 상세 주소만 직접 받는다.
 *
 * <p>다만 <b>스크립트를 못 불러왔을 때는 직접 입력을 연다.</b> 광고 차단기나 사내망에
 * 막히는 일이 있는데, 그때 칸이 잠겨 있으면 주문 자체를 못 한다 — 결제 직전 화면에서
 * 그것은 기능 하나가 빠진 것이 아니라 매출이 멈추는 일이다.
 */
export function PostcodeSearch({ onPick, onUnavailable }: Props) {
  const [phase, setPhase] = useState<'loading' | 'ready' | 'failed'>('loading');
  const [open, setOpen] = useState(false);
  const embedRef = useRef<HTMLDivElement | null>(null);

  // 포털은 브라우저에서만 만들 수 있다. 서버 렌더에는 레이어를 그리지 않는다.
  const mounted = useIsBrowser();

  // 부르는 쪽은 onPick 을 보통 그 자리에서 만든 함수로 넘긴다. 그것을 그대로
  // 의존성에 두면 <b>부모가 다시 그려질 때마다</b> 아래 effect 가 다시 돌아
  // 검색 화면을 지웠다 새로 끼운다 — 사용자가 "역삼" 까지 친 것이 사라진다.
  // 주문서는 입력할 때마다 다시 그려지는 화면이라 실제로 그렇게 된다.
  const onPickRef = useRef(onPick);
  // 렌더 중에 ref 를 건드리면 안 된다 — 같은 렌더가 버려질 수 있어서다.
  // effect 에서 맞춘다. oncomplete 는 한참 뒤에 불리므로 한 박자 늦어도 상관없다.
  useEffect(() => {
    onPickRef.current = onPick;
  }, [onPick]);

  // 레이어가 열릴 때 한 번만 끼운다. embed() 는 부를 때마다 새로 그리므로
  // 넣기 전에 비운다.
  useEffect(() => {
    if (!open || !embedRef.current) return;

    const target = embedRef.current;
    target.innerHTML = '';
    new window.daum!.Postcode({
      width: '100%',
      height: '100%',
      oncomplete: (data) => {
        onPickRef.current({ zipcode: data.zonecode, address: composeAddress(data) });
        setOpen(false);
      },
      onclose: () => setOpen(false),
    }).embed(target);
  }, [open]);

  // 레이어가 떠 있는 동안 뒤 화면이 같이 스크롤되지 않게 한다.
  useEffect(() => {
    if (!open) return;
    const previous = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.body.style.overflow = previous;
    };
  }, [open]);

  const fail = () => {
    setPhase('failed');
    onUnavailable?.();
  };

  return (
    <>
      <Script
        src={SCRIPT_SRC}
        strategy="afterInteractive"
        onReady={() => setPhase(window.daum?.Postcode ? 'ready' : 'failed')}
        onError={fail}
      />

      <button
        type="button"
        className={styles.button}
        disabled={phase !== 'ready'}
        onClick={() => setOpen(true)}
      >
        {phase === 'loading' ? '불러오는 중…' : '우편번호 찾기'}
      </button>

      {phase === 'failed' && (
        <span className={styles.note}>
          우편번호 찾기를 불러오지 못했습니다. 주소를 직접 입력해 주세요.
        </span>
      )}

      {mounted && open &&
        createPortal(
          <div
            className={styles.dim}
            role="dialog"
            aria-modal="true"
            aria-label="우편번호 찾기"
            onClick={(e) => {
              if (e.target === e.currentTarget) setOpen(false);
            }}
          >
            <div className={styles.panel}>
              <div className={styles.panelHead}>
                <strong className={styles.panelTitle}>우편번호 찾기</strong>
                <button
                  type="button"
                  className={styles.close}
                  aria-label="닫기"
                  onClick={() => setOpen(false)}
                >
                  ×
                </button>
              </div>
              <div ref={embedRef} className={styles.embed} />
            </div>
          </div>,
          document.body,
        )}
    </>
  );
}

/**
 * 고른 쪽(도로명/지번)을 그대로 쓰고, 도로명이면 괄호를 붙인다.
 *
 * <p>도로명주소는 "서울 강남구 테헤란로 123" 처럼 동 이름이 없어서, 받는 사람이
 * 자기 주소인지 알아보기 어렵다. 그래서 법정동·건물명을 괄호로 덧붙이는 것이
 * 관례다 — "서울 강남구 테헤란로 123 (역삼동, 한성빌딩)".
 */
function composeAddress(data: DaumPostcodeResult): string {
  const base = data.userSelectedType === 'J' ? data.jibunAddress : data.roadAddress;
  if (data.userSelectedType === 'J') {
    return base;
  }

  const extras: string[] = [];
  // 법정동은 "역삼동" 처럼 끝나는 것만 쓴다 — "역삼1가" 같은 값은 도로명주소에
  // 이미 들어 있어 두 번 적히게 된다.
  if (data.bname && /[동|로|가]$/.test(data.bname)) {
    extras.push(data.bname);
  }
  if (data.buildingName && data.apartment === 'Y') {
    extras.push(data.buildingName);
  }
  return extras.length > 0 ? `${base} (${extras.join(', ')})` : base;
}

/** 쓰는 값만 좁게 선언한다 — 공급자 타입 패키지를 들이지 않는다 (NaverMap 과 같은 판단). */
interface DaumPostcodeResult {
  zonecode: string;
  roadAddress: string;
  jibunAddress: string;
  /** 'R' 도로명, 'J' 지번 */
  userSelectedType: 'R' | 'J';
  /** 법정동·법정리 */
  bname: string;
  buildingName: string;
  /** 공동주택이면 'Y' */
  apartment: 'Y' | 'N';
}

declare global {
  interface Window {
    daum?: {
      Postcode: new (options: {
        width: string;
        height: string;
        oncomplete: (data: DaumPostcodeResult) => void;
        onclose: () => void;
      }) => { embed: (element: HTMLElement) => void };
    };
  }
}
