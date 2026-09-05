'use client';

import Script from 'next/script';
import { useEffect, useRef, useState } from 'react';
import { naverPlaceUrl, type Branch } from '@/data/branches';
import styles from './NaverMap.module.css';

/** NCP 콘솔 > Maps > Application 에서 발급한다. 없으면 지도 대신 안내를 띄운다. */
const CLIENT_ID = process.env.NEXT_PUBLIC_NAVER_MAP_CLIENT_ID;

/**
 * 지오코더 서브모듈을 기다리는 한도.
 *
 * <p>느린 모바일 회선에서도 서브모듈 한 개를 받기에 충분하고, Geocoding 이 아예
 * 꺼져 있어 영영 오지 않는 경우에 사용자를 붙잡아 두기에는 짧다.
 */
const GEOCODER_WAIT_MS = 5_000;
const GEOCODER_POLL_MS = 100;

interface Props {
  branch: Branch;
}

/**
 * 지점 위치 지도 (네이버 지도 Maps JavaScript API v3).
 *
 * <h2>좌표를 데이터에 적지 않는다</h2>
 * {@link Branch#lat}/{@link Branch#lng} 가 없으면 <b>주소를 지오코딩해서</b> 좌표를 얻는다.
 * 지점이 늘 때마다 좌표를 손으로 찾아 넣는 것은 실수하기 쉽고(한 자리만 틀려도 엉뚱한
 * 동네에 찍힌다), 지점이 이전하면 주소와 좌표를 둘 다 고쳐야 한다. 주소 하나만 원천으로
 * 두면 그 문제가 없다.
 *
 * <p>NCP 애플리케이션에서 <b>Dynamic Map 과 Geocoding 을 함께</b> 켜야 한다.
 * Geocoding 이 꺼져 있으면 좌표 변환이 실패하고, 그때는 아래 폴백으로 떨어진다.
 *
 * <h2>키가 없어도, 좌표를 못 구해도 화면이 깨지지 않는다</h2>
 * 지도를 못 띄우는 모든 경우에 안내 문구와 <b>네이버 지도 바로가기</b>를 대신 보여준다.
 * 키 발급은 개발과 별개로 시간이 걸리는 일이라, 그것 때문에 지점 페이지가 멈추면 안 된다.
 *
 * <p>임의의 기본 좌표(서울시청 등)로 지도를 띄우지 않는다 — 그건 비어 있는 것보다 나쁘다.
 * 고객이 <b>틀린 위치를 맞다고 믿는다.</b>
 */
