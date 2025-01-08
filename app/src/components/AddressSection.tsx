import { Avatar, Badge, Stack, Typography } from '@mui/material';
import { IdentityType } from '@payflow/common';
import AddressAvatar from './avatars/AddressAvatar';
import { shortenWalletAddressLabel } from '../utils/address';
import { useEnsAvatar, useEnsName } from 'wagmi';
import CopyToClipboardIconButton from './buttons/CopyToClipboardIconButton';
import { useDegenName } from '../utils/queries/domainNames';
import FarcasterAvatar from './avatars/FarcasterAvatar';
import LensAvatar from './avatars/LensAvatar';

export function AddressSection(props: {
  identity: IdentityType;
  fontSize?: number;
  maxWidth?: number;
  copy?: boolean;
}) {
  const { identity, fontSize, maxWidth, copy = true } = props;

  const social = identity.meta?.socials.sort((a, b) => b.followerCount - a.followerCount)[0];

  const { data: ensName, isFetched } = useEnsName({
    address: identity.address,
    chainId: 1,
    query: {
      enabled: !social && !identity.meta?.ens,
      staleTime: 300_000
    }
  });

  const { data: degenName } = useDegenName(identity.address, isFetched && !ensName);

  const ensAvatar = useEnsAvatar({
    name: identity.meta?.ens ?? (ensName as string),
    chainId: 1,
    query: {
      enabled:
        !social &&
        !identity.meta?.ensAvatar &&
        (identity.meta?.ens !== undefined || ensName !== undefined),
      staleTime: 300_000
    }
  });

  const username =
    identity.meta?.socials[0]?.profileName.replace('lens/@', '') ||
    identity.meta?.ens ||
    ensName ||
    degenName;

  const icon =
    social &&
    (social.dappName === 'farcaster' ? <FarcasterAvatar size={15} /> : <LensAvatar size={15} />);

  return (
    <Stack maxWidth={maxWidth ?? 130} direction="row" spacing={0.5} alignItems="center">
      {social || identity.meta?.ensAvatar || (ensAvatar.isSuccess && ensAvatar.data) ? (
        <Badge
          anchorOrigin={{
            vertical: 'bottom',
            horizontal: 'right'
          }}
          overlap="circular"
          badgeContent={icon}>
          <Avatar
            src={
              social?.profileImage ??
              identity.meta?.ensAvatar ??
              (ensAvatar.isSuccess && ensAvatar.data ? ensAvatar.data : '')
            }
          />
        </Badge>
      ) : (
        <AddressAvatar address={identity.address} />
      )}
      <Stack
        minWidth={75}
        spacing={0.1}
        alignItems="flex-start"
        sx={{
          overflowX: 'scroll',
          scrollbarWidth: 'auto', // Hide the scrollbar for firefox
          '&::-webkit-scrollbar': {
            display: 'none' // Hide the scrollbar for WebKit browsers (Chrome, Safari, Edge, etc.)
          },
          '&-ms-overflow-style:': {
            display: 'none' // Hide the scrollbar for IE
          },
          '-webkit-overflow-scrolling': 'touch' // Improve scrolling on iOS
        }}>
        {username && (
          <Typography noWrap variant="subtitle2" fontSize={fontSize}>
            {username}
          </Typography>
        )}

        <Stack direction="row" spacing={0.1} alignItems="center">
          <Typography
            noWrap
            variant={username ? 'caption' : 'subtitle2'}
            {...(!username && { fontSize })}>
            {shortenWalletAddressLabel(identity.address)}
          </Typography>
          {copy && <CopyToClipboardIconButton tooltip="Copy address" value={identity.address} />}
        </Stack>
      </Stack>
    </Stack>
  );
}
