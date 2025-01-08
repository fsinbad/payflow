import { useQuery } from '@tanstack/react-query';
import { Address, isAddress } from 'viem';
import { PaymentType } from '@payflow/common';
import { ContractState, fetchState } from '@withfabric/protocol-sdks/stpv2';
import axios from 'axios';
import { base } from 'viem/chains';

export interface ContractMetadata {
  image: string;
  external_link: string;
  description: string;
  name: string;
}

export interface HypersubData {
  chainId: number;
  contractAddress: Address;
  state: ContractState;
  metadata: ContractMetadata | null;
}

export async function fetchHypersubData(payment: PaymentType): Promise<HypersubData | null> {
  if (!payment || payment.category !== 'hypersub') return null;

  console.debug('Fetching Hypersub metadata for', payment.token, 'on chain', payment.chainId);

  try {
    const state = await fetchState({
      contractAddress: payment.token as Address,
      chainId: payment.chainId
    });

    let contractMetadata = null;
    if (state.contractURI) {
      try {
        const response = await axios.get(state.contractURI);
        contractMetadata = response.data;
        console.debug('Fetched contract metadata:', contractMetadata);
      } catch (error) {
        console.error('Error fetching contract metadata:', error);
      }
    }

    const data = {
      chainId: payment.chainId,
      contractAddress: payment.token as Address,
      state,
      metadata: contractMetadata
    };

    console.debug('Fetched Hypersub data:', data);

    return data;
  } catch (error) {
    console.error('Error fetching Hypersub metadata:', error);
    return null;
  }
}

export function useHypersubData(payment: PaymentType) {
  const { data: hypersubData, isLoading } = useQuery<HypersubData | null>({
    queryKey: ['hypersubData', payment?.token, payment?.chainId],
    queryFn: () => fetchHypersubData(payment),
    enabled: !!payment && payment.category === 'hypersub'
  });

  return { hypersubData, loading: isLoading };
}

export async function fetchHypersubSearch(searchTerm: string): Promise<HypersubData | null> {
  if (!searchTerm || !isAddress(searchTerm)) return null;

  console.debug('Searching Hypersub for', searchTerm);

  try {
    const state = await fetchState({
      contractAddress: searchTerm as `0x${string}`,
      chainId: base.id
    });

    let contractMetadata = null;
    if (state.contractURI) {
      try {
        const response = await axios.get(state.contractURI);
        contractMetadata = response.data;
        console.debug('Fetched contract metadata:', contractMetadata);
      } catch (error) {
        console.error('Error fetching contract metadata:', error);
      }
    }

    const data = {
      chainId: base.id,
      contractAddress: searchTerm as `0x${string}`,
      state,
      metadata: contractMetadata
    };

    console.debug('Fetched Hypersub data:', data);

    return data;
  } catch (error) {
    console.error('Error fetching Hypersub metadata:', error);
    return null;
  }
}

export function useHypersubSearch(searchTerm: string) {
  const {
    data: hypersubData,
    isLoading,
    isError
  } = useQuery<HypersubData | null>({
    queryKey: ['hypersubSearch', searchTerm],
    queryFn: () => fetchHypersubSearch(searchTerm),
    enabled: !!searchTerm
  });

  return { hypersubData, loading: isLoading, error: isError };
}
