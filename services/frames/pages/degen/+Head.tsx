import { API_URL, DAPP_URL } from '../../utils/constants';

export function Head() {
  const miniAppUrl = `${API_URL}/api/farcaster/composer/pay?action=useful`;
  const miniAppDeeplink = `https://warpcast.com/~/composer-action?url=${encodeURIComponent(miniAppUrl)}&view=prompt`;

  const imageUrl = 'https://i.imgur.com/FMusAac.png';
  return (
    <>
      <head>
        <meta charSet="UTF-8" />
        <meta content="width=device-width, initial-scale=1" name="viewport" />
        <meta name="theme-color" content="#ffffff" />

        <link rel="apple-touch-icon" sizes="180x180" href="/apple-touch-icon.png" />
        <link rel="shortcut icon" type="image/x-icon" href="/favicon.ico" />
        <link rel="icon" type="image/png" sizes="32x32" href="/favicon-32x32.png" />
        <link rel="icon" type="image/png" sizes="16x16" href="/favicon-16x16.png" />

        <title>Payflow | Claim Degen</title>
        <meta name="description" content="Claim your Degen Points on Payflow" />

        <meta property="og:url" content={DAPP_URL} />
        <meta property="og:type" content="website" />
        <meta property="og:title" content="Payflow | Claim Degen" />
        <meta property="og:description" content="Claim your Degen Points on Payflow" />
        <meta property="og:image" content={imageUrl} />

        <meta name="twitter:card" content="summary_large_image" />
        <meta property="twitter:domain" content="app.payflow.me" />
        <meta property="twitter:url" content={DAPP_URL} />
        <meta name="twitter:title" content="Payflow | Claim Degen" />
        <meta name="twitter:description" content="Claim your Degen Points on Payflow" />
        <meta name="twitter:image" content={imageUrl} />

        <meta property="of:accepts:xmtp" content="2024-02-01" />
        <meta property="of:accepts:lens" content="1.1" />
        <meta property="fc:frame" content="vNext" />

        <meta property="fc:frame:image" content={imageUrl} />
        <meta property="fc:frame:image:aspect_ratio" content="1:1" />

        <meta property="fc:frame:button:1" content="Claim Points" />
        <meta property="fc:frame:button:1:action" content="link" />
        <meta property="fc:frame:button:1:target" content={miniAppDeeplink} />
      </head>
    </>
  );
}
