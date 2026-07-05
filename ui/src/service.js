import { APP_BASE, renderServiceLink, useI18nStore } from '@ligoj/host'
import IdParentGroupField from './fields/IdParentGroupField.vue'
import IdOuField from './fields/IdOuField.vue'
import IdGroupField from './fields/IdGroupField.vue'

// Parameter ids the LDAP plugin owns a custom input for. Subscribe-mode
// only: in `edit-node` / `create-node` the wizard edits tool config
// (where the OU/group fields don't apply) and we let the default
// renderer handle them. The `service:id:group` field stays composite-or-
// autocomplete depending on the subscription mode — see IdGroupField.vue.
const PARAMETER_FIELDS = {
  'service:id:parent-group': IdParentGroupField,
  'service:id:ou': IdOuField,
  'service:id:group': IdGroupField,
}

/** Pull the group identifier out of a subscription. Mirrors the legacy
 *  `subscription.parameters['service:id:group']` lookup. */
function groupOf(subscription) {
  return subscription?.parameters?.['service:id:group'] ?? null
}

/** Today's date in `YYYY-MM-DD` form, used to stamp the exported CSV name
 *  so successive downloads don't clobber each other in the browser. */
function today() {
  const d = new Date()
  const yyyy = d.getFullYear()
  const mm = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  return `${yyyy}-${mm}-${dd}`
}

const service = {
  /**
   * Tool-level row actions for an LDAP subscription. Appended to the
   * parent `plugin-id`'s buttons via its delegation hook — mirrors the
   * legacy `current.$super(...)` inheritance where `ldap.js` augmented
   * `id.js#renderFeatures`.
   *
   * Two CSV exports are exposed:
   *   - Group activity   → `service/id/ldap/activity/<sub>/group-<group>-<date>.csv`
   *   - Project activity → `service/id/ldap/activity/<sub>/project-<group>-<date>.csv`
   *
   * The original UI bundled both behind a single `<i class="fa-download">`
   * dropdown. We split into two individual buttons here: simpler to mount
   * as VNodes, and avoids pulling in a Vuetify menu just for two links.
   */
  renderFeatures(subscription) {
    const { t } = useI18nStore()
    const group = groupOf(subscription)
    if (!subscription?.id || !group) return []

    const date = today()
    const base = `${APP_BASE}rest/service/id/ldap/activity/${subscription.id}`
    return [
      renderServiceLink({
        icon: 'mdi-file-table-outline',
        href: `${base}/group-${encodeURIComponent(group)}-${date}.csv`,
        download: '',
        title: t('id.renderFeatures.activityGroup'),
        rel: 'noopener',
      }),
      renderServiceLink({
        icon: 'mdi-file-chart-outline',
        href: `${base}/project-${encodeURIComponent(group)}-${date}.csv`,
        download: '',
        title: t('id.renderFeatures.activityProject'),
        rel: 'noopener',
      }),
    ]
  },

  /**
   * Wizard hook: replace the default parameter input for the LDAP
   * "shape" inputs (OU, parent-group, group) with rich autocompletes /
   * a composite simple-name + computed full-name editor. Returns null
   * for every other parameter so the wizard falls back to its default
   * type-based rendering.
   *
   * Active only in subscription mode (not when editing a node directly):
   * those fields drive subscription creation against the LDAP backend,
   * but in node-config screens they have no useful meaning.
   */
  parameterField({ parameter, isNode } = {}) {
    if (isNode) return null
    const comp = PARAMETER_FIELDS[parameter?.id]
    return comp || null
  },

  /**
   * Parameter layout for the node/subscription forms: cluster the LDAP
   * parameters into labelled groups (labels are i18n keys). The connection
   * group lists its parameters explicitly to fix their order; the others use
   * `*` globs so every matching parameter (name-ordered) is captured without
   * enumerating each id. Groups apply in both node and subscription context —
   * parameters absent from the current context (e.g. `clear-password`, a
   * node-only parameter) are simply skipped. Anything unmatched keeps the
   * default name-ascending order in a trailing unlabeled group.
   */
  parameterLayout() {
    return [
      {
        label: 'id.wizard.group.connection',
        parameters: [
          'service:id:ldap:url',
          'service:id:ldap:user-dn',
          'service:id:ldap:password',
          'service:id:ldap:clear-password',
          'service:id:ldap:login-attributes',
          'service:id:ldap:local-id-attribute',
        ],
      },
      { label: 'id.wizard.group.groups', parameters: ['service:id:ldap:groups-*'] },
      { label: 'id.wizard.group.people', parameters: ['service:id:ldap:people-*', 'service:id:ldap:quarantine-dn', 'service:id:ldap:department-attribute'] },
      { label: 'id.wizard.group.companies', parameters: ['service:id:ldap:compan*'] },
    ]
  },
}

export default service
