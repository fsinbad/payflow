/* eslint-disable jsx-a11y/alt-text */
import { PaymentType } from '../types/PaymentType';
import { IdentityType } from '../types/ProfleType';
import { shortenWalletAddressLabel } from '../utils/address';
import { assetImageSrc } from '../utils/image';
import getNetworkImageSrc, { getNetworkDisplayName } from '../utils/networks';
import { tokens as ERC20_CONTRACTS } from '@payflow/common';
import { formatNumberWithSuffix } from '../utils/format';
import Card from './Card';

type PaymentStep = 'create' | 'start' | 'command' | 'confirm' | 'execute';

export const paymentHtml = (
  identity: IdentityType,
  step: PaymentStep,
  payment: PaymentType,
  entryTitle?: string
) => <Payment identity={identity} step={step} payment={payment} entryTitle={entryTitle} />;

const paymentStepTitle = (step: PaymentStep, entryTitle?: string) => {
  switch (step) {
    case 'create':
      return '`Your payment title`';
    case 'start':
      return entryTitle ?? '👋🏻 Pay Me';
    case 'command':
      return 'Enter payment token details';
    case 'confirm':
      return 'Complete payment';
    case 'execute':
      return 'Payment details';
  }
};

function Payment({
  identity,
  step,
  payment,
  entryTitle
}: {
  identity: IdentityType;
  step: PaymentStep;
  payment: PaymentType;
  entryTitle?: string;
}) {
  const title = paymentStepTitle(step, entryTitle);
  const tokenImgSrc =
    payment.token &&
    (ERC20_CONTRACTS.find((t) => t.chainId === payment.chainId && t.id === payment.token)
      ?.imageURL ??
      assetImageSrc(`/assets/coins/${payment.token}.png`));

  const farcasterSocial = identity?.meta?.socials?.find((s) => s.dappName === 'farcaster');
  const profileDisplayName = identity?.profile?.displayName ?? farcasterSocial?.profileDisplayName;
  const profileUsername = identity?.profile?.username ?? farcasterSocial?.profileName;
  const profileImage = identity?.profile?.profileImage ?? farcasterSocial?.profileImage;

  const isPaymentInitiated = step !== 'create' && step !== 'start' && step !== 'command';
  const showPreferredTokens =
    (step === 'start' || step === 'command') &&
    identity.profile?.preferredTokens &&
    identity.profile.preferredTokens.length > 0;
  const maxNameWidth = isPaymentInitiated || showPreferredTokens ? 450 : 600;

  return (
    <Card>
      <p style={{ fontSize: 60, fontWeight: 'bold', fontStyle: 'italic' }}>{title}</p>
      <div
        style={{
          marginTop: 20,
          display: 'flex',
          flexDirection: 'row',
          alignItems: 'center',
          justifyContent: 'flex-start'
        }}>
        {profileImage && (
          <img
            src={profileImage}
            alt="profile"
            style={{ height: 250, width: 250, margin: 10, borderRadius: 25 }}
          />
        )}
        {profileUsername && profileDisplayName ? (
          <div style={{ margin: 10, display: 'flex', flexDirection: 'column' }}>
            <span
              style={{
                fontSize: 64,
                fontWeight: 'bold',
                whiteSpace: 'nowrap',
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                maxWidth: maxNameWidth
              }}>
              {profileDisplayName}
            </span>
            <span
              style={{
                marginTop: 10,
                fontSize: 64,
                fontWeight: 'normal',
                whiteSpace: 'nowrap',
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                maxWidth: maxNameWidth
              }}>
              @{profileUsername}
            </span>
          </div>
        ) : (
          <div style={{ margin: 10, display: 'flex', flexDirection: 'column' }}>
            <span style={{ marginTop: 10, fontSize: 64, fontWeight: 'normal' }}>
              {shortenWalletAddressLabel(identity?.address)}
            </span>
          </div>
        )}
        {isPaymentInitiated && (
          <div
            style={{
              margin: 10,
              minWidth: 250,
              maxWidth: 300,
              height: 230,
              display: 'flex',
              flexDirection: 'column',
              justifyContent: 'flex-start',
              alignItems: 'flex-start',
              padding: 16,
              fontSize: 36,
              backgroundColor: '#e0e0e0',
              borderRadius: 25,
              gap: 10
            }}>
            {payment.chainId && (
              <div
                style={{
                  display: 'flex',
                  flexDirection: 'row',
                  justifyContent: 'center',
                  alignItems: 'center',
                  gap: 10
                }}>
                <img
                  src={assetImageSrc(getNetworkImageSrc(payment.chainId as number))}
                  style={{ width: 36, height: 36, borderRadius: '50%' }}
                />
                <span style={{ fontWeight: 'bold' }}>{getNetworkDisplayName(payment.chainId)}</span>
              </div>
            )}
            {payment.token && (
              <div
                style={{
                  display: 'flex',
                  flexDirection: 'row',
                  justifyContent: 'center',
                  alignItems: 'center',
                  gap: 10
                }}>
                <img src={tokenImgSrc} style={{ width: 36, height: 36, borderRadius: '50%' }} />
                <span style={{ fontWeight: 'bold', textTransform: 'uppercase' }}>
                  <b>{payment.token}</b>
                </span>
              </div>
            )}
            {payment.usdAmount && payment.tokenAmount && (
              <div
                style={{
                  display: 'flex',
                  flexDirection: 'row',
                  justifyContent: 'center',
                  alignItems: 'center'
                }}>
                <span>
                  <b>${payment.usdAmount} ≈ </b>
                </span>
                <span>
                  <b>{formatNumberWithSuffix(payment.tokenAmount)}</b>
                </span>
              </div>
            )}
            <span>
              <b>
                {payment.status
                  ? payment.status === 'success'
                    ? '✅ Success'
                    : '❌ Failed'
                  : '⏳ Pending'}
              </b>
            </span>
          </div>
        )}
        {showPreferredTokens && (
          <div
            style={{
              margin: 10,
              maxWidth: 300,
              minHeight: 200,
              maxHeight: 300,
              display: 'flex',
              flexDirection: 'column',
              justifyContent: 'flex-start',
              alignItems: 'flex-start',
              padding: 16,
              fontSize: 36,
              backgroundColor: '#e0e0e0',
              borderRadius: '16px',
              gap: 5
            }}>
            <span style={{ textAlign: 'center', fontSize: 30, fontWeight: 'bold' }}>Preferred</span>
            {identity.profile?.preferredTokens?.slice(0, 5).map((tokenId) => {
              const token = ERC20_CONTRACTS.find((t) => t.id === tokenId);
              const tokenImgSrc = token?.imageURL ?? assetImageSrc(`/assets/coins/${tokenId}.png`);
              return (
                <div
                  key={`preferred_token_${tokenId}`}
                  style={{
                    display: 'flex',
                    flexDirection: 'row',
                    justifyContent: 'center',
                    alignItems: 'center',
                    gap: 10
                  }}>
                  <img src={tokenImgSrc} style={{ width: 28, height: 28, borderRadius: '50%' }} />
                  <span style={{ fontSize: 28, textTransform: 'uppercase' }}>{tokenId}</span>
                </div>
              );
            })}
          </div>
        )}
      </div>
    </Card>
  );
}
