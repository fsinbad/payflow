import { Stack, Box, Chip, Typography, IconButton, Divider } from '@mui/material';
import { useState, useMemo, useEffect } from 'react';
import { formatUnits } from 'viem';
import { useBalance, useChainId } from 'wagmi';
import { FlowWalletType } from '../types/FlowType';
import { Token, getSupportedTokens } from '../utils/erc20contracts';
import { getNetworkDisplayName } from '../utils/networks';
import { formatAmountWithSuffix, normalizeNumberPrecision } from '../utils/formats';
import { useTokenPrices } from '../utils/queries/prices';
import { NetworkSelectorButton } from './buttons/NetworkSelectorButton';
import { TokenSelectorButton } from './buttons/TokenSelectorButton';
import { FeeSection } from './dialogs/GasFeeSection';
import { PaymentType } from '../types/PaymentType';
import { degen } from 'viem/chains';
import { MdMultipleStop } from 'react-icons/md';
import { TbSend } from 'react-icons/tb';
import { IoMdArrowDropdown, IoMdArrowDropup, IoMdArrowUp } from 'react-icons/io';

export function NetworkTokenSelector({
  payment,
  crossChainMode = false,
  paymentWallet,
  setPaymentWallet,
  compatibleWallets,
  paymentToken,
  setPaymentToken,
  enabledChainCurrencies,
  gasFee,
  showBalance = true,
  expandSection = false
}: {
  payment?: PaymentType;
  crossChainMode?: boolean;
  paymentWallet: FlowWalletType | undefined;
  setPaymentWallet: React.Dispatch<React.SetStateAction<FlowWalletType | undefined>>;
  compatibleWallets: FlowWalletType[];
  paymentToken?: Token;
  setPaymentToken: React.Dispatch<React.SetStateAction<Token | undefined>>;
  enabledChainCurrencies?: string[];
  gasFee?: bigint;
  showBalance?: boolean;
  expandSection?: boolean;
}) {
  const chainId = useChainId();

  const [expand, setExpand] = useState<boolean>(expandSection);
  const [compatibleTokens, setCompatibleTokens] = useState<Token[]>([]);

  const [maxBalance, setMaxBalance] = useState<string>('0.0');
  const [maxBalanceUsd, setMaxBalanceUsd] = useState<string>('0.0');

  const { data: tokenPrices } = useTokenPrices();

  const { isSuccess, data: balance } = useBalance({
    address: paymentWallet?.address,
    chainId,
    token: paymentToken?.tokenAddress,
    query: {
      enabled: showBalance && paymentWallet !== undefined && paymentToken !== undefined,
      gcTime: 3000
    }
  });

  useMemo(async () => {
    if (showBalance) {
      const selectedTokenPrice = paymentToken && tokenPrices?.[paymentToken.id];
      const maxBalance =
        isSuccess && balance ? parseFloat(formatUnits(balance.value, balance.decimals)) : 0;

      const maxBalanceUsd =
        isSuccess && balance
          ? parseFloat(formatUnits(balance.value, balance.decimals)) * (selectedTokenPrice ?? 0)
          : 0;

      setMaxBalance(normalizeNumberPrecision(maxBalance));
      setMaxBalanceUsd(normalizeNumberPrecision(maxBalanceUsd));
    }
  }, [isSuccess, balance, tokenPrices]);

  useEffect(() => {
    // don't update if selected token was already selected
    if (paymentToken && compatibleTokens.find((t) => t === paymentToken)) {
      return;
    }

    setPaymentToken(compatibleTokens[0]);
  }, [paymentToken, compatibleTokens, paymentWallet?.network]);

  console.log('Compatible tokens: ', compatibleTokens);
  console.log('Selected token: ', paymentToken);

  useMemo(() => {
    if (!paymentWallet) {
      return;
    }
    // filter by passed token if available
    const tokens = getSupportedTokens(paymentWallet.network).filter((t) =>
      !crossChainMode && payment?.token ? t.id === payment?.token : true
    );

    setCompatibleTokens(
      enabledChainCurrencies
        ? tokens.filter((t) =>
            enabledChainCurrencies.find(
              (c) =>
                c ===
                `eip155:${paymentWallet.network}/${
                  t.tokenAddress
                    ? `erc20:${t.tokenAddress}`
                    : paymentWallet.network === degen.id
                    ? 'slip44:33436'
                    : 'slip44:60'
                }`
            )
          )
        : tokens
    );
  }, [crossChainMode, paymentWallet, enabledChainCurrencies]);

  return (
    <Stack width="100%">
      <Box
        px={0.5}
        display="flex"
        flexDirection="row"
        justifyContent="space-between"
        alignItems="center">
        <Chip
          icon={crossChainMode ? <MdMultipleStop size={20} /> : <TbSend size={20} />}
          label={crossChainMode ? 'Cross-Chain Payment Token' : 'Payment Token'}
          variant="outlined"
          sx={{ border: 0, fontSize: 13, fontWeight: 500 }}
        />
        {paymentToken ? (
          <Stack direction="row" spacing={0.5} alignItems="center">
            <Typography fontSize={13} fontWeight={500}>
              {getNetworkDisplayName(paymentToken.chainId)} / {paymentToken.id.toUpperCase()}
            </Typography>
            <IconButton size="small" onClick={() => setExpand(!expand)} sx={{ p: 0.3 }}>
              {expand ? <IoMdArrowDropup /> : <IoMdArrowDropdown />}
            </IconButton>
          </Stack>
        ) : (
          <Typography variant="subtitle2">...</Typography>
        )}
      </Box>

      {expand && paymentToken && paymentWallet && compatibleTokens && (
        <>
          <Box
            py={1}
            px={2}
            display="flex"
            flexDirection="column"
            alignItems="stretch"
            justifyContent="flex-start"
            gap={0.5}
            sx={{ borderRadius: 5, border: 1, borderColor: 'divider' }}>
            <Box
              display="flex"
              flexDirection="row"
              justifyContent="space-between"
              alignItems="center">
              <Typography variant="caption" fontWeight={500}>
                Network
              </Typography>
              <Stack direction="row" alignItems="center" spacing={1}>
                <Typography variant="caption" fontWeight="bold">
                  {getNetworkDisplayName(paymentWallet.network)}
                </Typography>
                <NetworkSelectorButton
                  selectedWallet={paymentWallet}
                  setSelectedWallet={setPaymentWallet}
                  wallets={compatibleWallets}
                />
              </Stack>
            </Box>

            <Box
              display="flex"
              flexDirection="row"
              justifyContent="space-between"
              alignItems="center">
              <Typography variant="caption" fontWeight={500}>
                Token
              </Typography>
              <Stack direction="row" alignItems="center" spacing={1}>
                <Typography variant="caption" fontWeight="bold">
                  {paymentToken?.name}
                </Typography>
                <TokenSelectorButton
                  selectedToken={paymentToken}
                  setSelectedToken={setPaymentToken}
                  tokens={compatibleTokens}
                />
              </Stack>
            </Box>

            {showBalance && (
              <Box
                display="flex"
                flexDirection="row"
                justifyContent="space-between"
                alignItems="center">
                <Typography variant="caption" fontWeight={500}>
                  Balance
                </Typography>
                <Typography variant="caption" fontWeight="bold">
                  {`${formatAmountWithSuffix(maxBalance)} ${paymentToken?.name} ≈ $${maxBalanceUsd}`}
                </Typography>
              </Box>
            )}
            {gasFee !== undefined && (
              <FeeSection
                type="gas"
                tooltip="Gas is paid by the sending flow wallet via Gelato SyncFee call method. 
                    The fee includes Gelato onchain call, safe tx fee + deployment fee on the first tx, and 10% Gelato's comission on top of all."
                title="Transaction fee"
                token={paymentToken}
                fee={gasFee}
              />
            )}
            {crossChainMode && (
              <FeeSection
                type="cross-chain"
                tooltip="Charged fee for fasciliating cross-chain payments"
                title="Cross-chain fee"
                token={paymentToken}
                fee={BigInt(0)}
              />
            )}
          </Box>
        </>
      )}
    </Stack>
  );
}
