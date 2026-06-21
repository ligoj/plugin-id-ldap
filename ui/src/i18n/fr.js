// Traductions locales du plugin, fusionnées dans le store i18n de l'hôte
// au moment de l'installation. Clés à plat (séparées par des points) pour
// rester cohérent avec la convention de l'hôte.
export default {
  // Actions de ligne d'abonnement (renderFeatures).
  'id.renderFeatures.export': 'Exporter',
  'id.renderFeatures.activityGroup': 'Activité du groupe (CSV)',
  'id.renderFeatures.activityProject': 'Activité du projet (CSV)',

  // Connexion LDAP (serveur)
  'service:id:ldap:base-dn': 'Base DN',
  'service:id:ldap:url': 'URLs',
  'service:id:ldap:url-description': 'URLs de connexion séparées par une virgule. Mode failover supporté uniquement, la première URL étant utilisée en priorité',
  'service:id:ldap:user-dn': 'Utilisateur de connexion',
  'service:id:ldap:password': 'Mot de passe de connexion',
  'service:id:ldap:clear-password': 'Mot de passe non-crypté',

  // Authentification
  'service:id:ldap:local-id-attribute': 'Attribut d\'identifiant local',
  'service:id:ldap:locked-attribute': 'Attribut de verrouillage',
  'service:id:ldap:locked-value': 'Valeur de verrouillage',
  'service:id:ldap:referral': 'Mode referral',
  'service:id:ldap:referral-description': 'Si renseigné, les instructions données de suivi seront exécutées.',
  'service:id:ldap:self-search': 'Les utilisateurs peuvent rechercher',
  'service:id:ldap:self-search-description': 'Lorsque coché, au moment de l\'authentification le DN est récupéré par une recherche en utilisant les secrets de l\'utilisateur. Sinon, le DN est calculé à partir du cache de données et seule une authentification est effectuée.',
  'service:id:ldap:department-attribute': 'Attribut de département',
  'service:id:ldap:login-attributes': 'Attributs de login',
  'service:id:ldap:login-attributes-description': 'Attributs LDAP autorisés pour l\'authentification. Utiliser des virgules et des espaces comme séparateurs. Ignoré lorsque `service:id:ldap:self-search` est `false`',
  'service:id:ldap:uid-attribute': 'Attribut UID',

  // Personnes
  'service:id:ldap:people-dn': 'DN des personnes',
  'service:id:ldap:people-internal-dn': 'DN interne des personnes',
  'service:id:ldap:quarantine-dn': 'DN de quarantaine',
  'service:id:ldap:people-class': 'Classes des personnes',
  'service:id:ldap:people-class-description': 'Classes LDAP des personnes à rechercher. Séparées par des espaces ou virgules.',
  'service:id:ldap:people-class-create': 'Classes des personnes (création)',
  'service:id:ldap:people-class-create-description': 'Classes LDAP des personnes à créer. Séparées par des espaces ou virgules. Si vide, la première des classes de recherche est utilisée.',
  'service:id:ldap:people-custom-attributes': 'Attributs personnalisés',
  'service:id:ldap:people-custom-attributes-description': 'Liste d\'attributs LDAP obligatoires pour les utilisateurs. Séparés par des espaces ou virgules.',

  // Groupes
  'service:id:ldap:groups-dn': 'DN des groupes',
  'service:id:ldap:groups-class': 'Classes des groupes',
  'service:id:ldap:groups-class-description': 'Classes LDAP des groupes à rechercher. Séparées par des espaces ou virgules.',
  'service:id:ldap:groups-class-create': 'Classes des groupes (création)',
  'service:id:ldap:groups-class-create-description': 'Classes LDAP des groupes à créer. Séparées par des espaces ou virgules. Si vide, la première des classes de recherche est utilisée.',
  'service:id:ldap:groups-member-attribute': 'Attribut des membres',

  // Sociétés
  'service:id:ldap:companies-dn': 'DN des sociétés',
  'service:id:ldap:companies-class': 'Classes des sociétés',
  'service:id:ldap:companies-class-description': 'Classes LDAP des sociétés à rechercher. Séparées par des espaces ou virgules.',
  'service:id:ldap:companies-class-create': 'Classes des sociétés (création)',
  'service:id:ldap:companies-class-create-description': 'Classes LDAP des sociétés à créer. Séparées par des espaces ou virgules. Si vide, la première des classes de recherche est utilisée.',
  'service:id:ldap:company-pattern': 'Pattern de capture de l\'identifiant de société dans un DN',

  // Divers
  'service:id:ldap:group-create': 'Nom du groupe (calculé)',
}
