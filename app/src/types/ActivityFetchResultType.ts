import { Address, Hash } from 'viem';
import { FlowWalletType } from './FlowType';
import { ProfileType } from './ProfleType';

export type ActivityFetchResultType = {
  isLoading: boolean;
  isFetched: boolean;
  transactions: TxInfo[] | undefined;
};

export interface TxInfo {
  chainId: number;
  block: number;
  hash: Hash;
  timestamp: string;
  success: boolean;
  from: Address;
  to: Address;
  type: string | number;
  value: number;
  token?: TxToken;
  activity: 'inbound' | 'outbound' | 'self';
  fromProfile?: ProfileType;
  toProfile?: ProfileType;
  payment?: PaymentType;
}

export interface TxToken {
  address: Address;
  decimals: number;
  exchange_rate: number;
  name: string;
  symbol: string;
  type: string;
}

export interface PaymentType {
  hash: Hash;
  source: { app: string; ref?: string };
  comment?: string;
}

export type WalletActivityType = { wallet: FlowWalletType; txs: TxInfo[] | undefined };
