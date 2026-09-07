'use client';

import { Alert } from '@/components/admin/Alert';
import { Button } from '@/components/admin/Button';
import { Modal } from '@/components/admin/Modal';
import { StatusBadge } from '@/components/ui/StatusBadge';
import { useMemberDetail } from '@/hooks/admin/useMembers';
import { ApiError } from '@/types/api';
import { MEMBER_STATUS_LABEL, type AdminMemberDetail } from '@/types/member';
import { formatDateTime } from '@/utils/format';
import { MEMBER_STATUS_TONE } from '../AdminMembersView';
import styles from '@/components/admin/admin.module.css';

/**
 * 회원 상세.
 *
 * <h2>여는 것 자체가 기록된다</h2>
 * 이 창에는 이름·이메일·휴대폰이 <b>가려지지 않고</b> 나온다. 서버는 그 조회를
 * {@code MEMBER_VIEW} 로 감사로그에 남긴다 — 그래서 목록을 그리면서 미리 받아 두지 않고,
 * 창을 열 때만 부른다. 열어보지도 않은 회원의 열람 기록이 쌓이면 그 기록이 쓸모없어진다.
 *
 * <h2>고칠 수 있는 것이 없다</h2>
 * 이름·생년월일·휴대폰은 본인확인 응답으로만 채워지는 값이라, 관리자가 손대는 순간
 * "확인된 값"이 아니라 "누군가 적어 넣은 값"이 된다. 잘못됐으면 본인확인을 다시 받는다.
 */
export function MemberDetailDialog({ id, onClose }: { id: number; onClose: () => void }) {
  const { data, isLoading, error } = useMemberDetail(id);

  return (
    <Modal
      open
      title="회원 상세"
      onClose={onClose}
      footer={
        <Button variant="primary" onClick={onClose}>
          닫기
        </Button>
      }
    >
      {error ? (
        <Alert tone="danger" title="회원 정보를 불러오지 못했습니다">
          {error instanceof ApiError ? error.message : '잠시 후 다시 시도해 주세요.'}
        </Alert>
      ) : isLoading || !data ? (
        <p className={styles.empty}>불러오는 중…</p>
      ) : (
        <Body member={data} />
      )}
    </Modal>
  );
}

function Body({ member }: { member: AdminMemberDetail }) {
  return (
    <>
      {/*
        열람 사실이 기록된다는 것을 화면에도 적는다. 서버만 알고 있으면 관리자는
        "여긴 그냥 조회 화면"으로 여기고, 그 인식이 습관을 만든다.
      */}
      <Alert tone="info">
        이 화면의 조회 기록이 감사로그에 남습니다. 회원 정보는 여기서 수정할 수 없습니다.
      </Alert>

      <dl className={styles.rows}>
        <Row label="회원 ID" value={<span className="tabular">{member.id}</span>} />
        <Row
          label="상태"
          value={
            <>
              <StatusBadge tone={MEMBER_STATUS_TONE[member.status]}>
                {MEMBER_STATUS_LABEL[member.status]}
              </StatusBadge>
              {member.locked && (
                <span className={styles.cellSub}>
                  로그인 잠김 · 실패 {member.loginFailCount}회 ·{' '}
                  {formatDateTime(member.lockedAt)}
                </span>
              )}
            </>
          }
        />
        <Row label="이름" value={member.name || '-'} />
        <Row label="이메일" value={member.email || '-'} />
        <Row label="휴대폰" value={<span className="tabular">{member.phone || '-'}</span>} />
        <Row
          label="생년월일"
          value={<span className="tabular">{member.birthDate ?? '-'}</span>}
        />
        <Row label="성별" value={genderLabel(member.gender)} />
        <Row
          label="로그인 수단"
          value={
            <>
              {loginMethods(member)}
              {!member.hasPassword && (
                <span className={styles.cellSub}>
                  비밀번호가 없는 계정입니다 — 소셜로만 가입했습니다
                </span>
              )}
            </>
          }
        />
        <Row
          label="본인확인"
          value={
            member.identityVerified
              ? `완료 · ${formatDateTime(member.phoneVerifiedAt)}`
              : '미완료'
          }
        />
        <Row
          label="이메일 인증"
          value={member.emailVerifiedAt ? formatDateTime(member.emailVerifiedAt) : '기록 없음'}
        />
        <Row
          label="비밀번호 변경"
          value={
            member.passwordChangedAt ? formatDateTime(member.passwordChangedAt) : '기록 없음'
          }
        />
        <Row label="가입" value={formatDateTime(member.createdAt)} />
        <Row label="마지막 변경" value={formatDateTime(member.updatedAt)} />
      </dl>
    </>
  );
}

function Row({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className={styles.row}>
      <dt className={styles.rowLabel}>{label}</dt>
      <dd className={styles.rowValue}>{value}</dd>
    </div>
  );
}

function genderLabel(gender: AdminMemberDetail['gender']): string {
  if (gender === 'MALE') return '남성';
  if (gender === 'FEMALE') return '여성';
  return '-';
}

function loginMethods(member: AdminMemberDetail): string {
  const methods: string[] = [];
  if (member.hasPassword) methods.push('비밀번호');
  methods.push(...member.socialProviders.map(providerLabel));
  return methods.length > 0 ? methods.join(' · ') : '없음';
}

function providerLabel(provider: string): string {
  switch (provider) {
    case 'GOOGLE':
      return '구글';
    case 'NAVER':
      return '네이버';
    case 'KAKAO':
      return '카카오';
    default:
      return provider;
  }
}
