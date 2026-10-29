/**
 * 업로드된 이미지 키를 브라우저가 읽는 주소로 바꾼다.
 *
 * DB 에는 URL 이 아니라 <b>키</b>가 들어 있다 (`products/2026/10/3f2a….webp`).
 * 저장 위치를 옮기는 날 전 행을 UPDATE 하지 않아도 되게 하려는 것이고, 그 대가로
 * 주소 조립은 화면이 한다.
 *
 * 기본 주소를 API 주소에서 끌어내는 이유 — 지금 이미지를 내보내는 것이 백엔드
 * 자신이다 (`MediaResourceConfig` 의 `/media/**`). 별도 환경변수를 두면 둘이
 * 어긋나는 날이 오고, 그때 업로드는 성공하는데 화면만 전부 깨진다.
 * CDN 을 붙이면 `NEXT_PUBLIC_MEDIA_BASE_URL` 로 덮어쓴다.
 */
const BASE_URL = (
  process.env.NEXT_PUBLIC_MEDIA_BASE_URL ??
  `${process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://localhost:8080'}/media`
).replace(/\/+$/, '');

/** 키가 없으면 null — 호출하는 쪽이 "이미지 없음" 자리를 그린다. */
export function mediaUrl(objectKey: string | null | undefined): string | null {
  if (objectKey == null) return null;
  const key = objectKey.trim();
  if (key === '') return null;

  // 예전에 손으로 넣은 값이 전체 URL 일 수 있다. 그대로 쓴다 — 앞에 베이스를 또
  // 붙이면 `.../media/https://...` 가 되어 무엇이 잘못됐는지 알기 어려워진다.
  if (/^https?:\/\//i.test(key)) return key;

  return `${BASE_URL}/${key.replace(/^\/+/, '')}`;
}
