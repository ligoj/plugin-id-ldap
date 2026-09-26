<template>
  <!-- Organisation / customer picker. Looks up existing OUs from the
       node-scoped backend route:
         GET rest/service/id/ldap/customer/<instanceNodeId>/<criteria>
       (LdapPluginResource.findCustomersByName). Opening the dropdown
       lists every customer (`customer/<instanceNodeId>` without
       criteria); typing narrows the list. `hide-no-data` is forced
       off: a v-combobox keeps its menu DISABLED while it has no item,
       so the first opening (list not fetched yet) would show nothing
       even once the customers arrive.

       `v-combobox` (not `v-autocomplete`) so free-form text typed by
       the user becomes the field's value when no LDAP OU matches.
       The OU parameter is a plain string upstream, so an org name
       the LDAP search doesn't yet know about (a new customer that
       admin will declare) is still a legitimate value. -->
  <LigojCombobox
    :model-value="modelValue"
    :label="paramLabel"
    :hint="hint"
    :persistent-hint="!!hint"
    :items="items"
    :loading="loading"
    item-title="name"
    item-value="id"
    variant="outlined"
    density="compact"
    clearable
    no-filter
    :hide-no-data="false"
    :no-data-text="loading ? t('common.loading') : t('common.noData')"
    :rules="rules"
    @update:search="onSearch"
    @update:menu="onMenuOpen"
    @update:model-value="onModelUpdate"
  />
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { LigojCombobox, useApi, useI18nStore } from '@ligoj/host'

const props = defineProps({
  modelValue: { type: [String, Number, null], default: null },
  parameter: { type: Object, required: true },
  formValues: { type: Object, default: () => ({}) },
  mode: { type: String, default: null },
  isNode: { type: Boolean, default: false },
  project: { type: Object, default: null },
  // Tool-level node id (e.g. `service:id:ldap`). Unused here — the OU
  // endpoint is instance-scoped — but accepted so the wizard can pass
  // the full context uniformly.
  nodeId: { type: String, default: null },
  // Instance-level node id (e.g. `service:id:ldap:local`). Forms the
  // `{node}` segment of the customer-lookup URL.
  instanceNodeId: { type: String, default: null },
})
const emit = defineEmits(['update:modelValue'])

const { t } = useI18nStore()
const api = useApi()

const items = ref([])
const loading = ref(false)
// `null` sentinel — distinguishes "never fetched" from "explicit empty
// query" so the first-page fetch on dropdown open isn't suppressed.
let lastQuery = null

const hint = computed(() => t('service:id:ou-description'))
const required = computed(() => !!(props.parameter?.mandatory || props.parameter?.required))
const paramLabel = computed(() => `${t('service:id:ou')}${required.value ? ' *' : ''}`)
const createMode = computed(() => !props.isNode && String(props.mode).toLowerCase() === 'create')
/**
 * Prefill: the organization must be a prefix of the project key (the group is `<organization>-<name>` and must
 * start with the key), so the key itself is the natural default for a new subscription. Only when nothing is
 * set yet: a value chosen by the user, or restored by the form, is never overwritten.
 */
watch(() => props.project?.pkey, (pkey) => {
  if (createMode.value && pkey && (props.modelValue == null || props.modelValue === '')) emit('update:modelValue', pkey)
}, { immediate: true })
const rules = computed(() => required.value
  ? [(v) => (v != null && v !== '') || 'Required']
  : [])

/**
 * Opening the dropdown before any input lists every customer (`customer/{node}`), so the
 * user can pick without typing; typing then narrows the list (`customer/{node}/{criteria}`).
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
    // Backend ignores the node value but the JAX-RS route requires the
    // segment. Use the wizard-supplied instance id; fall back to the tool
    // id, then to a sane default so the request still matches when the
    // field mounts standalone.
    const node = props.instanceNodeId || props.nodeId || 'service:id:ldap'
    const url = `rest/service/id/ldap/customer/${encodeURIComponent(node)}${q ? '/' + encodeURIComponent(q) : ''}`
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

/**
 * Combobox emits one of: the picked item's `item-value` (the OU id
 * string), the raw typed value (when the user enters free text and
 * blurs / hits Enter), or `null` after the clear button. Normalise
 * everything to a string so the wizard's paramValues map stores a
 * consistent type. The "object" branch is defensive — Vuetify can
 * surface the whole item under some focus-timing edge cases.
 */
function onModelUpdate(v) {
  if (v == null) {
    emit('update:modelValue', '')
    return
  }
  if (typeof v === 'object') {
    emit('update:modelValue', v.id ?? v.name ?? '')
    return
  }
  emit('update:modelValue', String(v))
}

</script>
