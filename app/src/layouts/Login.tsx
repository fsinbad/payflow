import { lazy, Suspense } from 'react';
import { Box, Container } from '@mui/material';
import { Helmet } from 'react-helmet-async';
import CustomThemeProvider from '../theme/CustomThemeProvider';
import { useNavigate, useSearchParams } from 'react-router';
import { useEffect } from 'react';
import ProfileOnboardingDialog from '../components/dialogs/ProfileOnboardingDialog';
import { ProfileType } from '@payflow/common';
import LoadingPayflowEntryLogo from '../components/LoadingPayflowEntryLogo';
import { useDarkMode } from '../utils/hooks/useDarkMode';
import { useMiniApp, useMobile, usePwa } from '../utils/hooks/useMobile';
import PullToRefresh from 'react-simple-pull-to-refresh';
import { useRegisterSW } from 'virtual:pwa-register/react';
import { UpdateVersionPrompt } from '../components/UpdateVersionPrompt';

const ConnectCard = lazy(() => import('../components/cards/ConnectCard'));

export default function Login({
  authStatus,
  profile
}: {
  authStatus: string;
  profile: ProfileType | undefined;
}) {
  const prefersDarkMode = useDarkMode();
  const [searchParams] = useSearchParams();
  const username = searchParams.get('username');
  const invitationCode = searchParams.get('code');
  const redirect = searchParams.get('redirect');
  const isMobile = useMobile();
  const navigate = useNavigate();
  const enablePullToRefresh = usePwa() || useMiniApp();

  const {
    needRefresh: [needRefresh]
  } = useRegisterSW();

  useEffect(() => {
    console.debug(profile, authStatus);

    if (profile && authStatus === 'authenticated') {
      if (profile.username) {
        console.debug('redirecting to: ', redirect ?? '/');
        navigate(redirect ?? '/');
      }
    }
  }, [authStatus, profile]);

  const handleRefresh = async () => {
    // Implement your refresh logic here
    // For example, you might want to reload the page or re-fetch some data
    window.location.reload();
  };

  return (
    <CustomThemeProvider darkMode={prefersDarkMode}>
      <Helmet>
        <title> Payflow | Connect </title>
      </Helmet>
      <PullToRefresh onRefresh={handleRefresh} isPullable={enablePullToRefresh}>
        <Container maxWidth="sm" sx={{ height: '80vh' }}>
          {needRefresh && <UpdateVersionPrompt />}
          {authStatus === 'loading' ? (
            <LoadingPayflowEntryLogo />
          ) : (
            !profile && (
              <Box
                position="fixed"
                display="flex"
                alignItems="center"
                boxSizing="border-box"
                justifyContent="center"
                sx={{ inset: 0 }}>
                <Suspense fallback={<LoadingPayflowEntryLogo />}>
                  <ConnectCard />
                </Suspense>
              </Box>
            )
          )}
          {profile && !profile.username && (
            <ProfileOnboardingDialog
              fullScreen={isMobile}
              open={!profile.username}
              profile={profile}
              closeStateCallback={() => {}}
              username={username}
              code={invitationCode}
            />
          )}
        </Container>
      </PullToRefresh>
    </CustomThemeProvider>
  );
}
