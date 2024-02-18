import { dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import express from 'express';
import { renderPage } from 'vike/server';
import { test } from './components/test';
import { htmlToImage } from './utils/image';
import axios from 'axios';
import { profileHtml } from './components/Profile';
import { ProfileType } from './types/ProfleType';

import dotenv from 'dotenv';
import { welcomeProfileHtml } from './components/WelcomeProfile';
import { invitedHtml } from './components/Invited';
import { notInvitedHtml } from './components/NotInvited';
import { giftHtml } from './components/Gift';
dotenv.config();

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const isProduction = process.env.NODE_ENV === 'production';
const root = __dirname;

const API_URL = process.env.VITE_PAYFLOW_SERVICE_API_URL;

startServer();

async function startServer() {
  const app = express();
  app.use(express.static('assets'));

  if (isProduction) {
    app.use(express.static(`${root}/dist/client`));
  } else {
    // Instantiate Vite's development server and integrate its middleware to our server.
    // ⚠️ We should instantiate it *only* in development. (It isn't needed in production
    // and would unnecessarily bloat our server in production.)
    const vite = await import('vite');
    const viteDevMiddleware = (
      await vite.createServer({
        root,
        server: { middlewareMode: true }
      })
    ).middlewares;
    app.use(viteDevMiddleware);
  }

  // handling frames image generation

  app.get('/images/welcome.png', async (_, res) => {
    const image = await htmlToImage(test('Payflow'), 'landscape');
    res.type('png').send(image);
  });

  app.get('/images/profile/:fname/welcome.png', async (req, res) => {
    const fname = req.params.fname;

    const image = await htmlToImage(welcomeProfileHtml(fname), 'landscape');
    res.type('png').send(image);
  });

  app.get('/images/profile/invited.png', async (_, res) => {
    const image = await htmlToImage(invitedHtml(), 'landscape');
    res.type('png').send(image);
  });

  app.get('/images/profile/notinvited.png', async (_, res) => {
    const image = await htmlToImage(notInvitedHtml(), 'landscape');
    res.type('png').send(image);
  });

  app.get('/images/profile/:identity/image.png', async (req, res) => {
    const identity = req.params.identity;

    try {
      const response = await axios.get(`${API_URL}/api/user/${identity}`);
      const profileData = response.data as ProfileType;
      const image = await htmlToImage(profileHtml(profileData), 'landscape');
      res.type('png').send(image);
    } catch (error) {
      console.error(error);
      res.status(500).send('Error retrieving profile data');
    }
  });

  app.get('/images/profile/:identity/gift/image.png', async (req, res) => {
    const gifter = req.params.identity;
    const error = req.query.error?.toString();

    try {
      const gifterProfile = (await axios.get(`${API_URL}/api/user/${gifter}`)).data as ProfileType;

      const image = await htmlToImage(giftHtml(gifterProfile, undefined, error), 'landscape');
      res.type('png').send(image);
    } catch (error) {
      console.error(error);
      res.status(500).send('Error retrieving profile data');
    }
  });

  app.get('/images/profile/:identity/gift/:contact/image.png', async (req, res) => {
    const gifter = req.params.identity;
    const gifted = req.params.contact;

    try {
      const gifterProfile = (await axios.get(`${API_URL}/api/user/${gifter}`)).data as ProfileType;
      const giftedProfile = (await axios.get(`${API_URL}/api/user/${gifted}`)).data as ProfileType;

      const image = await htmlToImage(giftHtml(gifterProfile, giftedProfile), 'landscape');
      res.type('png').send(image);
    } catch (error) {
      console.error(error);
      res.status(500).send('Error retrieving profile data');
    }
  });

  /**
   * Vike route
   *
   * @link {@see https://vike.dev}
   **/
  app.all('*', async (req, res, next) => {
    const pageContextInit = { urlOriginal: req.originalUrl };
    const pageContext = await renderPage(pageContextInit);
    if (pageContext.httpResponse === null) return next();

    const { statusCode, contentType } = pageContext.httpResponse;
    res.status(statusCode).type(contentType);
    pageContext.httpResponse.pipe(res);
  });

  app.listen(process.env.PORT ? parseInt(process.env.PORT) : 3000, () => {
    console.log('Server listening on http://localhost:3000');
  });
}
