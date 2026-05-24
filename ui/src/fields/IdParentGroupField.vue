<template>
  <!-- Parent group picker. The user can either pick an existing group as
       the prefix for the new group, or leave it blank to let the OU drive
       the prefix. Mirrors the legacy `registerIdParentGroupSelect2` REST
       suggestion against `service/id/group?search[value]=…`. -->
  <v-autocomplete
    :model-value="modelValue"
    :label="t('service:id:parent-group')"
    :hint="t('service:id:parent-group-description')"
    :persistent-hint="!!t('service:id:parent-group-description')"
    :items="items"
    :loading="loading"
    item-title="name"
    item-value="id"
    variant="outlined"
    density="compact"
    clearable
    no-filter
    @update:search="onSearch"
    @update:menu="onMenuOpen"
    @update:model-value="onSelect"
  />
</template>

<script setup>
import { ref } from 'vue'
import { useApi, useI18nStore } from '@ligoj/host'

defineProps({
  modelValue: { type: [String, Number, null], default: null },
  parameter: { type: Object, required: true },
  formValues: { type: Object, default: () => ({}) },
  mode: { type: String, default: null },
  isNode: { type: Boolean, default: false },
  project: { type: Object, default: null },
})
const emit = defineEmits(['update:modelValue'])

const { t } = useI18nStore()
const api = useApi()

const items = ref([])
const loading = ref(false)
// `null` sentinel distinguishes "not yet fetched" from "explicit empty
// query", so the first-page prefetch isn't blocked by a stale lastQuery.
let lastQuery = null

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
    const data = await api.get(`rest/service/id/group?search[value]=${encodeURIComponent(q)}`)
    const list = Array.isArray(data) ? data : (data?.data || [])
    items.value = list.map((g) => ({ id: g.id ?? g.name, name: g.name ?? g.id }))
  } catch (err) {
    console.warn('[id-ldap] parent-group lookup failed', err)
    items.value = []
  } finally {
    loading.value = false
  }
}

function onSelect(value) {
  emit('update:modelValue', value ?? '')
}
</script>
