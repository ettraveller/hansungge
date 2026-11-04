/**
 * 지점 데이터.
 *
 * <p>메인의 지점 섹션과 `/store` 지점찾기가 <b>같은 원천</b>을 쓴다. 두 곳에 따로 두면
 * 지점이 하나 늘 때 한쪽만 고쳐지고, 그 차이는 누가 제보하기 전까지 드러나지 않는다.
 *
 * <p>지점 관리 기능(STEP 15~16)이 생기면 이 파일을 지우고 호출부에서 API 를 부르면 된다 —
 * 화면 컴포넌트는 그대로다. 그래서 배열 하나만 내보낸다.
 */

export interface Branch {
  name: string;
  /** 도로명 주소. 지도 좌표는 이 값을 지오코딩해서 얻는다 */
  address: string;
  /** 지번 주소. 표시용 보조 정보 */
  addressJibun?: string;
  postalCode?: string;
  /**
   * 대표번호 — <b>없을 수 있다.</b>
   *
   * <p>새로 낸 지점은 번호가 아직 안 나온다. 그때 빈 문자열을 넣어 두면 화면에
   * 「전화 걸기」 버튼이 그대로 뜨고, 누르면 아무 데도 걸리지 않는다. 없으면
   * 비워 두고, 화면은 그 줄과 버튼을 아예 그리지 않는다.
   */
  tel?: string;
  /**
   * 네이버 지도 장소 ID. "길찾기"·"상세보기" 링크의 근거다.
   *
   * <p><b>없을 수 있다.</b> 네이버에 등록되기 전의 지점이 그렇다. 없으면 두 링크가
   * 장소 페이지 대신 <b>이름·주소로 검색한 결과</b>로 간다 — 깨진 링크를 내거는
   * 것보다 낫고, 등록되는 날 ID 한 줄만 채우면 원래 동작으로 돌아온다.
   */
  naverPlaceId?: string;
  /**
   * 위도·경도 — <b>적지 않아도 된다.</b>
   *
   * <p>비워 두면 지도가 {@link address} 를 지오코딩해서 좌표를 얻는다. 지점이 늘 때마다
   * 좌표를 손으로 찾아 넣는 것은 실수하기 쉽고(한 자리만 틀려도 엉뚱한 동네에 찍힌다),
   * 지점이 이전하면 주소와 좌표 두 곳을 고쳐야 한다. <b>주소 하나만 원천으로 둔다.</b>
   *
   * <p>지오코딩이 실패하는 주소(신축 건물 등)일 때만 여기에 직접 적어 덮어쓴다.
   */
  lat?: number;
  lng?: number;
  /** 영업시간 등 한 줄 안내 */
  hours?: string;
}

export interface Region {
  name: string;
  branches: Branch[];
}

/**
 * 지점을 가리키는 값. 목록의 key 와 "지금 고른 지점" 비교에 쓴다.
 *
 * <p>예전에는 {@link Branch.naverPlaceId} 를 그대로 썼다. 그 값이 없는 지점이
 * 생기면서(네이버 등록 전) 두 지점의 key 가 모두 undefined 가 되어 목록이
 * 엉키고, 하나를 고르면 둘이 같이 선택된 것으로 보인다. 이름은 늘 있고 서로 다르다.
 */
export function branchKey(branch: Branch): string {
  return branch.name;
}

/**
 * 지역은 서울부터. 지점 목록의 통상적인 차례다.
 *
 * <p>레퍼런스(한국공인금거래소)는 68개 지점을 17개 시·도로 나눠 보여준다. 아직 둘뿐이라
 * 지역 구분이 과해 보이지만 구조를 미리 맞춰 둔다 — 지점이 늘 때 데이터만 추가하면
 * 화면은 그대로 동작한다.
 */
export const REGIONS: Region[] = [
  {
    name: '서울',
    branches: [
      {
        name: '한성금거래소 석계점',
        /*
         * 도로명은 네이버 지오코더가 지번('월계동 46-1')을 변환해 돌려준 값이다
         * (2026-11-04 운영에서 확인, 좌표 37.6152893 / 127.0651434 — 석계역 앞).
         * 지도가 쓰는 바로 그 서비스의 답이라 화면과 어긋날 일이 없다.
         */
        address: '서울 노원구 화랑로 337 105호',
        addressJibun: '서울 노원구 월계동 46-1',
        // 번호는 아직 안 나왔다. 비워 두면 화면이 전화 줄과 버튼을 그리지 않는다.
        // 네이버 장소도 등록 전이라 길찾기·상세보기는 검색 결과로 간다.
        hours: '평일 10:00 – 19:00 (일·공휴일 휴무)',
      },
    ],
  },
  {
    name: '경기 · 인천',
    branches: [
      {
        name: '한성금거래소 다산점',
        address: '경기 남양주시 다산중앙로145번길 15 1층 101호',
        addressJibun: '경기 남양주시 다산동 6063-1',
        postalCode: '12248',
        tel: '010-5901-4979',
        naverPlaceId: '2058976297',
        hours: '평일 10:00 – 19:00 (일·공휴일 휴무)',
      },
    ],
  },
];

/** 전 지점 수. 화면의 "전체 (00)" 에 쓴다. */
export const TOTAL_BRANCHES = REGIONS.reduce((n, r) => n + r.branches.length, 0);

/** 지역명을 함께 들고 다니는 평탄화 목록. 검색·필터가 이 배열 위에서 돈다. */
export interface FlatBranch extends Branch {
  region: string;
}

export const ALL_BRANCHES: FlatBranch[] = REGIONS.flatMap((r) =>
  r.branches.map((b) => ({ ...b, region: r.name })),
);

/**
 * 장소 ID 가 없을 때의 대안 — 이름과 주소로 검색한다.
 *
 * <p>검색어에 이름만 넣으면 같은 상호의 다른 지점이 먼저 뜰 수 있어 주소를 함께 넣는다.
 */
function naverSearchUrl(branch: Branch): string {
  return `https://map.naver.com/p/search/${encodeURIComponent(`${branch.name} ${branch.address}`)}`;
}

/** 네이버 지도 장소 페이지. 지도를 못 띄울 때의 대안이자 "상세보기" 링크다. */
export function naverPlaceUrl(branch: Branch): string {
  if (!branch.naverPlaceId) return naverSearchUrl(branch);
  return `https://map.naver.com/p/entry/place/${branch.naverPlaceId}`;
}

/**
 * 네이버 지도 길찾기 — 도착지를 이 지점으로 지정한다.
 *
 * <p>도착지는 장소 ID 로만 지정할 수 있다. 등록 전 지점은 검색 결과로 보낸다 —
 * 거기서 「길찾기」를 한 번 더 누르면 된다. 도착지 없는 길찾기 화면을 띄우는 것보다 낫다.
 */
export function naverDirectionsUrl(branch: Branch): string {
  if (!branch.naverPlaceId) return naverSearchUrl(branch);
  return `https://map.naver.com/p/directions/-/-/-/transit?c=15,0,0,0,dh&destination=${branch.naverPlaceId}`;
}
