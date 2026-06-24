import { describe, it, expect, beforeEach, afterEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
// `@ligoj/host` is aliased in vite.config.js → app-ui/src/host.js so the
// plugin's tests use the real registry / stores / helpers the host
// publishes at runtime. The same alias is what the dev server resolves.
import { pluginRegistry } from '@ligoj/host'
// Plugin source (pre-build). The built bundle under
// ../src/main/resources/.../webjars/id-ldap/vue/index.js is what the
// host loads at runtime — here we test the authoring surface directly.
import pluginIdLdapDef from '../index.js'
// Cross-plugin reference: the second describe verifies plugin-id's
// delegation hook fires when an LDAP sub-plugin is registered. The
// two repos sit side by side in the workspace.
import pluginIdDef from '../../../../plugin-id/ui/src/index.js'

describe('plugin-id-ldap contract', () => {
  it('exports required fields (id, label, install, feature, service, meta)', () => {
    expect(pluginIdLdapDef.id).toBe('id-ldap')
    expect(typeof pluginIdLdapDef.label).toBe('string')
    expect(typeof pluginIdLdapDef.install).toBe('function')
    expect(typeof pluginIdLdapDef.feature).toBe('function')
    expect(pluginIdLdapDef.service).toBeTypeOf('object')
    expect(pluginIdLdapDef.meta).toMatchObject({ icon: expect.any(String), color: expect.any(String) })
  })

  it('declares `requires: ["id"]` — parent plugin must load first', () => {
    // The loader awaits everything in `requires` before calling install(),
    // so the parent's i18n is merged and registry slot exists before any
    // delegation or label lookup runs.
    expect(pluginIdLdapDef.requires).toEqual(['id'])
  })

  it('declares no routes — tool-level augmentation only', () => {
    expect(pluginIdLdapDef.routes).toBeUndefined()
  })

  it('feature() throws for unknown actions', () => {
    expect(() => pluginIdLdapDef.feature('unknown')).toThrow(/no feature "unknown"/)
  })

  it('renderFeatures() returns CSV-download VNodes when the group is set', () => {
    setActivePinia(createPinia())
    const result = pluginIdLdapDef.feature('renderFeatures', {
      id: 42,
      node: { id: 'service:id:ldap:foo' },
      parameters: { 'service:id:group': 'engineering' },
    })
    expect(Array.isArray(result)).toBe(true)
    expect(result.length).toBe(2)
    for (const node of result) expect(node.__v_isVNode).toBe(true)
    // Both buttons carry a download href that points at the LDAP activity endpoint.
    const hrefs = result.map((n) => n.props?.href).filter(Boolean)
    expect(hrefs.every((h) => h.includes('/rest/service/id/ldap/activity/42/'))).toBe(true)
    expect(hrefs.some((h) => h.startsWith === undefined ? false : true)).toBe(true)
    expect(hrefs.some((h) => h.includes('/group-engineering-'))).toBe(true)
    expect(hrefs.some((h) => h.includes('/project-engineering-'))).toBe(true)
  })

  it('renderFeatures() returns an empty list when no group is set', () => {
    setActivePinia(createPinia())
    const result = pluginIdLdapDef.feature('renderFeatures', {
      id: 42,
      node: { id: 'service:id:ldap:foo' },
      parameters: {},
    })
    expect(result).toEqual([])
  })

  it('parameterField() returns custom components for OU / parent-group / group in subscribe mode', () => {
    setActivePinia(createPinia())
    const ouComp = pluginIdLdapDef.feature('parameterField', {
      parameter: { id: 'service:id:ou' },
      mode: 'create',
      isNode: false,
    })
    const parentComp = pluginIdLdapDef.feature('parameterField', {
      parameter: { id: 'service:id:parent-group' },
      mode: 'create',
      isNode: false,
    })
    const groupComp = pluginIdLdapDef.feature('parameterField', {
      parameter: { id: 'service:id:group' },
      mode: 'link',
      isNode: false,
    })
    expect(ouComp).toBeTruthy()
    expect(parentComp).toBeTruthy()
    expect(groupComp).toBeTruthy()
  })

  it('parameterField() returns null in node edit/create mode — those forms tweak tool config, not subscriptions', () => {
    setActivePinia(createPinia())
    const result = pluginIdLdapDef.feature('parameterField', {
      parameter: { id: 'service:id:ou' },
      mode: 'create',
      isNode: true,
    })
    expect(result).toBeNull()
  })

  it('parameterField() returns null for parameters the plugin does not customise', () => {
    setActivePinia(createPinia())
    const result = pluginIdLdapDef.feature('parameterField', {
      parameter: { id: 'service:id:ldap:base-dn' },
      mode: 'create',
      isNode: false,
    })
    expect(result).toBeNull()
  })
})

describe('plugin-id delegation to plugin-id-ldap', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    pluginRegistry.register('id-ldap', pluginIdLdapDef)
  })

  afterEach(() => {
    pluginRegistry.remove('id-ldap')
  })

  it('appends id-ldap activity buttons to plugin-id renderFeatures output', () => {
    const result = pluginIdDef.feature('renderFeatures', {
      id: 7,
      node: { id: 'service:id:ldap:local' },
      parameters: { 'service:id:group': 'engineering' },
    })
    // plugin-id no longer contributes a base "manage" button (group management
    // moved onto the clickable chip in renderDetailsKey); with no `help`
    // parameter it contributes 0, plus the two LDAP-contributed activity
    // buttons = 2 VNodes.
    expect(result.length).toBe(2)
    for (const node of result) expect(node.__v_isVNode).toBe(true)
  })

  it('does not delegate when the subscription is not on an LDAP node', () => {
    const result = pluginIdDef.feature('renderFeatures', {
      id: 7,
      // Different tool segment — sub-plugin lookup resolves to `id-other`
      // which isn't registered, so the parent's output is unchanged.
      node: { id: 'service:id:other:local' },
      parameters: { 'service:id:group': 'engineering' },
    })
    // No base button anymore (manage moved to the chip) and `id-other` is not
    // registered → no delegation → empty output.
    expect(result.length).toBe(0)
  })
})
