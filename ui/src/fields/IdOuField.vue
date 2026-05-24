<template>
  <!-- Organisation / customer picker. Looks up existing OUs from the
       node-scoped backend route:
         GET rest/service/id/ldap/customer/<instanceNodeId>/<criteria>
       (LdapPluginResource.findCustomersByName). The route requires a
       non-empty `{criteria}` path segment, so unlike the group fields
       this picker does NOT fire on dropdown open — the user must type
       at least one character before a request is issued. -->
  <v-autocomplete
    :model-value="modelValue"
    :label="t('service:id:ou')"
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
    :rules="rules"
    @update:search="onSearch"
    @update:model-value="(v) => emit('update:modelValue', v ?? '')"
  />
</template>

<script setup>
import { computed, ref } from 'vue'
import { useApi, useI18nStore } from '@ligoj/host'

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
const rules = computed(() => required.value
  ? [(v) => (v != null && v !== '') || 'Required']
  : [])

/**
 * Trigger a lookup only once the user types at least one character —
 * the backend's `customer/{node}/{criteria}` route requires a
 * non-empty criteria segment, so an empty fetch would 404. No mount-
 * time fetch either: zero API calls until real input.
 */
async function onSearch(term) {
  const q = (term || '').trim()
  if (q === lastQuery) return
  lastQuery = q
  if (q.length < 1) {
    items.value = []
    return
  }
  loading.value = true
  try {
    // `customer/{node}/{criteria}` — backend ignores the node value but
    // the JAX-RS route requires the segment. Use the wizard-supplied
    // instance id; fall back to the tool id, then to a sane default so
    // the request still matches when the field mounts standalone.
    const node = props.instanceNodeId || props.nodeId || 'service:id:ldap'
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
</script>
