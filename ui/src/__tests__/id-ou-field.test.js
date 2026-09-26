/*
 * Organization picker: every customer is suggested as soon as the dropdown opens,
 * typing narrows the list through the criteria route.
 */
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { mount, flushPromises } from '@vue/test-utils'
import { useI18nStore } from '@ligoj/host'
import IdOuField from '../fields/IdOuField.vue'

const ComboStub = { props: ['modelValue', 'items', 'loading', 'hideNoData', 'noDataText', 'label'], emits: ['update:search', 'update:menu', 'update:modelValue'], template: '<div class="cb" :data-items="items.map((i) => i.name).join(\',\')" />' }
function json(body) { return { ok: true, status: 200, headers: { get: () => 'application/json' }, json: () => Promise.resolve(body), text: () => Promise.resolve(JSON.stringify(body)) } }
function urls() { return globalThis.fetch.mock.calls.map((c) => String(c[0])) }

describe('IdOuField — suggestions', () => {
  beforeEach(() => {
    setActivePinia(createPinia()); useI18nStore().merge({ 'service:id:ou': 'Organization' }, 'en')
    globalThis.fetch = vi.fn((url) => Promise.resolve(json(String(url).endsWith('/se') ? [{ id: 'sea', name: 'sea' }] : [{ id: 'sea', name: 'sea' }, { id: 'sky', name: 'sky' }])))
  })

  it('lists every customer when the dropdown opens without any input, then narrows on typing', async () => {
    const w = mount(IdOuField, { props: { parameter: { id: 'service:id:ou' }, modelValue: '', instanceNodeId: 'service:id:ldap:local' }, global: { stubs: { LigojCombobox: ComboStub } } })
    await flushPromises()
    expect(urls()).toEqual([])
    // The menu must open before the first fetch answers: no "hide when empty"
    expect(w.findComponent(ComboStub).props('hideNoData')).toBe(false)
    expect(w.findComponent(ComboStub).props('noDataText')).toBeTruthy()
    w.findComponent(ComboStub).vm.$emit('update:menu', true)
    await flushPromises()
    expect(urls()).toEqual(['rest/service/id/ldap/customer/service%3Aid%3Aldap%3Alocal'])
    expect(w.find('.cb').attributes('data-items')).toBe('sea,sky')

    w.findComponent(ComboStub).vm.$emit('update:search', 'se')
    await flushPromises()
    expect(urls().at(-1)).toBe('rest/service/id/ldap/customer/service%3Aid%3Aldap%3Alocal/se')
    expect(w.find('.cb').attributes('data-items')).toBe('sea')
  })
})

describe('IdOuField — required marker and project key default', () => {
  beforeEach(() => { setActivePinia(createPinia()); useI18nStore().merge({ 'service:id:ou': 'Organization' }, 'en'); globalThis.fetch = vi.fn() })

  it('marks the organization as required and prefills it with the project key when creating', async () => {
    const w = mount(IdOuField, { props: { parameter: { id: 'service:id:ou', mandatory: true }, modelValue: '', mode: 'create', project: { pkey: 'demo-2' } }, global: { stubs: { LigojCombobox: ComboStub } } })
    await flushPromises()
    expect(w.findComponent(ComboStub).props('label')).toBe('Organization *')
    expect(w.emitted('update:modelValue')).toEqual([['demo-2']])
  })

  it('never overwrites a value already set, and does not prefill when linking', async () => {
    const set = mount(IdOuField, { props: { parameter: { id: 'service:id:ou', mandatory: true }, modelValue: 'acme', mode: 'create', project: { pkey: 'demo-2' } }, global: { stubs: { LigojCombobox: ComboStub } } })
    await flushPromises()
    expect(set.emitted('update:modelValue')).toBeUndefined()
    const link = mount(IdOuField, { props: { parameter: { id: 'service:id:ou', mandatory: false }, modelValue: '', mode: 'link', project: { pkey: 'demo-2' } }, global: { stubs: { LigojCombobox: ComboStub } } })
    await flushPromises()
    expect(link.emitted('update:modelValue')).toBeUndefined()
    expect(link.findComponent(ComboStub).props('label')).toBe('Organization')
  })
})
