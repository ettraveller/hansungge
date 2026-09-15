'use client';

import { useState } from 'react';

import { Alert } from '../Alert';
import { Button } from '../Button';
import { DecimalInput, Field, TextArea } from '../Field';
import { Modal } from '../Modal';
import { Panel } from '../Panel';
import { ReasonDialog } from '../ReasonDialog';
import { PriceErrorAlert } from './PriceErrorAlert';
import { QuoteChangeTable } from './QuoteChangeTable';
import {
  useDerivations,
  usePreviewDerivation,
  useUpdateDerivation,
} from '@/hooks/admin/usePrices';
import { formatDateTime } from '@/utils/format';
import { METAL_LABEL } from '@/types/price';
import type { PriceDerivation, QuotePlan } from '@/types/admin';
import { ApiError } from '@/types/api';
import styles from '../admin.module.css';

/** 0 초과 1 이하, 소수 넷째 자리까지. 서버도 같은 것을 본다 — 왕복을 아끼려는 것이다. */
const RATIO_PATTERN = /^(0(\.\d{1,4})?|1(\.0{1,4})?)$/;

function ratioIsValid(value: string): boolean {
  return RATIO_PATTERN.test(value) && Number(value) > 0;
}

/**
 * 18K·14K 환산 설정.
 *
 * 18K·14K 는 24K 고시가에 비율을 곱해 낼 수도 있고, 따로 입력할 수도 있다. 업계는 대개
 * 비율 하나를 살 때·팔 때에 똑같이 쓴다(예: 18K 0.735, 14K 0.57).
 *
 * 비율을 바꾸면 지금 24K 기준으로 <b>곧바로 다시 고시</b>된다. 저장만 해 두고 다음 24K
 * 고시를 기다리게 하면, 비율을 고쳤는데 파는 값은 그대로인 상태가 며칠 갈 수 있다.
 */
