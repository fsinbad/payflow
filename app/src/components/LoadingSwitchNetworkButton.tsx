import LoadingButton, { LoadingButtonProps } from '@mui/lab/LoadingButton';
import { useEffect } from 'react';
import { useSwitchNetwork } from 'wagmi';

export function LoadingSwitchNetworkButton({
  chainId,
  ...props
}: { chainId: number } & LoadingButtonProps) {
  const { switchNetwork, isLoading, pendingChainId, isError, error } = useSwitchNetwork();

  useEffect(() => {
    if (chainId !== pendingChainId) {
      switchNetwork?.(chainId);
    }
  }, [chainId, pendingChainId, isError, error]);

  return (
    <LoadingButton
      {...props}
      fullWidth
      variant="outlined"
      loading={isLoading}
      size="large"
      color="primary"
      onClick={() => {
        if (isError || chainId !== pendingChainId) {
          switchNetwork?.(chainId);
        }
      }}
      sx={{ mt: 3, mb: 1, borderRadius: 5 }}>
      Switch Network
    </LoadingButton>
  );
}
