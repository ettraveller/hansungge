import { adminRequest } from '@/api/admin/client';

export interface UploadedMedia {
  objectKey: string;
  url: string;
}

/** 키 앞머리로 쓸 분류. 백엔드의 허용 목록과 같아야 한다 (AdminMediaController). */
export type MediaFolder = 'products' | 'events' | 'notices' | 'banners';

/**
 * 이미지 업로드.
 *
 * 여러 장을 한 요청으로 보낸다 — 장당 따로 보내면 10장을 올릴 때 토큰 재발급이
 * 동시에 10번 걸리고, 중간 몇 장만 성공한 상태를 화면이 수습해야 한다.
 */
export function uploadMedia(files: File[], folder: MediaFolder = 'products') {
  const form = new FormData();
  for (const file of files) {
    form.append('files', file);
  }
  form.append('folder', folder);

  return adminRequest<UploadedMedia[]>('/api/admin/media', { method: 'POST', body: form });
}
