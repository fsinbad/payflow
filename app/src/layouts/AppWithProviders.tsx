import App from './App';

import { useEffect, useMemo, useRef, useState } from 'react';
import { AppSettings } from '../types/AppSettingsType';
import { me } from '../services/user';
import { ProfileType } from '../types/ProfileType';
import sortAndFilterFlows from '../utils/sortAndFilterFlows';
import { toast } from 'react-toastify';
import axios from 'axios';
import { useSearchParams } from 'react-router-dom';
import LoadingPayflowEntryLogo from '../components/LoadingPayflowEntryLogo';

const appSettingsStorageItem = localStorage.getItem('appSettings');
const appSettingsStored = appSettingsStorageItem
  ? (JSON.parse(appSettingsStorageItem) as AppSettings)
  : null;

export default function AppWithProviders() {
  const [appSettings, setAppSettings] = useState<AppSettings>(
    appSettingsStored ? appSettingsStored : {}
  );

  const fetchingStatusRef = useRef(false);
  const [loading, setLoading] = useState<boolean>(true);
  const [profile, setProfile] = useState<ProfileType>();

  const accessToken = useSearchParams()[0].get('access_token') ?? undefined;

  // Fetch user when:
  useEffect(() => {
    const fetchStatus = async () => {
      if (fetchingStatusRef.current) {
        return;
      }

      fetchingStatusRef.current = true;
      try {
        const profile = await me(accessToken);
        if (profile) {
          if (profile.defaultFlow && profile.flows) {
            profile.flows = sortAndFilterFlows(profile.defaultFlow, profile.flows);
          }
          setProfile(profile);
        }
      } catch (error) {
        if (axios.isAxiosError(error)) {
          toast.error(`🤷🏻‍♂️ ${error.message}`);
        }
        console.error(error);
      } finally {
        setLoading(false);
        fetchingStatusRef.current = false;
      }
    };

    // 1. page loads
    fetchStatus();

    // 2. window is focused (in case user logs out of another window)
    window.addEventListener('focus', fetchStatus);
    return () => window.removeEventListener('focus', fetchStatus);
  }, []);

  useMemo(() => {
    localStorage.setItem('appSettings', JSON.stringify(appSettings));
  }, [appSettings]);

  return loading ? (
    <LoadingPayflowEntryLogo />
  ) : (
    <App profile={profile} appSettings={appSettings} setAppSettings={setAppSettings} />
  );
}
