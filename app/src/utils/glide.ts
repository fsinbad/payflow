import { createGlideConfig, PaymentOption } from '@paywithglide/glide-js';
import { base, optimism, degen, arbitrum, mode, zora, ham } from 'wagmi/chains';
import { Token } from '@payflow/common';
import { PaymentCategory, PaymentType } from '../types/PaymentType';

export const glideConfig = createGlideConfig({
  projectId: import.meta.env.VITE_GLIDE_API_KEY,
  chains: [base, optimism, zora, degen, arbitrum, mode, ham]
});

export const getPaymentOption = (
  paymentOptions: PaymentOption[] | undefined,
  paymentToken: Token | undefined
) => {
  if (!paymentOptions || !paymentToken) return null;
  return paymentOptions.find(
    (option) =>
      option.paymentCurrency.toLowerCase() ===
      `eip155:${paymentToken.chainId}/${
        paymentToken.tokenAddress
          ? `erc20:${paymentToken.tokenAddress}`
          : paymentToken.chainId === degen.id
            ? 'slip44:33436'
            : 'slip44:60'
      }`.toLowerCase()
  );
};

export const getCommissionUSD = (payment?: PaymentType) => {
  switch (payment?.category) {
    case 'fc_storage':
      return payment?.tokenAmount ? 0.5 + (payment.tokenAmount - 1) * 0.1 : 0.5;
    default:
      return 0.05;
  }
};
