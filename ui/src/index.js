/*
 * Plugin "id-ldap" — LDAP implementation of plugin-id.
 *
 * Tool-level plugin: lives at `service:id:ldap` in the node tree. It does
 * not own routes or a top-level component — it augments the parent
 * `plugin-id` via:
 *
 *   - i18n: LDAP-specific parameter labels (so the subscribe wizard's
 *     auto-rendered parameter form shows friendly names).
 *   - feature('renderFeatures', subscription): activity-export CSV
 *     buttons appended to the parent's row actions through plugin-id's
 *     `subPluginIdFor(...)` delegation hook.
 *
 * Authored as source — compiled to `/main/id-ldap/vue/index.js` by Vite.
 * Shared host surface (stores, components) is imported from `@ligoj/host`
 * and kept external at build so plugin and host share the same instances.
 */
import { useI18nStore } from '@ligoj/host'
import enMessages from './i18n/en.js'
import frMessages from './i18n/fr.js'
import service from './service.js'

const features = {
  renderFeatures: service.renderFeatures,
  parameterField: service.parameterField,
}

export default {
  id: 'id-ldap',
  label: 'Identity LDAP',
  // Declared dependency: the parent service-level plugin contributes the
  // inherited parameter labels (`service:id:ou`, `service:id:group`, …),
  // the delegation hook that pulls our `renderFeatures` VNodes into the
  // subscription row, and the `/id/*` routes referenced from our nav.
  // The loader awaits these before calling our install(), so by the
  // time we merge our LDAP-specific i18n the parent's bundle is already
  // in the store — labels resolve correctly on the first render.
  requires: ['id'],
  // No routes — LDAP-specific screens (the legacy `ldap.html` was empty)
  // and parameter forms come from the parent's wizard.
  install() {
    const i18n = useI18nStore()
    i18n.merge(enMessages, 'en')
    i18n.merge(frMessages, 'fr')
  },
  feature(action, ...args) {
    const fn = features[action]
    if (!fn) throw new Error(`Plugin "id-ldap" has no feature "${action}"`)
    return fn(...args)
  },
  service,
  meta: { icon: 'mdi-folder-network-outline', color: 'blue-grey-darken-2' },
}

export { service }
