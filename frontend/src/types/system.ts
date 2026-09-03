/** 백엔드 HealthResponse 와 대응 */
export interface HealthResponse {
  service: string;
  version: string;
  profile: string;
  /** DB 연결 및 Flyway 이력 조회 결과 */
  database: 'UP' | 'DOWN';
  /** 적용된 마지막 마이그레이션 버전 */
  schemaVersion: string;
  /** 적용 중인 반올림 정책 */
  roundingPolicy: string;
  /** 반올림 정책이 A-2 확정 전 임시값인지 */
  roundingPolicyTemporary: boolean;
  serverTime: string;
}
