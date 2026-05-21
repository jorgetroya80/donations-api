import { defineConfig } from '@hey-api/openapi-ts';

export default defineConfig({
  input: '../../build/openapi/openapi.yaml',
  output: 'src',
  plugins: [
    '@hey-api/typescript',
    '@hey-api/sdk',
    '@hey-api/client-fetch',
  ],
});
