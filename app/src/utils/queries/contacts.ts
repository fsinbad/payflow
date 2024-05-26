import { useQuery } from '@tanstack/react-query';
import axios from 'axios';
import { API_URL } from '../urlConstants';
import { ContactType } from '../../types/ProfleType';
import { sortBySocialScore } from '../../services/socials';

export const useContacts = (enabled: boolean, bypassCache: boolean = false) => {
  return useQuery({
    enabled,
    queryKey: ['contacts'],
    staleTime: Infinity,
    queryFn: () =>
      axios
        .get(`${API_URL}/api/user/me/contacts`, {
          headers: { ...(bypassCache && { 'Cache-Control': 'no-cache' }) },
          withCredentials: true
        })
        .then((res) => {
          const contacts = sortBySocialScore(res.data as ContactType[]);
          console.log('Fetched contacts: ', contacts);
          return contacts;
        })
  });
};
