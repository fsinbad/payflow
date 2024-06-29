import { useContext, useMemo, useRef, useState } from 'react';
import { useAccount, useBalance, useChainId, useClient, useWalletClient } from 'wagmi';
import { Id, toast } from 'react-toastify';

import {
  Account,
  Address,
  Chain,
  Client,
  Transport,
  WalletClient,
  encodeFunctionData,
  erc20Abi,
  parseUnits
} from 'viem';

import { FlowType, FlowWalletType } from '../../types/FlowType';
import { ProfileType } from '../../types/ProfleType';
import { SafeAccountConfig } from '@safe-global/protocol-kit';
import { ProfileContext } from '../../contexts/UserContext';
import { SafeVersion } from '@safe-global/safe-core-sdk-types';
import { useSafeTransfer } from '../../utils/hooks/useSafeTransfer';
import { updateWallet } from '../../services/flow';
import { TransferToastContent } from '../toasts/TransferToastContent';
import { LoadingSwitchNetworkButton } from '../buttons/LoadingSwitchNetworkButton';
import { LoadingPaymentButton } from '../buttons/LoadingPaymentButton';
import { PaymentDialogProps } from './PaymentDialog';
import { Token } from '../../utils/erc20contracts';
import { TokenAmountSection } from './TokenAmountSection';
import { SwitchFlowSignerSection } from './SwitchFlowSignerSection';
import { useCompatibleWallets, useToAddress } from '../../utils/hooks/useCompatibleWallets';
import { completePayment } from '../../services/payments';
import { NetworkTokenSelector } from '../NetworkTokenSelector';
import { useRegularTransfer } from '../../utils/hooks/useRegularTransfer';

