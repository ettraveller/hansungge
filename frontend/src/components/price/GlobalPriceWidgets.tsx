'use client';

import { useEffect, useId, useRef, useState } from 'react';

import styles from './GlobalPriceWidgets.module.css';

/**
 * 국제 시세 — TradingView 위젯.
 *
 * <h2>우리 서버가 하는 일이 없다</h2>
 * 브라우저가 TradingView 에 직접 붙는다. 우리 쪽에 수집기도, 데이터 공급자 계약도
 * 필요 없다. 그래서 국제시세 공급자 선정(TODO B-4)이 끝나기 전에도 붙일 수 있다.
 *
 * <h2>고시가와 값이 다르다 — 그 사실을 반드시 적는다</h2>
 * 이 그래프는 국제 현물가이고, 우리 고시가는 거기에 마진을 얹어 사람이 정한 값이다.
 * 두 숫자는 당연히 다르다. 적어 두지 않으면 "왜 그래프와 판매가가 다르냐"는 문의가
 * 기본값이 된다 — 경쟁사도 같은 문구를 페이지에 두고 있다.
 */

/** TradingView 스크립트가 window 에 심는 전역. 타입 선언이 없어 최소한만 적는다. */
interface TradingViewGlobal {
  widget: new (options: Record<string, unknown>) => unknown;
}

const SCRIPT_SRC = 'https://s3.tradingview.com/tv.js';

const SYMBOLS = [
  { symbol: 'TVC:GOLD', label: '금', unit: 'USD / 트로이온스' },
  { symbol: 'TVC:SILVER', label: '은', unit: 'USD / 트로이온스' },
  { symbol: 'TVC:PLATINUM', label: '백금', unit: 'USD / 트로이온스' },
] as const;

/**
 * 스크립트를 한 번만 받는다.
 *
 * <p>위젯이 3개라 각자 로드하면 같은 파일을 3번 받는다. 모듈 수준 Promise 로
 * 묶어 두면 두 번째부터는 이미 끝난 Promise 를 그대로 받는다.
 */
let loader: Promise<TradingViewGlobal> | null = null;

function loadTradingView(): Promise<TradingViewGlobal> {
  if (loader) return loader;

  loader = new Promise<TradingViewGlobal>((resolve, reject) => {
    const existing = (window as unknown as { TradingView?: TradingViewGlobal }).TradingView;
    if (existing) {
      resolve(existing);
      return;
    }

    const script = document.createElement('script');
    script.src = SCRIPT_SRC;
    script.async = true;
    script.onload = () => {
      const tv = (window as unknown as { TradingView?: TradingViewGlobal }).TradingView;
      if (tv) resolve(tv);
      else reject(new Error('TradingView 전역을 찾지 못했습니다'));
    };
    // 실패를 조용히 넘기지 않는다. 광고 차단기나 사내 방화벽이 이 도메인을 막는 일이
    // 흔한데, 그때 빈 네모만 남으면 사용자는 사이트가 고장 난 줄 안다.
    script.onerror = () => reject(new Error('국제 시세를 불러오지 못했습니다'));
    document.head.appendChild(script);
  }).catch((e) => {
    // 다음 시도에서 다시 받을 수 있게 실패한 Promise 를 붙들고 있지 않는다.
    loader = null;
    throw e;
  });

  return loader;
}

function Chart({ symbol, label, unit }: { symbol: string; label: string; unit: string }) {
  // DOM id 를 만든다. 고정 문자열을 쓰면 같은 페이지에 두 번 놓였을 때 두 위젯이
  // 같은 컨테이너를 잡고 하나가 사라진다.
  //
  // useId 의 값(React 19 는 «r0» 꼴)을 그대로 쓰지 않는다. 영숫자만 남긴다 —
  // 외부 라이브러리가 이 문자열로 선택자를 만들면 특수문자에서 깨진다.
  const containerId = `tv-${useId().replace(/[^a-zA-Z0-9]/g, '')}`;
  const ref = useRef<HTMLDivElement | null>(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let alive = true;
    const el = ref.current;

    loadTradingView()
      .then((tv) => {
        // effect 가 정리된 뒤 도착한 응답이면 버린다. 개발 모드의 StrictMode 는
        // 마운트 → 정리 → 재마운트를 하는데, 여기서 걸러내지 않으면 두 번째
        // 위젯이 첫 번째 위에 겹쳐 그려진다.
        //
        // ⚠️ "이미 한 번 돌았으면 건너뛴다"는 ref 가드를 쓰면 안 된다. ref 는
        //    재마운트 뒤에도 값이 남아 있어, 정작 진짜 마운트에서 조기 반환하고
        //    위젯이 하나도 안 생긴다 — 실제로 그렇게 만들어 빈 네모만 나왔다.
        if (!alive || !el) return;

        // 재실행 시 앞서 그린 것을 지운다. TradingView 는 컨테이너에 iframe 을
        // 덧붙이기만 해서, 비우지 않으면 겹쳐 쌓인다.
        el.innerHTML = '';

        new tv.widget({
          symbol,
          container_id: el.id,
          interval: '30',
          timezone: 'Asia/Seoul',
          locale: 'kr',
          theme: 'light',
          style: '3', // 영역형 — 값 하나를 훑어보는 용도라 캔들보다 읽기 쉽다
          autosize: true,
          hide_top_toolbar: true,
          hide_legend: false,
          save_image: false,
          allow_symbol_change: false,
        });
      })
      .catch(() => {
        if (alive) setFailed(true);
      });

    return () => {
      alive = false;
    };
  }, [symbol, containerId]);

  return (
    <figure className={styles.card}>
      <figcaption className={styles.cardHead}>
        <span className={styles.cardName}>{label}</span>
        <span className={styles.cardUnit}>{unit}</span>
      </figcaption>
      {/*
        실패해도 컨테이너는 DOM 에 남긴다. 조건부로 제거하면 재시도할 자리가
        사라지고, ref 도 null 이 되어 다시 그릴 수 없다.
      */}
      <div ref={ref} id={containerId} className={styles.cardChart} hidden={failed} />
      {failed && (
        <p className={styles.cardError}>
          국제 시세를 불러오지 못했습니다. 광고 차단 기능이 켜져 있으면 꺼 주세요.
        </p>
      )}
    </figure>
  );
}

export function GlobalPriceWidgets() {
  return (
    <div>
      <div className={styles.grid}>
        {SYMBOLS.map((s) => (
          <Chart key={s.symbol} symbol={s.symbol} label={s.label} unit={s.unit} />
        ))}
      </div>
      <p className={styles.disclaimer}>
        국제 시세는 서버로의 데이터 처리 및 시간차로 인해 다소 차이가 발생할 수 있습니다. 위
        그래프는 국제 현물 시세이며, 당사 고시 시세와는 다릅니다. 실제 거래는 위 &lsquo;오늘의
        고시 시세&rsquo;를 기준으로 합니다.
      </p>
    </div>
  );
}
