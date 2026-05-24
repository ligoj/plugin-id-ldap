import { APP_BASE as e, VBtn as t, VIcon as n, useApi as r, useI18nStore as i } from "@ligoj/host";
import { computed as a, createBlock as o, createElementBlock as s, createVNode as c, h as l, openBlock as u, ref as d, resolveComponent as f, unref as p, watch as m } from "vue";
//#region src/i18n/en.js
var h = {
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
}, g = {
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
}, _ = {
	__name: "IdParentGroupField",
	props: {
		modelValue: {
			type: [
				String,
				Number,
				null
			],
			default: null
		},
		parameter: {
			type: Object,
			required: !0
		},
		formValues: {
			type: Object,
			default: () => ({})
		},
		mode: {
			type: String,
			default: null
		},
		isNode: {
			type: Boolean,
			default: !1
		},
		project: {
			type: Object,
			default: null
		}
	},
	emits: ["update:modelValue"],
	setup(e, { emit: t }) {
		let n = t, { t: a } = i(), s = r(), c = d([]), l = d(!1), m = null;
		function h(e) {
			e && !c.value.length && g("");
		}
		async function g(e) {
			let t = (e || "").trim();
			if (t !== m) {
				m = t, l.value = !0;
				try {
					let e = await s.get(`rest/service/id/group?search[value]=${encodeURIComponent(t)}`);
					c.value = (Array.isArray(e) ? e : e?.data || []).map((e) => ({
						id: e.id ?? e.name,
						name: e.name ?? e.id
					}));
				} catch (e) {
					console.warn("[id-ldap] parent-group lookup failed", e), c.value = [];
				} finally {
					l.value = !1;
				}
			}
		}
		function _(e) {
			n("update:modelValue", e ?? "");
		}
		return (t, n) => {
			let r = f("v-autocomplete");
			return u(), o(r, {
				"model-value": e.modelValue,
				label: p(a)("service:id:parent-group"),
				hint: p(a)("service:id:parent-group-description"),
				"persistent-hint": !!p(a)("service:id:parent-group-description"),
				items: c.value,
				loading: l.value,
				"item-title": "name",
				"item-value": "id",
				variant: "outlined",
				density: "compact",
				clearable: "",
				"no-filter": "",
				"onUpdate:search": g,
				"onUpdate:menu": h,
				"onUpdate:modelValue": _
			}, null, 8, [
				"model-value",
				"label",
				"hint",
				"persistent-hint",
				"items",
				"loading"
			]);
		};
	}
}, v = {
	__name: "IdOuField",
	props: {
		modelValue: {
			type: [
				String,
				Object,
				null
			],
			default: null
		},
		parameter: {
			type: Object,
			required: !0
		},
		formValues: {
			type: Object,
			default: () => ({})
		},
		mode: {
			type: String,
			default: null
		},
		isNode: {
			type: Boolean,
			default: !1
		},
		project: {
			type: Object,
			default: null
		},
		nodeId: {
			type: String,
			default: null
		}
	},
	emits: ["update:modelValue"],
	setup(e, { emit: t }) {
		let n = e, s = t, { t: c } = i(), l = r(), m = d([]), h = d(!1), g = d(null), _ = null, v = a(() => c("service:id:ou-description")), y = a(() => !!(n.parameter?.mandatory || n.parameter?.required)), b = a(() => y.value ? [(e) => e != null && e !== "" || "Required"] : []);
		function x(e) {
			e && !m.value.length && S("");
		}
		async function S(e) {
			let t = (e || "").trim();
			if (t !== _) {
				_ = t, h.value = !0;
				try {
					let e = n.nodeId || "service:id:ldap", r = `rest/service/id/ldap/customer/${encodeURIComponent(e)}/${encodeURIComponent(t)}`, i = await l.get(r);
					m.value = (Array.isArray(i) ? i : i?.data || []).map((e) => ({
						id: e.id ?? e.name,
						name: e.name ?? e.id
					}));
				} catch (e) {
					console.warn("[id-ldap] ou lookup failed", e), m.value = [];
				} finally {
					h.value = !1;
				}
			}
		}
		function C(e) {
			if (e && typeof e == "object") g.value = null, s("update:modelValue", e.id || e.name || "");
			else {
				let t = String(e || "").trim();
				g.value = t ? c("service:id:ou-not-exists") : null, s("update:modelValue", t);
			}
		}
		return (t, n) => {
			let r = f("v-combobox");
			return u(), o(r, {
				"model-value": e.modelValue,
				label: p(c)("service:id:ou"),
				hint: v.value,
				"persistent-hint": "",
				messages: g.value ? [g.value] : [],
				items: m.value,
				loading: h.value,
				"item-title": "name",
				"item-value": "id",
				"return-object": "",
				variant: "outlined",
				density: "compact",
				rules: b.value,
				"no-filter": "",
				"onUpdate:search": S,
				"onUpdate:menu": x,
				"onUpdate:modelValue": C
			}, null, 8, [
				"model-value",
				"label",
				"hint",
				"messages",
				"items",
				"loading",
				"rules"
			]);
		};
	}
}, y = { key: 0 }, b = {
	"service:id:parent-group": _,
	"service:id:ou": v,
	"service:id:group": {
		__name: "IdGroupField",
		props: {
			modelValue: {
				type: [
					String,
					Number,
					null
				],
				default: null
			},
			parameter: {
				type: Object,
				required: !0
			},
			formValues: {
				type: Object,
				default: () => ({})
			},
			mode: {
				type: String,
				default: null
			},
			isNode: {
				type: Boolean,
				default: !1
			},
			project: {
				type: Object,
				default: null
			}
		},
		emits: ["update:modelValue"],
		setup(e, { emit: t }) {
			let n = e, l = t, { t: h } = i(), g = r(), _ = a(() => !n.isNode && n.mode === "create"), v = d(""), b = a(() => {
				let e = n.formValues?.["service:id:parent-group"];
				if (e) return String(e);
				let t = n.formValues?.["service:id:ou"];
				return t ? String(t) : "";
			}), x = a(() => {
				let e = String(v.value || "").toLowerCase();
				return e ? b.value ? `${b.value}-${e}` : e : b.value;
			}), S = a(() => n.project?.pkey || ""), C = d(!1), w = d(null), T = null;
			async function E() {
				w.value = null;
				let e = x.value;
				if (!_.value || !e) return;
				if (S.value && e !== S.value && !e.startsWith(`${S.value}-`)) {
					w.value = h("service:id:group-starts-with-pkey") === "service:id:group-starts-with-pkey" ? `Group name must start with "${S.value}-".` : h("service:id:group-starts-with-pkey", { pkey: S.value });
					return;
				}
				let t = Symbol("exists");
				T = t, C.value = !0;
				try {
					let n = await g.get(`rest/service/id/group/${encodeURIComponent(e)}/exists`);
					if (T !== t) return;
					(n === !0 || n === "true") && (w.value = h("service:id:group-already-exists") === "service:id:group-already-exists" ? `A group named "${e}" already exists.` : h("service:id:group-already-exists", { name: e }));
				} catch (e) {
					if (T !== t) return;
					console.warn("[id-ldap] group exists probe failed", e);
				} finally {
					T === t && (C.value = !1);
				}
			}
			m(x, (e) => l("update:modelValue", e), { immediate: !0 }), m([x, () => S.value], () => {
				E();
			}, { immediate: !0 });
			let D = (e) => e != null && String(e).trim() !== "" || "Required", O = [D], k = a(() => _.value ? O : []), A = d([]), j = d(!1), M = null;
			function N(e) {
				e && !_.value && !A.value.length && P("");
			}
			async function P(e) {
				let t = (e || "").trim();
				if (t !== M) {
					M = t, j.value = !0;
					try {
						let e = await g.get(`rest/service/id/group?search[value]=${encodeURIComponent(t)}`);
						A.value = (Array.isArray(e) ? e : e?.data || []).map((e) => ({
							id: e.id ?? e.name,
							name: e.name ?? e.id
						}));
					} catch (e) {
						console.warn("[id-ldap] group lookup failed", e), A.value = [];
					} finally {
						j.value = !1;
					}
				}
			}
			let F = a(() => n.parameter?.mandatory || n.parameter?.required ? [D] : []);
			return (t, n) => {
				let r = f("v-text-field"), i = f("v-autocomplete");
				return _.value ? (u(), s("div", y, [c(r, {
					modelValue: v.value,
					"onUpdate:modelValue": n[0] ||= (e) => v.value = e,
					label: p(h)("service:id:group-simple-name"),
					hint: p(h)("service:id:group-simple-name-description"),
					"persistent-hint": !!p(h)("service:id:group-simple-name-description"),
					"error-messages": w.value ? [w.value] : [],
					loading: C.value,
					rules: k.value,
					variant: "outlined",
					density: "compact",
					class: "mb-2",
					required: ""
				}, null, 8, [
					"modelValue",
					"label",
					"hint",
					"persistent-hint",
					"error-messages",
					"loading",
					"rules"
				]), c(r, {
					"model-value": x.value,
					label: p(h)("service:id:group"),
					placeholder: p(h)("service:id:ldap:group-create"),
					messages: x.value ? [x.value] : [],
					readonly: "",
					variant: "outlined",
					density: "compact"
				}, null, 8, [
					"model-value",
					"label",
					"placeholder",
					"messages"
				])])) : (u(), o(i, {
					key: 1,
					"model-value": e.modelValue,
					label: p(h)("service:id:group"),
					items: A.value,
					loading: j.value,
					"item-title": "name",
					"item-value": "id",
					variant: "outlined",
					density: "compact",
					clearable: "",
					"no-filter": "",
					rules: F.value,
					"onUpdate:search": P,
					"onUpdate:menu": N,
					"onUpdate:modelValue": n[1] ||= (e) => l("update:modelValue", e ?? "")
				}, null, 8, [
					"model-value",
					"label",
					"items",
					"loading",
					"rules"
				]));
			};
		}
	}
};
function x(e) {
	return e?.parameters?.["service:id:group"] ?? null;
}
function S() {
	let e = /* @__PURE__ */ new Date();
	return `${e.getFullYear()}-${String(e.getMonth() + 1).padStart(2, "0")}-${String(e.getDate()).padStart(2, "0")}`;
}
var C = {
	renderFeatures(r) {
		let { t: a } = i(), o = x(r);
		if (!r?.id || !o) return [];
		let s = S(), c = `${e}rest/service/id/ldap/activity/${r.id}`;
		return [l(t, {
			icon: !0,
			size: "small",
			variant: "text",
			href: `${c}/group-${encodeURIComponent(o)}-${s}.csv`,
			download: "",
			title: a("id.renderFeatures.activityGroup"),
			rel: "noopener"
		}, () => l(n, { size: "small" }, () => "mdi-file-table-outline")), l(t, {
			icon: !0,
			size: "small",
			variant: "text",
			href: `${c}/project-${encodeURIComponent(o)}-${s}.csv`,
			download: "",
			title: a("id.renderFeatures.activityProject"),
			rel: "noopener"
		}, () => l(n, { size: "small" }, () => "mdi-file-chart-outline"))];
	},
	parameterField({ parameter: e, isNode: t } = {}) {
		return t ? null : b[e?.id] || null;
	}
}, w = {
	renderFeatures: C.renderFeatures,
	parameterField: C.parameterField
}, T = {
	id: "id-ldap",
	label: "Identity LDAP",
	requires: ["id"],
	install() {
		let e = i();
		e.merge(h, "en"), e.merge(g, "fr");
	},
	feature(e, ...t) {
		let n = w[e];
		if (!n) throw Error(`Plugin "id-ldap" has no feature "${e}"`);
		return n(...t);
	},
	service: C,
	meta: {
		icon: "mdi-folder-network-outline",
		color: "blue-grey-darken-2"
	}
};
//#endregion
export { T as default, C as service };
