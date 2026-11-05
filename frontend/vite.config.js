import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
import vuetify from 'vite-plugin-vuetify';

const localAppData = process.env.LOCALAPPDATA;
if (!localAppData) {
  throw new Error('LOCALAPPDATA is required to configure the local HTTPS certificate.');
}

const certificateDirectory = resolve(localAppData, 'Track2-HTTPS');

export default defineConfig({
  plugins: [vue(), vuetify({ autoImport: true })],
  server: {
    host: '127.0.0.1',
    https: {
      cert: readFileSync(resolve(certificateDirectory, 'track2.pem')),
      key: readFileSync(resolve(certificateDirectory, 'track2-key.pem')),
    },
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
      },
    },
  },
});
