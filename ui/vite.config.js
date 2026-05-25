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

// Path to the Ligoj host repo, sitting beside `ligoj-plugins/` in the
// developer workspace. Used to resolve `@ligoj/host` for tests and the
// standalone dev server (the runtime import map handles the production
// case via the webjars-served bundle).
const HOST_SRC = resolve(__dirname, '../../../ligoj/app-ui/src/main/webapp/src')

export default defineConfig({
  plugins: [vue()],

  resolve: {
    alias: {
      // Resolve the shared host surface locally for tests and dev. At
      // runtime the browser pulls `@ligoj/host` via the import map in
      // the host's index.html; the build keeps it external (see below)
      // so the plugin bundle doesn't ship a second copy of the host.
      '@ligoj/host': resolve(HOST_SRC, 'host.js'),
      // host.js transitively imports `@/stores/*`, `@/composables/*`,
      // etc. Map `@` to the host's src root so those resolve too. The
      // plugin's own code uses relative paths, never `@/`, so this
      // alias only affects host-side imports pulled in through
      // `@ligoj/host`.
      '@': HOST_SRC,
    },
    // Shared singletons. The plugin and the host each ship a copy of
    // vue / pinia / vue-router / vuetify under their own node_modules.
    // At runtime the browser de-dupes via the import map; in tests we
    // need vitest to pick exactly one copy or `setActivePinia` from
    // the test won't reach `useI18nStore` from host.js. Point all four
    // at the host's node_modules — same trick the runtime import map
    // pulls.
    dedupe: ['vue', 'pinia', 'vue-router', 'vuetify'],
  },

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

  // Vitest configuration. Same shape as the host's setup so tests behave
  // identically when invoked from either repo. The plugin owns its tests
  // now (was previously under app-ui/src/__tests__/plugins/).
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['src/__tests__/setup.js'],
    exclude: ['node_modules/**', 'dist/**'],
    css: false,
    server: {
      deps: {
        inline: ['vuetify'],
      },
    },
  },
})