export function DerivationPanel({ canEdit }: { canEdit: boolean }) {
  const { data, isLoading, error } = useDerivations();
  const update = useUpdateDerivation();
  const [editing, setEditing] = useState<PriceDerivation | null>(null);
  const [toDirect, setToDirect] = useState<PriceDerivation | null>(null);

  return (
    <Panel
      title="18K·14K 환산 설정"
      note="24K 고시가에 비율을 곱해 18K·14K 를 낸다. 비율을 바꾸면 지금 24K 기준으로 바로 다시 고시된다. 직접 입력으로 돌리면 마지막 값이 그대로 남는다."
      flush
    >
      {error && (
        <div className={styles.panelBody}>
          <Alert tone="danger">
            {error instanceof ApiError ? error.message : '환산 설정을 불러오지 못했습니다.'}
          </Alert>
        </div>
      )}

      {isLoading ? (
        <p className={styles.loading}>불러오는 중…</p>
      ) : !data || data.length === 0 ? (
        <p className={styles.empty}>환산 설정이 없습니다.</p>
      ) : (
        <div className={styles.tableWrap}>
          <table className={styles.table}>
            <thead>
              <tr>
                <th>금속</th>
                <th>고시 방식</th>
                <th className={styles.num}>환산 비율</th>
                <th>변경자</th>
                <th>변경 시각</th>
                {canEdit && <th aria-label="비율 변경" />}
              </tr>
            </thead>
            <tbody>
              {data.map((derivation) => (
                <tr key={derivation.metalType}>
                  <td data-label="금속" data-card-title className={styles.strong}>
                    {METAL_LABEL[derivation.metalType] ?? derivation.metalName}
                  </td>
                  <td data-label="고시 방식">
                    <ModeCell
                      derivation={derivation}
                      canEdit={canEdit}
                      onDerive={() => {
                        // 창을 열 때 지난 오류를 지운다. 뮤테이션이 이 패널에 있어서 창만
                        // 닫으면 오류가 남고, 다음에 열었을 때 해결된 오류가 그대로 뜬다.
                        update.reset();
                        setEditing(derivation);
                      }}
                      onDirect={() => {
                        update.reset();
                        setToDirect(derivation);
                      }}
                    />
                  </td>
                  <td data-label="환산 비율" className={styles.num}>
                    {derivation.mode === 'DERIVED' && derivation.ratio != null
                      ? `24K × ${derivation.ratio}`
                      : '-'}
                  </td>
                  <td data-label="변경자" className={styles.muted}>
                    {derivation.updatedBy ?? '-'}
                  </td>
                  <td data-label="변경 시각" className={styles.muted}>
                    {formatDateTime(derivation.updatedAt)}
                  </td>
                  {canEdit && (
                    <td data-label="비율 변경" data-card-actions>
                      {derivation.mode === 'DERIVED' && (
                        <Button
                          small
                          onClick={() => {
                            update.reset();
                            setEditing(derivation);
                          }}
                        >
                          비율 변경
                        </Button>
                      )}
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {editing && (
        // key 로 대상이 바뀌면 입력 상태를 새로 만든다. 이전 금속의 비율이 남아 저장되는 일이 없다.
        <DerivationDialog
          key={editing.metalType}
          derivation={editing}
          onClose={() => setEditing(null)}
        />
      )}

      {toDirect && (
        <ReasonDialog
          key={`${toDirect.metalType}-direct`}
          open
          title={`${METAL_LABEL[toDirect.metalType]} 직접 입력으로 전환`}
          description="환산을 멈춥니다. 지금 나가 있는 값이 그대로 남고, 그다음부터는 직접 고시한 값이 나갑니다."
          confirmLabel="직접 입력으로 전환"
          placeholder="예: 18K 시세 별도 고시"
          maxLength={300}
          pending={update.isPending}
          error={
            update.error instanceof ApiError
              ? update.error.message
              : update.error
                ? '바꾸지 못했습니다.'
                : null
          }
          onConfirm={(reason) =>
            update.mutate(
              { metalType: toDirect.metalType, mode: 'DIRECT', changeReason: reason },
              { onSuccess: () => setToDirect(null) },
            )
          }
          onClose={() => setToDirect(null)}
        />
      )}
    </Panel>
  );
}

/**
 * 지금 방식과 바꾸는 자리. 두 값을 나란히 두고 지금 것을 칠한다.
 *
 * 누르면 곧바로 바뀌지 않고 창이 열린다 — 환산으로 켤 때는 비율을, 끌 때는 사유를
 * 받아야 하기 때문이다. 그래서 토글 스위치가 아니라 버튼 두 개다.
 */
function ModeCell({
  derivation,
  canEdit,
  onDerive,
  onDirect,
}: {
  derivation: PriceDerivation;
  canEdit: boolean;
  onDerive: () => void;
  onDirect: () => void;
}) {
  const derived = derivation.mode === 'DERIVED';

  if (!canEdit) {
    return <span>{derived ? '24K 환산' : '직접 입력'}</span>;
  }

  return (
    <div className={styles.segment} role="group" aria-label="고시 방식">
      <button
        type="button"
        className={!derived ? styles.segmentOnDirect : styles.segmentOff}
        aria-pressed={!derived}
        disabled={!derived}
        onClick={onDirect}
      >
        직접 입력
      </button>
      <button
        type="button"
        className={derived ? styles.segmentOnDerived : styles.segmentOff}
        aria-pressed={derived}
        disabled={derived}
        onClick={onDerive}
      >
        24K 환산
      </button>
    </div>
  );
}

/**
 * 환산 비율 정하기 — 바뀔 값을 먼저 보고 적용한다.
 *
 * 비율은 숫자 하나지만 결과는 18K 살 때·팔 때 두 값이 바로 바뀌는 것이다. 0.735 를
 * 0.0735 로 치면 값이 10분의 1 로 나간다. 그래서 적용 버튼은 <b>지금 입력한 비율로 계산한
 * 결과를 본 뒤에만</b> 눌린다. 비율을 고치면 다시 봐야 한다.
 */
function DerivationDialog({
  derivation,
  onClose,
}: {
  derivation: PriceDerivation;
  onClose: () => void;
}) {
  const preview = usePreviewDerivation();
  const update = useUpdateDerivation();

  const [ratio, setRatio] = useState(derivation.ratio == null ? '' : String(derivation.ratio));
  const [reason, setReason] = useState('');
  const [plan, setPlan] = useState<{ ratio: string; result: QuotePlan } | null>(null);

  const label = METAL_LABEL[derivation.metalType];
  const ratioInvalid = ratio !== '' && !ratioIsValid(ratio);
  // 본 결과가 지금 입력한 비율의 것일 때만 적용할 수 있다.
  const seen = plan != null && plan.ratio === ratio ? plan.result : null;
  const canPreview = ratioIsValid(ratio) && !preview.isPending;
  const canApply = seen != null && reason.trim().length > 0 && !update.isPending;

  const runPreview = () => {
    if (!canPreview) return;
    update.reset();
    preview.mutate(
      { metalType: derivation.metalType, ratio },
      { onSuccess: (result) => setPlan({ ratio, result }) },
    );
  };

  const apply = () => {
    if (!canApply || seen == null) return;
    update.mutate(
      {
        metalType: derivation.metalType,
        mode: 'DERIVED',
        ratio,
        changeReason: reason.trim(),
        // 기준을 넘는 값이 표에 짚여 있는 상태에서 누르는 것이 곧 확인이다.
        confirmLargeChange: seen.needsConfirm,
      },
      { onSuccess: onClose },
    );
  };

  return (
    <Modal
      open
      title={`${label} 24K 환산 비율`}
      onClose={onClose}
      footer={
        <>
          <Button onClick={onClose} disabled={update.isPending}>
            취소
          </Button>
          <Button
            variant={seen?.needsConfirm ? 'danger' : 'primary'}
            disabled={!canApply}
            onClick={apply}
          >
            {update.isPending ? '적용 중…' : seen?.needsConfirm ? '확인하고 적용' : '적용'}
          </Button>
        </>
      }
    >
      <div className={styles.form}>
        <PriceErrorAlert error={preview.error ?? update.error} fallback="적용하지 못했습니다." />

        <Field
          label="환산 비율"
          htmlFor="derive-ratio"
          required
          hint="24K 고시가에 곱할 값. 0보다 크고 1 이하, 소수 넷째 자리까지. 예: 18K 0.735, 14K 0.57"
          error={ratioInvalid ? '0보다 크고 1 이하인 숫자를 소수 넷째 자리까지 입력해 주세요.' : null}
        >
          <DecimalInput
            id="derive-ratio"
            value={ratio}
            invalid={ratioInvalid}
            placeholder="0.735"
            onChange={(e) => setRatio(e.target.value.trim())}
            onKeyDown={(e) => {
              if (e.key === 'Enter') {
                e.preventDefault();
                runPreview();
              }
            }}
          />
        </Field>

        <div className={styles.formActions}>
          <Button small onClick={runPreview} disabled={!canPreview}>
            {preview.isPending ? '계산 중…' : '바뀔 값 보기'}
          </Button>
          {!seen && <span className={styles.hint}>바뀔 값을 확인해야 적용할 수 있습니다.</span>}
        </div>

        {seen && seen.lines.length === 0 && (
          <Alert tone="info" title="24K 시세가 아직 고시되지 않았습니다">
            설정만 저장됩니다. 24K 를 고시하면 이 비율로 {label}도 함께 고시됩니다.
          </Alert>
        )}

        {seen && seen.lines.length > 0 && (
          <>
            {seen.needsConfirm && (
              <Alert tone="warning" title={`직전 고시보다 ${seen.limitPercent}% 넘게 바뀌는 값이 있습니다`}>
                비율이 맞는지 한 번 더 확인해 주세요. 적용하면 바로 고시됩니다.
              </Alert>
            )}
            <QuoteChangeTable lines={seen.lines} />
          </>
        )}

        <Field label="변경 사유" htmlFor="derive-reason" required hint="감사로그와 시세 이력에 남는다.">
          <TextArea
            id="derive-reason"
            value={reason}
            maxLength={300}
            placeholder="예: 18K 환산 비율 확정"
            onChange={(e) => setReason(e.target.value)}
          />
        </Field>
      </div>
    </Modal>
  );
}