export default function PayWithPayflowDialog({ payment, sender, recipient }: PaymentDialogProps) {
  const flow = sender.identity.profile?.defaultFlow as FlowType;

  const { profile } = useContext(ProfileContext);

  const { data: signer } = useWalletClient();
  const client = useClient();

  const { address } = useAccount();
  const chainId = useChainId();

  const [paymentPending, setPaymentPending] = useState<boolean>(false);
  const [paymentEnabled, setPaymentEnabled] = useState<boolean>(false);

  const compatibleWallets = useCompatibleWallets({ sender: flow, recipient, payment });
  const [selectedWallet, setSelectedWallet] = useState<FlowWalletType>();
  const [selectedToken, setSelectedToken] = useState<Token>();

  const toAddress = useToAddress({ recipient, selectedWallet });

  const [sendAmount, setSendAmount] = useState<number | undefined>(payment?.tokenAmount);
  const [sendAmountUSD, setSendAmountUSD] = useState<number | undefined>(payment?.usdAmount);
  const sendToastId = useRef<Id>();

  const { loading, confirmed, error, status, txHash, transfer, reset } = useSafeTransfer();

  const {
    loading: loadingRegular,
    confirmed: confirmedRegular,
    error: errorRegular,
    status: statusRegular,
    txHash: txHashRegular,
    sendTransaction,
    writeContract,
    reset: resetRegular
  } = useRegularTransfer();

  // force to display sponsored
  const [gasFee] = useState<bigint | undefined>(
    flow.type !== 'FARCASTER_VERIFICATION' ? BigInt(0) : undefined
  );

  // TODO: use pre-configured tokens to fetch decimals, etc
  const { isSuccess, data: balance } = useBalance({
    address: selectedWallet?.address,
    chainId,
    token: selectedToken?.tokenAddress,
    query: {
      enabled: selectedWallet !== undefined && selectedToken !== undefined,
      gcTime: 5000
    }
  });

  useMemo(async () => {
    if (compatibleWallets.length === 0) {
      setSelectedWallet(undefined);
      return;
    }
    setSelectedWallet(compatibleWallets.find((w) => w.network === chainId) ?? compatibleWallets[0]);
  }, [compatibleWallets, chainId]);

  useMemo(async () => {
    if (!sendAmountUSD || !selectedWallet) {
      return;
    }

    const loadingCombined = flow.type !== 'FARCASTER_VERIFICATION' ? loading : loadingRegular;
    const confirmedCombined = flow.type !== 'FARCASTER_VERIFICATION' ? confirmed : confirmedRegular;
    const errorCombined = flow.type !== 'FARCASTER_VERIFICATION' ? error : errorRegular;
    const statusCombined = flow.type !== 'FARCASTER_VERIFICATION' ? status : statusRegular;
    const txHashCombined = flow.type !== 'FARCASTER_VERIFICATION' ? txHash : txHashRegular;

    if (loadingCombined && !sendToastId.current) {
      toast.dismiss();
      sendToastId.current = toast.loading(
        <TransferToastContent from={sender} to={recipient} usdAmount={sendAmountUSD ?? 0} />
      );
    }

    if (!sendToastId.current) {
      return;
    }

    if (confirmedCombined && txHashCombined) {
      console.debug('Confirmed with txHas: ', txHashCombined);

      toast.update(sendToastId.current, {
        render: (
          <TransferToastContent from={sender} to={recipient} usdAmount={sendAmountUSD ?? 0} />
        ),
        type: 'success',
        isLoading: false,
        autoClose: 5000
      });
      sendToastId.current = undefined;

      if (payment?.referenceId) {
        payment.hash = txHashCombined;
        completePayment(payment);
      }

      // if tx was successfull, mark wallet as deployed if it wasn't
      if (flow.type !== 'FARCASTER_VERIFICATION' && !selectedWallet.deployed) {
        selectedWallet.deployed = true;
        updateWallet(flow.uuid, selectedWallet);
      }
    } else if (errorCombined) {
      toast.update(sendToastId.current, {
        render: (
          <TransferToastContent
            from={sender}
            to={recipient}
            usdAmount={sendAmountUSD ?? 0}
            status="error"
          />
        ),
        type: 'error',
        isLoading: false,
        autoClose: 5000
      });
      sendToastId.current = undefined;

      if (statusCombined === 'insufficient_fees') {
        toast.error('Insufficient gas fees', { closeButton: false, autoClose: 5000 });
      }

      if (statusCombined?.includes('gas_sponsorship_failure')) {
        toast.error(`Failed to sponsor tx: ${statusCombined.split(':')[1]}`, {
          closeButton: false,
          autoClose: 5000
        });
      }
    }
  }, [
    loading,
    confirmed,
    error,
    status,
    txHash,
    loadingRegular,
    confirmedRegular,
    errorRegular,
    statusRegular,
    txHashRegular,
    sendAmountUSD
  ]);

  async function submitTransaction() {
    if (
      selectedWallet &&
      toAddress &&
      sendAmount &&
      selectedToken &&
      balance &&
      client &&
      signer &&
      profile
    ) {
      if (flow.type !== 'FARCASTER_VERIFICATION') {
        await sendSafeTransaction(
          client,
          signer,
          profile,
          flow,
          selectedWallet,
          toAddress,
          parseUnits(sendAmount.toString(), balance.decimals)
        );
      } else {
        if (selectedToken.tokenAddress) {
          writeContract?.({
            abi: erc20Abi,
            address: selectedToken.tokenAddress,
            functionName: 'transfer',
            args: [toAddress, parseUnits(sendAmount.toString(), balance.decimals)]
          });
        } else {
          sendTransaction?.({
            to: toAddress,
            value: parseUnits(sendAmount.toString(), balance.decimals)
          });
        }
      }
    } else {
      toast.error("Can't send to this profile");
    }
  }

  async function sendSafeTransaction(
    client: Client<Transport, Chain>,
    signer: WalletClient<Transport, Chain, Account>,
    profile: ProfileType,
    flow: FlowType,
    from: FlowWalletType,
    to: Address,
    amount: bigint
  ) {
    reset();

    const txData =
      selectedToken && selectedToken.tokenAddress
        ? {
            from: from.address,
            to: selectedToken.tokenAddress,
            data: encodeFunctionData({
              abi: erc20Abi,
              functionName: 'transfer',
              args: [to, amount]
            })
          }
        : {
            from: from.address,
            to,
            value: amount
          };

    // TODO: hard to figure out if there 2 signers or one, for now consider if signerProvider not specified - 1, otherwise - 2
    const owners = [];
    if (flow.signerProvider && flow.signer.toLowerCase() !== profile.identity.toLowerCase()) {
      owners.push(profile.identity);
    }
    owners.push(flow.signer);

    const safeAccountConfig: SafeAccountConfig = {
      owners,
      threshold: 1
    };

    const saltNonce = flow.saltNonce as string;
    const safeVersion = from.version as SafeVersion;

    transfer(client, signer, txData, safeAccountConfig, safeVersion, saltNonce);
  }

  useMemo(async () => {
    if (flow.type !== 'FARCASTER_VERIFICATION') {
      setPaymentPending(Boolean(loading || (txHash && !confirmed && !error)));
    } else {
      setPaymentPending(
        Boolean(loadingRegular || (txHashRegular && !confirmedRegular && !errorRegular))
      );
    }
  }, [
    loading,
    txHash,
    confirmed,
    error,
    loadingRegular,
    txHashRegular,
    confirmedRegular,
    errorRegular
  ]);

  useMemo(async () => {
    setPaymentEnabled(Boolean(toAddress && sendAmount));
  }, [toAddress, sendAmount]);

  return (
    <>
      {address?.toLowerCase() === flow.signer.toLowerCase() ? (
        selectedWallet && (
          <>
            <TokenAmountSection
              payment={payment}
              selectedWallet={selectedWallet}
              selectedToken={selectedToken}
              sendAmount={sendAmount}
              setSendAmount={setSendAmount}
              sendAmountUSD={sendAmountUSD}
              setSendAmountUSD={setSendAmountUSD}
            />
            <NetworkTokenSelector
              payment={payment}
              selectedWallet={selectedWallet}
              setSelectedWallet={setSelectedWallet}
              selectedToken={selectedToken}
              setSelectedToken={setSelectedToken}
              compatibleWallets={compatibleWallets}
              gasFee={gasFee}
            />
            {chainId === selectedWallet.network ? (
              <LoadingPaymentButton
                title="Pay"
                loading={paymentPending}
                disabled={!paymentEnabled}
                status={flow.type !== 'FARCASTER_VERIFICATION' ? status : statusRegular}
                onClick={submitTransaction}
              />
            ) : (
              <LoadingSwitchNetworkButton chainId={selectedWallet.network} />
            )}
          </>
        )
      ) : (
        <SwitchFlowSignerSection flow={flow} />
      )}
    </>
  );
}
