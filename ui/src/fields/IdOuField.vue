<template>
  <!-- Organisation / customer picker. Suggests existing OUs from
       `service/id/ldap/customer?search[value]=<term>`. New values are
       accepted — Vuetify's combobox lets the user keep a typed-but-
       unmatched entry, which the backend creates on subscription.
       Mirrors the legacy `registerIdOuSelect2(..., allowNew=true)`. -->
  <v-combobox
    :model-value="modelValue"
    :label="t('service:id:ou')"
    :hint="hint"
    persistent-hint
    :messages="warning ? [warning] : []"
    :items="items"
    :loading="loading"
    item-title="name"
    item-value="id"
    return-object
    variant="outlined"
    density="compact"
    :rules="rules"
    no-filter
    @update:search="onSearch"
    @update:menu="onMenuOpen"
    @update:model-value="onSelect"
  />
</template>

<script setup>
import { computed, ref } from 'vue'
import { useApi, useI18nStore } from '@ligoj/host'

const props = defineProps({
  modelValue: { type: [String, Object, null], default: null },
  parameter: { type: Object, required: true },
  formValues: { type: Object, default: () => ({}) },
  mode: { type: String, default: null },
  isNode: { type: Boolean, default: false },
  project: { type: Object, default: null },
  // Subscribe wizard passes the tool node id (e.g. `service:id:ldap`).
  // The backend customer endpoint takes `{node}/{criteria}` as path
  // segments — and even though it ignores the node value today, the
  // route still has to match.
  nodeId: { type: String, default: null },
})
const emit = defineEmits(['update:modelValue'])

const { t } = useI18nStore()
const api = useApi()

const items = ref([])
const loading = ref(false)
const warning = ref(null)
// `null` sentinel — distinguishes "never fetched" from "empty query".
let lastQuery = null

const hint = computed(() => t('service:id:ou-description'))
const required = computed(() => !!(props.parameter?.mandatory || props.parameter?.required))
const rules = computed(() => required.value
  ? [(v) => (v != null && v !== '') || 'Required']
  : [])

/**
 * Defer every REST call until the user actually interacts: opens the
 * dropdown or types a query. The autocomplete must NOT fetch on mount
 * — many forms render fields the user never touches, and a per-field
 * spurious request adds up. `@update:menu` covers the "no text typed,
 * just opened" case by triggering an empty-query fetch (the server
 * answers with the first page).
 */
function onMenuOpen(open) {
  if (open && !items.value.length) onSearch('')
}

async function onSearch(term) {
  const q = (term || '').trim()
  if (q === lastQuery) return
  lastQuery = q
  loading.value = true
  try {
    // `customer/{node}/{criteria}` — `LdapPluginResource.findCustomersByName`.
    // Node segment is ignored server-side but the route requires it. Use
    // the wizard-supplied node id; default to `service:id:ldap` so the
    // request still matches when the field is mounted outside the
    // wizard (tests, dev preview).
    const node = props.nodeId || 'service:id:ldap'
    const url = `rest/service/id/ldap/customer/${encodeURIComponent(node)}/${encodeURIComponent(q)}`
    const data = await api.get(url)
    const list = Array.isArray(data) ? data : (data?.data || [])
    items.value = list.map((c) => ({ id: c.id ?? c.name, name: c.name ?? c.id }))
  } catch (err) {
    console.warn('[id-ldap] ou lookup failed', err)
    items.value = []
  } finally {
    loading.value = false
  }
}

function onSelect(value) {
  // v-combobox emits either an object (picked from the menu) or a raw
  // string (typed-and-not-matched). Normalise to the id/text the backend
  // expects, and flag a "will be created" warning for the new case —
  // matches the legacy `validateIdOuCreateMode` `service:id:ou-not-exists`
  // behaviour.
  if (value && typeof value === 'object') {
    warning.value = null
    emit('update:modelValue', value.id || value.name || '')
  } else {
    const text = String(value || '').trim()
    warning.value = text ? t('service:id:ou-not-exists') : null
    emit('update:modelValue', text)
  }
}
</script>
