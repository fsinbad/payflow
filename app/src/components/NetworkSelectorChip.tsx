import { SelectAll } from '@mui/icons-material';
import { ChipProps, Chip, Avatar, Typography, useMediaQuery, useTheme } from '@mui/material';
import { Chain } from 'viem';
import { SafeWalletType } from '../types/FlowType';
import { useNetwork } from 'wagmi';
import getNetworkImageSrc from '../utils/networkImages';

export default function NetworkSelectorChip(
  props: ChipProps & {
    wallet?: SafeWalletType;
    selectedNetwork: Chain | undefined;
    setSelectedNetwork: React.Dispatch<React.SetStateAction<Chain | undefined>>;
  }
) {
  const theme = useTheme();
  const smallScreen = useMediaQuery(theme.breakpoints.down('sm'));

  const { chains } = useNetwork();

  const { wallet, selectedNetwork, setSelectedNetwork } = props;
  return (
    <Chip
      {...props}
      clickable
      icon={
        wallet ? (
          <Avatar src={getNetworkImageSrc(wallet.network)} sx={{ width: 20, height: 20 }} />
        ) : (
          <SelectAll />
        )
      }
      label={
        <Typography variant={!smallScreen ? 'subtitle2' : 'caption'}>
          {wallet ? wallet.network : 'All networks'}
        </Typography>
      }
      onClick={async () => {
        if (wallet) {
          setSelectedNetwork(chains.find((c) => c.name === wallet.network));
        } else {
          setSelectedNetwork(undefined);
        }
      }}
      sx={{
        backgroundColor: (
          wallet ? selectedNetwork?.name === wallet.network : selectedNetwork === undefined
        )
          ? ''
          : 'inherit'
      }}
    />
  );
}