export function NaverMap({ branch }: Props) {
  const ref = useRef<HTMLDivElement>(null);
  /** 'wait' 스크립트 대기 · 'ready' API 사용 가능 · 'failed' 지도를 포기하고 폴백 */
  const [phase, setPhase] = useState<'wait' | 'ready' | 'failed'>('wait');

  /**
   * 스크립트가 준비됐는지 여기서 판정한다.
   *
   * <p>effect 안에서 동기적으로 setState 하면 렌더가 연쇄된다(React 규칙). 스크립트
   * 로드는 이벤트 핸들러라 그런 제약이 없고, "API 가 실제로 붙었는가"도 이 시점에
   * 바로 알 수 있다.
   */
  const handleReady = () => {
    const naver = (window as unknown as { naver?: NaverMaps }).naver;
    setPhase(naver?.maps ? 'ready' : 'failed');
  };

  useEffect(() => {
    if (phase !== 'ready' || !ref.current) return;

    const naver = (window as unknown as { naver?: NaverMaps }).naver;
    if (!naver?.maps) return;

    const { LatLng, Map, Marker } = naver.maps;
    const el = ref.current;
    let cancelled = false;
    let timer: ReturnType<typeof setTimeout> | undefined;

    const draw = (lat: number, lng: number) => {
      if (cancelled) return;
      const center = new LatLng(lat, lng);
      const map = new Map(el, { center, zoom: 17 });
      new Marker({ position: center, map, title: branch.name });
    };

    if (branch.lat != null && branch.lng != null) {
      draw(branch.lat, branch.lng);
      return;
    }

    const geocode = (Service: NonNullable<NaverMaps['maps']['Service']>) => {
      Service.geocode({ query: branch.address }, (status, response) => {
        if (cancelled) return;
        const found = response?.v2?.addresses?.[0];
        if (status !== Service.Status.OK || !found) {
          setPhase('failed');
          return;
        }
        // x 가 경도, y 가 위도다. 뒤집으면 지도가 엉뚱한 곳(바다)으로 간다.
        draw(Number(found.y), Number(found.x));
      });
    };

    /**
     * 지오코더가 준비될 때까지 기다린다.
     *
     * <p>{@code submodules=geocoder} 는 maps.js 와 <b>같이 오지 않는다.</b> maps.js 가
     * 실행되면서 서브모듈을 따로 내려받으므로, 스크립트의 load 이벤트가 떴을 때
     * {@code naver.maps.Service} 는 아직 없을 수 있다.
     *
     * <p>여기서 곧장 실패로 처리하면 <b>캐시가 없는 첫 방문에서만</b> 지도가 안 뜨고,
     * 새로고침하면 서브모듈이 캐시에서 즉시 올라와 정상으로 보인다 — "처음엔 안 되고
     * 새로고침하면 되는" 증상이 정확히 이것이었다.
     *
     * <p>영영 안 오는 경우(NCP 애플리케이션에서 Geocoding 미활성)와 구분해야 하므로
     * 무한정 기다리지 않는다. 기한을 넘기면 그때 폴백으로 내려간다.
     */
    const deadline = Date.now() + GEOCODER_WAIT_MS;
    const waitForGeocoder = () => {
      if (cancelled) return;
      const Service = naver.maps.Service;
      if (Service) {
        geocode(Service);
        return;
      }
      if (Date.now() >= deadline) {
        setPhase('failed');
        return;
      }
      timer = setTimeout(waitForGeocoder, GEOCODER_POLL_MS);
    };

    // effect 본문에서 동기적으로 setState 하지 않도록 한 박자 미뤄 시작한다.
    timer = setTimeout(waitForGeocoder, 0);

    return () => {
      cancelled = true;
      if (timer) clearTimeout(timer);
    };
  }, [phase, branch]);

  const failed = phase === 'failed';

  if (!CLIENT_ID || failed) {
    return (
      <div className={styles.fallback}>
        <p className={styles.fallbackTitle}>
          {CLIENT_ID ? '지도를 불러오지 못했습니다' : '지도는 준비 중입니다'}
        </p>
        <p className={styles.fallbackDesc}>
          네이버 지도에서 위치와 길찾기를 확인하실 수 있습니다.
        </p>
        <a
          className={styles.fallbackLink}
          href={naverPlaceUrl(branch)}
          target="_blank"
          rel="noopener noreferrer"
        >
          네이버 지도에서 보기
        </a>
      </div>
    );
  }

  return (
    <>
      <Script
        src={`https://oapi.map.naver.com/openapi/v3/maps.js?ncpKeyId=${CLIENT_ID}&submodules=geocoder`}
        strategy="afterInteractive"
        onReady={handleReady}
        onError={() => setPhase('failed')}
      />
      <div ref={ref} className={styles.map} aria-label={`${branch.name} 위치 지도`} />
    </>
  );
}

/**
 * 쓰는 부분만 좁게 선언한다. 전체 타입 패키지(@types/navermaps)를 들이면 의존성이 늘고,
 * 실제로 부르는 것은 아래 몇 개뿐이다.
 */
interface NaverMaps {
  maps: {
    LatLng: new (lat: number, lng: number) => unknown;
    Map: new (el: HTMLElement, opts: { center: unknown; zoom: number }) => unknown;
    Marker: new (opts: { position: unknown; map: unknown; title: string }) => unknown;
    Service?: {
      Status: { OK: string };
      geocode: (
        opts: { query: string },
        cb: (status: string, response?: GeocodeResponse) => void,
      ) => void;
    };
  };
}

interface GeocodeResponse {
  v2?: { addresses?: Array<{ x: string; y: string }> };
}
