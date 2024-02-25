/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
define({
	root: {
		'service:id:ldap:base-dn': 'Base DN',
		'service:id:ldap:companies-dn': 'Companies DN',
	    'service:id:ldap:companies-class': 'Companies classes',
	    'service:id:ldap:companies-class-description': 'LDAP object classes of users for search. Comma and space separated values.',
	    'service:id:ldap:companies-class-create': 'Companies class (create)',
	    'service:id:ldap:companies-class-create-description': 'LDAP object classes of companies for the creation. Comma and space separated values. When empty, use the first of search classes.',
		'service:id:ldap:company-pattern': 'Company pattern capture id from DN',
		'service:id:ldap:department-attribute': 'Department attribute',
		'service:id:ldap:groups-dn': 'Groups DN',
		'service:id:ldap:groups-class': 'Groups classes',
		'service:id:ldap:groups-class-description':'LDAP object classes of groups for search. Comma and space separated values.',
		'service:id:ldap:groups-class-create': 'Groups class (create)',
		'service:id:ldap:groups-class-create-description': 'LDAP object classes of groups for the creation. Comma and space separated values. When empty, use the first of search classes.',
		'service:id:ldap:groups-member-attribute': 'Group member attribute',
		'service:id:ldap:local-id-attribute': 'Local ID attribute',
		'service:id:ldap:locked-attribute': 'Locked attribute',
		'service:id:ldap:locked-value': 'Locked match value',
	    'service:id:ldap:login-attributes': 'Login attributes',
        'service:id:ldap:login-attributes-description': 'Accepted authentication LDAP attributes. Use commas and spaces as separator. Ignored when `service:id:ldap:self-search` is `false`',
		'service:id:ldap:password': 'Connection password',
		'service:id:ldap:people-class': 'People classes',
		'service:id:ldap:people-class-description': 'LDAP object classes of users for search. Comma and space separated values',
		'service:id:ldap:people-class-create': 'People class (create)',
		'service:id:ldap:people-class-create-description': 'LDAP object classes of users for the creation. Comma and space separated values. When empty, use the first of search classes.',
		'service:id:ldap:people-dn': 'People DN',
		'service:id:ldap:people-internal-dn': 'People internal DN',
		'service:id:ldap:quarantine-dn': 'Quarantine DN',
		'service:id:ldap:referral': 'Referral mode',
		'service:id:ldap:referral-description': 'When provided, the given referrals instruction are followed.',
		'service:id:ldap:self-search': 'Users can search',
		'service:id:ldap:self-search-description': 'When checked, at authentication time the DN is retrieved from the LDAP server with a search using the provided user credentials. Otherwise, the DN is computed from the cache database and a single bind is executed.',
		'service:id:ldap:uid-attribute': 'UID attribute',
		'service:id:ldap:url': 'Connection URL',
		'service:id:ldap:user-dn': 'Connection user',
		'service:id:ldap:clear-password': 'Clear password'
	},
	fr: true
});
