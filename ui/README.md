# plugin-id-ldap UI

Vue sources for the Ligoj "id-ldap" tool-level plugin. Augments `plugin-id`
with LDAP-specific labels and subscription row actions (activity export).

Built with Vite in library mode; the output bundle is placed under the
Java module's webjars classpath so the Ligoj host serves it at
`/main/id-ldap/vue/index.js`.

## Layout

```
ui/
├── package.json
├── vite.config.js            # library build → ../src/main/resources/.../webjars/id-ldap/vue/
├── index.html                # standalone dev entry
└── src/
    ├── index.js              # plugin contract entry (default export)
    ├── service.js            # feature implementations (renderFeatures)
    └── i18n/{en,fr}.js       # LDAP-specific parameter labels
```

## Commands

```sh
npm install
npm run dev        # standalone dev server on :5175; proxies REST to :8080
npm run build      # writes ../src/main/resources/META-INF/resources/webjars/id-ldap/vue/index.js
```

## Delegation contract

`plugin-id` walks the subscription node id (`service:id:<tool>:...`) and
delegates `renderFeatures` to the sub-plugin registered as `id-<tool>`.
For LDAP subscriptions (`service:id:ldap:*`) that lookup resolves to
this plugin, and the activity-export buttons it returns get appended to
the parent's "Manage members" / "Help" set.

## Shared dependencies

`vue`, `vue-router`, `pinia`, and `vuetify` are kept **external** in the
build output — the host resolves them via an import map so the plugin
and host share the same module instances.
