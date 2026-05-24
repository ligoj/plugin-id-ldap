// Plugin-local translations merged into the host i18n store at install
// time. Keep keys flat (dot-separated) to match the host's convention.
//
// Most of these keys are LDAP-specific parameter labels — the parameter
// inputs are auto-rendered by the subscribe wizard from the backend's
// metadata; supplying labels here is what turns the raw parameter ids
// like `service:id:ldap:base-dn` into user-friendly form labels.
export default {
  // Subscription row actions contributed via renderFeatures. Reuse the
  // existing `id.renderFeatures.export` umbrella key the host already
  // expects, but add LDAP-specific labels for the two CSV exports.
  'id.renderFeatures.export': 'Export',
  'id.renderFeatures.activityGroup': 'Group activity (CSV)',
  'id.renderFeatures.activityProject': 'Project activity (CSV)',

  // LDAP connection (server)
  'service:id:ldap:base-dn': 'Base DN',
  'service:id:ldap:url': 'Connection URLs',
  'service:id:ldap:url-description': 'Connection URLs, comma separated. Failover mode only, where the first one is used in priority',
  'service:id:ldap:user-dn': 'Connection user',
  'service:id:ldap:password': 'Connection password',
  'service:id:ldap:clear-password': 'Clear password',

  // Authentication
  'service:id:ldap:local-id-attribute': 'Local ID attribute',
  'service:id:ldap:locked-attribute': 'Locked attribute',
  'service:id:ldap:locked-value': 'Locked match value',
  'service:id:ldap:referral': 'Referral mode',
  'service:id:ldap:referral-description': 'When provided, the given referrals instruction are followed.',
  'service:id:ldap:self-search': 'Users can search',
  'service:id:ldap:self-search-description': 'When checked, at authentication time the DN is retrieved from the LDAP server with a search using the provided user\'s credentials. Otherwise, the DN is computed from the cache database and a single bind is executed.',
  'service:id:ldap:department-attribute': 'Department attribute',
  'service:id:ldap:login-attributes': 'Login attributes',
  'service:id:ldap:login-attributes-description': 'Accepted authentication LDAP attributes. Use commas and spaces as separator. Ignored when `service:id:ldap:self-search` is `false`',
  'service:id:ldap:uid-attribute': 'UID attribute',

  // People
  'service:id:ldap:people-dn': 'People DN',
  'service:id:ldap:people-internal-dn': 'People internal DN',
  'service:id:ldap:quarantine-dn': 'Quarantine DN',
  'service:id:ldap:people-class': 'People classes',
  'service:id:ldap:people-class-description': 'LDAP object classes of users for search. Comma or space separated values',
  'service:id:ldap:people-class-create': 'People classes (create)',
  'service:id:ldap:people-class-create-description': 'LDAP object classes of users for the creation. Comma or space separated values. When empty, use the first of search classes.',
  'service:id:ldap:people-custom-attributes': 'Custom attributes',
  'service:id:ldap:people-custom-attributes-description': 'List of custom user LDAP attribute names. Comma or space separated values',

  // Groups
  'service:id:ldap:groups-dn': 'Groups DN',
  'service:id:ldap:groups-class': 'Groups classes',
  'service:id:ldap:groups-class-description': 'LDAP object classes of groups for search. Comma or space separated values.',
  'service:id:ldap:groups-class-create': 'Groups classes (create)',
  'service:id:ldap:groups-class-create-description': 'LDAP object classes of groups for the creation. Comma or space separated values. When empty, use the first of search classes.',
  'service:id:ldap:groups-member-attribute': 'Group member attribute',

  // Companies
  'service:id:ldap:companies-dn': 'Companies DN',
  'service:id:ldap:companies-class': 'Companies classes',
  'service:id:ldap:companies-class-description': 'LDAP object classes of users for search. Comma or space separated values.',
  'service:id:ldap:companies-class-create': 'Companies classes (create)',
  'service:id:ldap:companies-class-create-description': 'LDAP object classes of companies for the creation. Comma or space separated values. When empty, use the first of search classes.',
  'service:id:ldap:company-pattern': 'Company pattern capture id from DN',

  // Misc
  'service:id:ldap:group-create': 'Group name (computed)',
}
