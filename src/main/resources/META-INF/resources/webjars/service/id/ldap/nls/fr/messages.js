/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
define({
	'service:id:ldap:base-dn': 'Base DN',
	'service:id:ldap:companies-dn': 'DN des sociétés',
	'service:id:ldap:companies-class': 'Classe des sociétés',
	'service:id:ldap:company-pattern': 'Pattern de capture de l\'identifiant de société dans un DN',
	'service:id:ldap:department-attribute': 'Attribut de département',
	'service:id:ldap:groups-dn': 'DN des groupes',
	'service:id:ldap:groups-class': 'Classe des groupes',
	'service:id:ldap:groups-member-attribute': 'Attribut des membres',
	'service:id:ldap:local-id-attribute': 'Attribut d\'identifiant local',
	'service:id:ldap:locked-attribute': 'Attribut de verrouillage',
	'service:id:ldap:locked-value': 'Valeur de verrouillage',
	'service:id:ldap:password': 'Mot de passe de connexion',
	'service:id:ldap:people-class': 'Class des personnes',
	'service:id:ldap:people-dn': 'DN des personnes',
	'service:id:ldap:people-internal-dn': 'People internal DN',
	'service:id:ldap:quarantine-dn': 'DN de quarantaine',
	'service:id:ldap:referral': 'Mode referral',
    'service:id:ldap:referral-description': 'Si renseigné, les instructions données de suivi seront exécutées.',
	'service:id:ldap:self-search': 'Utilisateurs peuvent rechercher',
    'service:id:ldap:self-search-description': 'Lorsque coché, au oment de l\'authentification le DN est récupéré par une recherche en utilisant les secrets de l\'utilisateur. Sinon, le DN est calculé à partir du cache de données et seule une authentification est effectuée.',
	'service:id:ldap:uid-attribute': 'Attribut UID',
	'service:id:ldap:url': 'URL de connexion',
	'service:id:ldap:user-dn': 'Utilisateur de connexion',
	'service:id:ldap:clear-password': 'Mot de passe non-crypté'
});
