import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

// Library build for the "id-ldap" tool-level plugin.
//
// Loaded by the Ligoj Vue host via a dynamic import of
//   /main/id-ldap/vue/index.js
// — so the output lives under the Java module's webjars classpath, where
// Spring Boot's webjars servlet serves it at runtime.
//
// Shared deps (vue, pinia, vue-router, vuetify, @ligoj/host) are kept
// EXTERNAL: the plugin must use the host's module instances or reactivity
// and plugin registries break across SFC boundaries.

export default defineConfig({
  plugins: [vue()],

  build: {
    lib: {
      entry: resolve(__dirname, 'src/index.js'),
      formats: ['es'],
      fileName: () => 'index.js',
    },
    outDir: resolve(
      __dirname,
      '../src/main/resources/META-INF/resources/webjars/id-ldap/vue',
    ),
    emptyOutDir: true,
    rollupOptions: {
      external: ['vue', 'vue-router', 'pinia', 'vuetify', '@ligoj/host'],
      output: {
        assetFileNames: 'index.[ext]',
      },
    },
  },

  // Standalone dev server — tests the plugin in isolation against a running
  // Ligoj backend on :8080. `npm run dev` then open http://localhost:5175/.
  server: {
    port: 5175,
    proxy: {
      '/rest': { target: 'http://localhost:8080', changeOrigin: true },
      '/webjars': { target: 'http://localhost:8080', changeOrigin: true },
    },
  },
})
