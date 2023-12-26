import { Avatar, Stack, Typography } from '@mui/material';
import { MetaType } from '../types/ProfleType';
import AddressAvatar from './AddressAvatar';
import { shortenWalletAddressLabel } from '../utils/address';
import { useEnsAvatar, useEnsName } from 'wagmi';

export function AddressSection(props: { meta: MetaType; fontSize?: number; maxWidth?: number }) {
  const { meta, fontSize, maxWidth } = props;

  const { data: ensName } = useEnsName({
    enabled: !meta.ens,
    address: meta.addresses[0],
    chainId: 1,
    cacheTime: 300_000
  });

  const avatar = useEnsAvatar({
    enabled: !meta.ensAvatar && (meta.ens !== undefined || ensName !== undefined),
    name: meta.ens ?? ensName,
    chainId: 1,
    cacheTime: 300_000
  });

  return (
    <Stack maxWidth={maxWidth ?? 130} direction="row" spacing={0.5} alignItems="center">
      {meta.ensAvatar || (avatar.isSuccess && avatar.data) ? (
        <Avatar src={meta.ensAvatar ?? (avatar.isSuccess && avatar.data ? avatar.data : '')} />
      ) : (
        <AddressAvatar address={meta.addresses[0] ?? '0x'} />
      )}
      <Stack
        minWidth={75}
        spacing={0.1}
        alignItems="flex-start"
        overflow="auto"
        sx={{
          scrollbarWidth: 'none', // Hide the scrollbar for firefox
          '&::-webkit-scrollbar': {
            display: 'none' // Hide the scrollbar for WebKit browsers (Chrome, Safari, Edge, etc.)
          },
          '&-ms-overflow-style:': {
            display: 'none' // Hide the scrollbar for IE
          }
        }}>
        <Typography noWrap variant="subtitle2" fontSize={fontSize}>
          {shortenWalletAddressLabel(meta.addresses[0])}
        </Typography>
        {(meta.ens || ensName) && (
          <Typography noWrap variant="caption">
            {meta.ens ?? ensName}
          </Typography>
        )}
      </Stack>
    </Stack>
  );
}
