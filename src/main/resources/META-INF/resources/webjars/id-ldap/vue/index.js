import { APP_BASE as e, VBtn as t, VIcon as n, useI18nStore as r } from "@ligoj/host";
import { h as i } from "vue";
//#region src/i18n/en.js
var a = {
	"id.renderFeatures.export": "Export",
	"id.renderFeatures.activityGroup": "Group activity (CSV)",
	"id.renderFeatures.activityProject": "Project activity (CSV)",
	"service:id:ldap:base-dn": "Base DN",
	"service:id:ldap:url": "Connection URLs",
	"service:id:ldap:url-description": "Connection URLs, comma separated. Failover mode only, where the first one is used in priority",
	"service:id:ldap:user-dn": "Connection user",
	"service:id:ldap:password": "Connection password",
	"service:id:ldap:clear-password": "Clear password",
	"service:id:ldap:local-id-attribute": "Local ID attribute",
	"service:id:ldap:locked-attribute": "Locked attribute",
	"service:id:ldap:locked-value": "Locked match value",
	"service:id:ldap:referral": "Referral mode",
	"service:id:ldap:referral-description": "When provided, the given referrals instruction are followed.",
	"service:id:ldap:self-search": "Users can search",
	"service:id:ldap:self-search-description": "When checked, at authentication time the DN is retrieved from the LDAP server with a search using the provided user's credentials. Otherwise, the DN is computed from the cache database and a single bind is executed.",
	"service:id:ldap:department-attribute": "Department attribute",
	"service:id:ldap:login-attributes": "Login attributes",
	"service:id:ldap:login-attributes-description": "Accepted authentication LDAP attributes. Use commas and spaces as separator. Ignored when `service:id:ldap:self-search` is `false`",
	"service:id:ldap:uid-attribute": "UID attribute",
	"service:id:ldap:people-dn": "People DN",
	"service:id:ldap:people-internal-dn": "People internal DN",
	"service:id:ldap:quarantine-dn": "Quarantine DN",
	"service:id:ldap:people-class": "People classes",
	"service:id:ldap:people-class-description": "LDAP object classes of users for search. Comma or space separated values",
	"service:id:ldap:people-class-create": "People classes (create)",
	"service:id:ldap:people-class-create-description": "LDAP object classes of users for the creation. Comma or space separated values. When empty, use the first of search classes.",
	"service:id:ldap:people-custom-attributes": "Custom attributes",
	"service:id:ldap:people-custom-attributes-description": "List of custom user LDAP attribute names. Comma or space separated values",
	"service:id:ldap:groups-dn": "Groups DN",
	"service:id:ldap:groups-class": "Groups classes",
	"service:id:ldap:groups-class-description": "LDAP object classes of groups for search. Comma or space separated values.",
	"service:id:ldap:groups-class-create": "Groups classes (create)",
	"service:id:ldap:groups-class-create-description": "LDAP object classes of groups for the creation. Comma or space separated values. When empty, use the first of search classes.",
	"service:id:ldap:groups-member-attribute": "Group member attribute",
	"service:id:ldap:companies-dn": "Companies DN",
	"service:id:ldap:companies-class": "Companies classes",
	"service:id:ldap:companies-class-description": "LDAP object classes of users for search. Comma or space separated values.",
	"service:id:ldap:companies-class-create": "Companies classes (create)",
	"service:id:ldap:companies-class-create-description": "LDAP object classes of companies for the creation. Comma or space separated values. When empty, use the first of search classes.",
	"service:id:ldap:company-pattern": "Company pattern capture id from DN",
	"service:id:ldap:group-create": "Group name (computed)"
}, o = {
	"id.renderFeatures.export": "Exporter",
	"id.renderFeatures.activityGroup": "Activité du groupe (CSV)",
	"id.renderFeatures.activityProject": "Activité du projet (CSV)",
	"service:id:ldap:base-dn": "Base DN",
	"service:id:ldap:url": "URLs",
	"service:id:ldap:url-description": "URLs de connexion séparées par une virgule. Mode failover supporté uniquement, la première URL étant utilisée en priorité",
	"service:id:ldap:user-dn": "Utilisateur de connexion",
	"service:id:ldap:password": "Mot de passe de connexion",
	"service:id:ldap:clear-password": "Mot de passe non-crypté",
	"service:id:ldap:local-id-attribute": "Attribut d'identifiant local",
	"service:id:ldap:locked-attribute": "Attribut de verrouillage",
	"service:id:ldap:locked-value": "Valeur de verrouillage",
	"service:id:ldap:referral": "Mode referral",
	"service:id:ldap:referral-description": "Si renseigné, les instructions données de suivi seront exécutées.",
	"service:id:ldap:self-search": "Les utilisateurs peuvent rechercher",
	"service:id:ldap:self-search-description": "Lorsque coché, au moment de l'authentification le DN est récupéré par une recherche en utilisant les secrets de l'utilisateur. Sinon, le DN est calculé à partir du cache de données et seule une authentification est effectuée.",
	"service:id:ldap:department-attribute": "Attribut de département",
	"service:id:ldap:login-attributes": "Attributs de login",
	"service:id:ldap:login-attributes-description": "Attributs LDAP autorisés pour l'authentification. Utiliser des virgules et des espaces comme séparateurs. Ignoré lorsque `service:id:ldap:self-search` est `false`",
	"service:id:ldap:uid-attribute": "Attribut UID",
	"service:id:ldap:people-dn": "DN des personnes",
	"service:id:ldap:people-internal-dn": "DN interne des personnes",
	"service:id:ldap:quarantine-dn": "DN de quarantaine",
	"service:id:ldap:people-class": "Classes des personnes",
	"service:id:ldap:people-class-description": "Classes LDAP des personnes à rechercher. Séparées par des espaces ou virgules.",
	"service:id:ldap:people-class-create": "Classes des personnes (création)",
	"service:id:ldap:people-class-create-description": "Classes LDAP des personnes à créer. Séparées par des espaces ou virgules. Si vide, la première des classes de recherche est utilisée.",
	"service:id:ldap:people-custom-attributes": "Attributs personnalisés",
	"service:id:ldap:people-custom-attributes-description": "Liste d'attributs LDAP obligatoires pour les utilisateurs. Séparés par des espaces ou virgules.",
	"service:id:ldap:groups-dn": "DN des groupes",
	"service:id:ldap:groups-class": "Classes des groupes",
	"service:id:ldap:groups-class-description": "Classes LDAP des groupes à rechercher. Séparées par des espaces ou virgules.",
	"service:id:ldap:groups-class-create": "Classes des groupes (création)",
	"service:id:ldap:groups-class-create-description": "Classes LDAP des groupes à créer. Séparées par des espaces ou virgules. Si vide, la première des classes de recherche est utilisée.",
	"service:id:ldap:groups-member-attribute": "Attribut des membres",
	"service:id:ldap:companies-dn": "DN des sociétés",
	"service:id:ldap:companies-class": "Classes des sociétés",
	"service:id:ldap:companies-class-description": "Classes LDAP des sociétés à rechercher. Séparées par des espaces ou virgules.",
	"service:id:ldap:companies-class-create": "Classes des sociétés (création)",
	"service:id:ldap:companies-class-create-description": "Classes LDAP des sociétés à créer. Séparées par des espaces ou virgules. Si vide, la première des classes de recherche est utilisée.",
	"service:id:ldap:company-pattern": "Pattern de capture de l'identifiant de société dans un DN",
	"service:id:ldap:group-create": "Nom du groupe (calculé)"
};
//#endregion
//#region src/service.js
function s(e) {
	return e?.parameters?.["service:id:group"] ?? null;
}
function c() {
	let e = /* @__PURE__ */ new Date();
	return `${e.getFullYear()}-${String(e.getMonth() + 1).padStart(2, "0")}-${String(e.getDate()).padStart(2, "0")}`;
}
var l = { renderFeatures(a) {
	let { t: o } = r(), l = s(a);
	if (!a?.id || !l) return [];
	let u = c(), d = `${e}rest/service/id/ldap/activity/${a.id}`;
	return [i(t, {
		icon: !0,
		size: "small",
		variant: "text",
		href: `${d}/group-${encodeURIComponent(l)}-${u}.csv`,
		download: "",
		title: o("id.renderFeatures.activityGroup"),
		rel: "noopener"
	}, () => i(n, { size: "small" }, () => "mdi-file-table-outline")), i(t, {
		icon: !0,
		size: "small",
		variant: "text",
		href: `${d}/project-${encodeURIComponent(l)}-${u}.csv`,
		download: "",
		title: o("id.renderFeatures.activityProject"),
		rel: "noopener"
	}, () => i(n, { size: "small" }, () => "mdi-file-chart-outline"))];
} }, u = { renderFeatures: l.renderFeatures }, d = {
	id: "id-ldap",
	label: "Identity LDAP",
	requires: ["id"],
	install() {
		let e = r();
		e.merge(a, "en"), e.merge(o, "fr");
	},
	feature(e, ...t) {
		let n = u[e];
		if (!n) throw Error(`Plugin "id-ldap" has no feature "${e}"`);
		return n(...t);
	},
	service: l,
	meta: {
		icon: "mdi-folder-network-outline",
		color: "blue-grey-darken-2"
	}
};
//#endregion
export { d as default, l as service };
