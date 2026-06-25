<template>
  <!-- Two render modes, mirroring the legacy `registerIdGroupSelect2`:

       - CREATE subscription mode: the canonical group name is computed
         from the parent group / OU prefix and a simple name suffix.
         The simple-name input is editable; the computed group is shown
         read-only. We push the computed value out via `modelValue`.
       - LINK / other modes: an autocomplete against the existing LDAP
         groups (`service/id/ldap/group/<term>`).

       In CREATE mode we live-validate the computed name: it must start
       with the project's pkey, and it must not collide with an existing
       group (`service/id/group/<name>/exists`). The result surfaces as
       a Vuetify error message on the simple-name input. -->
  <div v-if="composite">
    <v-text-field
      v-model="simpleName"
      :label="t('service:id:group-simple-name')"
      :hint="t('service:id:group-simple-name-description')"
      :persistent-hint="!!t('service:id:group-simple-name-description')"
      :error-messages="liveError ? [liveError] : []"
      :loading="checking"
      :rules="simpleNameRules"
      variant="outlined"
      density="compact"
      class="mb-2"
      required
    />
    <v-text-field
      :model-value="computedGroup"
      :label="t('service:id:group')"
      :placeholder="t('service:id:ldap:group-create')"
      :messages="computedGroup ? [computedGroup] : []"
      readonly
      variant="outlined"
      density="compact"
    />
  </div>

  <LigojAutocomplete
    v-else
    :model-value="modelValue"
    :label="t('service:id:group')"
    :items="items"
    :loading="loading"
    item-title="name"
    item-value="id"
    variant="outlined"
    density="compact"
    clearable
    no-filter
    :rules="autocompleteRules"
    @update:search="onSearch"
    @update:menu="onMenuOpen"
    @update:model-value="(v) => emit('update:modelValue', v ?? '')"
  />
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useApi, useI18nStore, LigojAutocomplete } from '@ligoj/host'

const props = defineProps({
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

/** Composite rendering only kicks in for a CREATE subscription on a
 *  tool (the legacy `mode === 'create' && !isNodeMode($container)`
 *  test). Edit/create-node modes and the LINK subscription mode fall
 *  back to the autocomplete branch. */
const composite = computed(() => !props.isNode && props.mode === 'create')

/* ------------- composite mode -------------- */

const simpleName = ref('')

const prefix = computed(() => {
  const parent = props.formValues?.['service:id:parent-group']
  if (parent) return String(parent)
  const ou = props.formValues?.['service:id:ou']
  return ou ? String(ou) : ''
})

const computedGroup = computed(() => {
  const simple = String(simpleName.value || '').toLowerCase()
  if (!simple) return prefix.value
  return prefix.value ? `${prefix.value}-${simple}` : simple
})

const pkey = computed(() => props.project?.pkey || '')

const checking = ref(false)
const liveError = ref(null)
let pending = null

async function recheck() {
  liveError.value = null
  const fullName = computedGroup.value
  if (!composite.value) return
  if (!fullName) return
  // Pkey constraint (legacy `validateIdGroupCreateMode`): the computed
  // group name MUST equal the project's pkey OR start with `<pkey>-`.
  if (pkey.value && fullName !== pkey.value && !fullName.startsWith(`${pkey.value}-`)) {
    liveError.value = t('service:id:group-starts-with-pkey') !== 'service:id:group-starts-with-pkey'
      ? t('service:id:group-starts-with-pkey', { pkey: pkey.value })
      : `Group name must start with "${pkey.value}-".`
    return
  }
  // Server-side existence probe — race-protected by the `pending`
  // token: only the latest call's result is acted upon.
  const token = Symbol('exists')
  pending = token
  checking.value = true
  try {
    const exists = await api.get(`rest/service/id/group/${encodeURIComponent(fullName)}/exists`)
    if (pending !== token) return
    if (exists === true || exists === 'true') {
      liveError.value = t('service:id:group-already-exists') !== 'service:id:group-already-exists'
        ? t('service:id:group-already-exists', { name: fullName })
        : `A group named "${fullName}" already exists.`
    }
  } catch (err) {
    if (pending !== token) return
    console.warn('[id-ldap] group exists probe failed', err)
  } finally {
    if (pending === token) checking.value = false
  }
}

// Keep the bound parameter value in sync with the computed full name
// the host wizard persists as `service:id:group`.
watch(computedGroup, (v) => emit('update:modelValue', v), { immediate: true })

// Re-validate whenever any of the inputs the full name depends on
// changes — prefix (parent-group / ou) included.
watch([computedGroup, () => pkey.value], () => { recheck() }, { immediate: true })

const REQUIRED_RULE = (v) => (v != null && String(v).trim() !== '') || 'Required'
const SIMPLE_NAME_RULES = [REQUIRED_RULE]
const simpleNameRules = computed(() => composite.value ? SIMPLE_NAME_RULES : [])

/* ------------- link / fallback mode -------- */

const items = ref([])
const loading = ref(false)
// Sentinel keeps `onSearch('')` distinct from the initial state so the
// first page is fetched even when the user opens the dropdown without
// typing.
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
  if (open && !composite.value && !items.value.length) onSearch('')
}

async function onSearch(term) {
  const q = (term || '').trim()
  if (q === lastQuery) return
  lastQuery = q
  loading.value = true
  try {
    // The legacy `service/id/ldap/group/<term>` (path-style) endpoint is
    // gone; the backend now exposes the shared `service/id/group` search
    // with a DataTables-style query parameter (same shape parent-group
    // uses). In LINK mode the picker just needs to surface groups the
    // user can attach to — the shared list works.
    const data = await api.get(`rest/service/id/group?search[value]=${encodeURIComponent(q)}`)
    const list = Array.isArray(data) ? data : (data?.data || [])
    items.value = list.map((g) => ({ id: g.id ?? g.name, name: g.name ?? g.id }))
  } catch (err) {
    console.warn('[id-ldap] group lookup failed', err)
    items.value = []
  } finally {
    loading.value = false
  }
}

const autocompleteRules = computed(() => (props.parameter?.mandatory || props.parameter?.required)
  ? [REQUIRED_RULE]
  : [])
</script>
