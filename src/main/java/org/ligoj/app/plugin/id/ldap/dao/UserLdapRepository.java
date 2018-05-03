/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.id.ldap.dao;

import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.naming.AuthenticationException;
import javax.naming.Context;
import javax.naming.Name;
import javax.naming.NamingException;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.DirContext;
import javax.naming.directory.InvalidAttributeValueException;
import javax.naming.directory.ModificationItem;
import javax.naming.directory.SearchControls;
import javax.naming.ldap.LdapContext;
import javax.naming.ldap.LdapName;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.IteratorUtils;
import org.apache.commons.lang3.CharUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.RandomStringGenerator;
import org.hibernate.validator.internal.constraintvalidators.hv.EmailValidator;
import org.ligoj.app.api.Normalizer;
import org.ligoj.app.iam.CompanyOrg;
import org.ligoj.app.iam.GroupOrg;
import org.ligoj.app.iam.IUserRepository;
import org.ligoj.app.iam.SimpleUser;
import org.ligoj.app.iam.SimpleUserOrg;
import org.ligoj.app.iam.UserOrg;
import org.ligoj.app.plugin.id.DnUtils;
import org.ligoj.app.plugin.id.ldap.dao.LdapCacheRepository.LdapData;
import org.ligoj.app.plugin.id.model.CompanyComparator;
import org.ligoj.app.plugin.id.model.FirstNameComparator;
import org.ligoj.app.plugin.id.model.LastNameComparator;
import org.ligoj.app.plugin.id.model.LoginComparator;
import org.ligoj.app.plugin.id.model.MailComparator;
import org.ligoj.bootstrap.core.DateUtils;
import org.ligoj.bootstrap.core.json.InMemoryPagination;
import org.ligoj.bootstrap.core.resource.BusinessException;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.ldap.core.ContextExecutor;
import org.springframework.ldap.core.DirContextAdapter;
import org.springframework.ldap.core.DirContextOperations;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.AbstractContextMapper;
import org.springframework.ldap.filter.AndFilter;
import org.springframework.ldap.filter.EqualsFilter;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * User LDAP repository
 */
@Slf4j
public class UserLdapRepository implements IUserRepository {

	private static final String OPEN_LDAP_DATE_FORMAT = "yyyyMMddHHmmss'Z'";

	private static final String LDAP_CONNECT_POOL = "com.sun.jndi.ldap.connect.pool";

	/**
	 * User password LDAP attribute.
	 */
	private static final String PASSWORD_ATTRIBUTE = "userPassword";

	/**
	 * This attribute contains the time that the user's account was locked. If the account has been locked, the password
	 * may no longer be used to authenticate the user to the directory. If pwdAccountLockedTime is set to 000001010000Z,
	 * the user's account has been permanently locked and may only be unlocked by an administrator. Note that account
	 * locking only takes effect when the pwdLockout password policy attribute is set to <code>TRUE</code>.
	 */
	private static final String PWD_ACCOUNT_LOCKED_ATTRIBUTE = "pwdAccountLockedTime";

	/**
	 * User SN LDAP attribute.
	 */
	private static final String SN_ATTRIBUTE = "sn";

	/**
	 * User givenName LDAP attribute.
	 */
	private static final String GIVEN_NAME_ATTRIBUTE = "givenName";

	/**
	 * User SN LDAP attribute.
	 */
	private static final String MAIL_ATTRIBUTE = "mail";

	/**
	 * PPolicy module identifier.
	 */
	private static final String PPOLICY_NAME = "_ppolicy";

	/**
	 * Flag used to hash the password or not.
	 */
	@Setter
	@Getter
	private boolean clearPassword = false;

	/**
	 * LDAP class filter.
	 */
	public static final String OBJECT_CLASS = "objectClass";

	private static final Map<String, Comparator<UserOrg>> COMPARATORS = new HashMap<>();

	/**
	 * User comparator for ordering
	 */
	public static final Comparator<UserOrg> DEFAULT_COMPARATOR = new LoginComparator();
	private static final Sort.Order DEFAULT_ORDER = new Sort.Order(Direction.ASC, "id");

	/**
	 * Shared random string generator used for temporary passwords.
	 */
	public static final RandomStringGenerator GENERATOR = new RandomStringGenerator.Builder()
			.filteredBy(c -> CharUtils.isAsciiAlphanumeric(Character.toChars(c)[0])).build();

	@Setter
	@Getter
	private LdapTemplate template;

	/**
	 * UID attribute name.
	 */
	@Setter
	private String uidAttribute = "sAMAccountName";

	/**
	 * Employee number attribute
	 */
	@Setter
	private String departmentAttribute = "employeeNumber";

	/**
	 * Local UID attribute name.
	 */
	@Setter
	private String localIdAttribute = "employeeID";

	/**
	 * Base DN for internal people. Should be a subset of people, so including {@link #peopleBaseDn}
	 */
	@Setter
	@Getter
	private String peopleInternalBaseDn;

	/**
	 * Object class of people
	 */
	@Setter
	private String peopleClass = "inetOrgPerson";

	/**
	 * Base DN for people.
	 */
	@Setter
	private String peopleBaseDn;

	/**
	 * Compiled pattern capturing the company from the DN of the user. May be a row string for constant.
	 */
	private Pattern companyPattern = Pattern.compile("");

	/**
	 * Special company that will contains the isolated accounts.
	 */
	@Setter
	private String quarantineBaseDn;

	/**
	 * LDAP Attribute used to tag a locked user. This attribute will contains several serialized values such as
	 * #lockedValue, author, date and previous company when this user is in the isolate state.<br>
	 * The structure of this attribute is composed by several fragments with pipe "|" as separator. The whole structure
	 * is : <code>FLAG|locked date as milliseconds|author|[optional old company for restore]</code>.
	 * 
	 * @see #lockedValue
	 */
	@Setter
	private String lockedAttribute;

	/**
	 * LDAP Attribute value to tag a disabled user.
	 * 
	 * @see #lockedAttribute
	 */
	@Setter
	private String lockedValue;

	@Autowired
	private InMemoryPagination inMemoryPagination;

	@Setter
	private GroupLdapRepository groupLdapRepository;

	@Getter
	@Setter
	private CompanyLdapRepository companyRepository;

	@Autowired
	private LdapCacheRepository ldapCacheRepository;

	@Autowired
	protected ApplicationContext applicationContext;

	/**
	 * LDAP Mapper
	 */
	private final Mapper mapper = new Mapper();

	static {
		COMPARATORS.put("company", new CompanyComparator());
		COMPARATORS.put("id", new LoginComparator());
		COMPARATORS.put("firstName", new FirstNameComparator());
		COMPARATORS.put("lastName", new LastNameComparator());
		COMPARATORS.put(MAIL_ATTRIBUTE, new MailComparator());
	}

	@Override
	public UserOrg create(final UserOrg user) {
		// Build the DN
		final Name dn = buildDn(user);

		// Create the LDAP entry
		user.setDn(dn.toString());
		final DirContextAdapter context = new DirContextAdapter(dn);
		context.setAttributeValues(OBJECT_CLASS, new String[] { peopleClass });
		mapToContext(user, context);
		template.bind(context);

		// Also, update the cache
		ldapCacheRepository.create(user);

		// Return the original entry with updated DN
		return user;
	}

	/**
	 * Replace a value by another one without touching other values.
	 * 
	 * @param dn
	 *            the DN of entry.
	 * @param attribute
	 *            The attribute name, single value.
	 * @param value
	 *            the new value.
	 */
	public void set(final Name dn, final String attribute, final String value) {
		final ModificationItem[] mods = new ModificationItem[1];
		mods[0] = new ModificationItem(DirContext.REPLACE_ATTRIBUTE, new BasicAttribute(attribute, value));
		template.modifyAttributes(org.springframework.ldap.support.LdapUtils.newLdapName(dn), mods);
	}

	/**
	 * Replace a value by another one without touching other values.
	 * 
	 * @param user
	 *            The entry to update.
	 * @param attribute
	 *            The attribute name, single value.
	 * @param value
	 *            the new value.
	 */
	public void set(final UserOrg user, final String attribute, final String value) {
		set(org.springframework.ldap.support.LdapUtils.newLdapName(user.getDn()), attribute, value);
	}

	@Override
	public UserOrg findByIdNoCache(final String login) {
		return findOneBy(uidAttribute, login);
	}

	@Override
	public List<UserOrg> findAllBy(final String attribute, final String value) {
		final AndFilter filter = new AndFilter().and(new EqualsFilter(OBJECT_CLASS, peopleClass))
				.and(new EqualsFilter(attribute, value));
		return template.search(peopleBaseDn, filter.encode(), mapper).stream()
				.map(u -> Optional.ofNullable(findById(u.getId())).orElse(u)).collect(Collectors.toList());
	}

	@Override
	@SuppressWarnings("unchecked")
	public Map<String, UserOrg> findAll() {
		return (Map<String, UserOrg>) ldapCacheRepository.getLdapData().get(LdapData.USER);
	}

	/**
	 * Return all user entries.
	 * 
	 * @param groups
	 *            The existing groups. They will be be used to complete the membership of each returned user.
	 * @return all user entries. Key is the user login.
	 */
	public Map<String, UserOrg> findAllNoCache(final Map<String, GroupOrg> groups) {

		// List of attributes to retrieve from LDAP.
		final String[] returnAttrs = new String[] { SN_ATTRIBUTE, GIVEN_NAME_ATTRIBUTE, PASSWORD_ATTRIBUTE,
				MAIL_ATTRIBUTE, uidAttribute, departmentAttribute, localIdAttribute, lockedAttribute,
				PWD_ACCOUNT_LOCKED_ATTRIBUTE };

		// Fetch users and their direct attributes
		final List<UserOrg> users = template.search(peopleBaseDn, new EqualsFilter(OBJECT_CLASS, peopleClass).encode(),
				SearchControls.SUBTREE_SCOPE, returnAttrs, mapper);

		// INdex the users by the identifier
		final Map<String, UserOrg> result = new HashMap<>();
		for (final UserOrg user : users) {
			user.setGroups(new ArrayList<>());
			result.put(user.getId(), user);
		}

		// Update the memberships of this user
		for (final Entry<String, GroupOrg> groupEntry : groups.entrySet()) {
			updateMembership(result, groupEntry);
		}
		return result;
	}

	/**
	 * Update the membership of given group. All users are checked.
	 */
	private void updateMembership(final Map<String, UserOrg> result, final Entry<String, GroupOrg> groupEntry) {
		final GroupOrg groupLdap = groupEntry.getValue();
		final String group = groupLdap.getId();
		new ArrayList<>(groupLdap.getMembers()).forEach(dn -> {
			// Extract the UID
			final String uid = DnUtils.toRdn(dn);

			// Remove this DN from the members, it would be replaced by the RDN form
			groupLdap.getMembers().remove(dn);

			// Check the broken UID reference
			final UserOrg user = result.get(uid);
			if (user == null) {
				if (!dn.startsWith(GroupLdapRepository.DEFAULT_MEMBER_DN)) {
					// It is a real broken reference
					log.warn("Broken user UID reference found '{}' --> {}", groupLdap.getDn(), uid);
				}
			} else {
				if (!Normalizer.normalize(dn).equals(Normalizer.normalize(user.getDn()))) {
					log.warn("Broken user DN reference found '{}' --> {}, instead of {}", groupLdap.getDn(), dn,
							user.getDn());
				}
				user.getGroups().add(group);

				// Finally, add the RDN (UID) of this user to replace the
				groupLdap.getMembers().add(uid);
			}
		});
	}

	@Override
	public String toDn(UserOrg newUser) {
		return buildDn(newUser).toString();
	}

	/**
	 * Return DN from entry.
	 * 
	 * @param entry
	 *            LDAP entry to convert to DN.
	 * @return DN from entry.
	 */
	public Name buildDn(final UserOrg entry) {
		return org.springframework.ldap.support.LdapUtils
				.newLdapName(buildDn(entry.getId(), companyRepository.findById(entry.getCompany()).getDn()));
	}

	/**
	 * Return DN from entry.
	 * 
	 * @param login
	 *            The user login to create.
	 * @param companyDn
	 *            The target company DN.
	 * @return DN from entry.
	 */
	private String buildDn(final String login, final String companyDn) {
		return "uid=" + login + "," + companyDn;
	}

	protected void mapToContext(final UserOrg entry, final DirContextOperations context) {
		context.setAttributeValue("cn", entry.getFirstName() + " " + entry.getLastName());
		context.setAttributeValue(SN_ATTRIBUTE, entry.getLastName());
		context.setAttributeValue(GIVEN_NAME_ATTRIBUTE, entry.getFirstName());
		context.setAttributeValue(uidAttribute, Normalizer.normalize(entry.getId()));
		context.setAttributeValues(MAIL_ATTRIBUTE, entry.getMails().toArray(), true);

		// Special and also optional attributes
		Optional.ofNullable(departmentAttribute).ifPresent(a -> context.setAttributeValue(a, entry.getDepartment()));
		Optional.ofNullable(localIdAttribute).ifPresent(a -> context.setAttributeValue(a, entry.getLocalId()));
	}

	private class Mapper extends AbstractContextMapper<UserOrg> {

		@Override
		public UserOrg doMapFromContext(final DirContextOperations context) {
			final UserOrg user = new UserOrg();
			user.setDn(context.getDn().toString());
			user.setLastName(context.getStringAttribute(SN_ATTRIBUTE));
			user.setFirstName(context.getStringAttribute(GIVEN_NAME_ATTRIBUTE));
			user.setSecured(context.getObjectAttribute(PASSWORD_ATTRIBUTE) != null);
			user.setId(Normalizer.normalize(context.getStringAttribute(uidAttribute)));

			// Special and also optional attributes
			Optional.ofNullable(departmentAttribute).ifPresent(a -> user.setDepartment(context.getStringAttribute(a)));
			Optional.ofNullable(localIdAttribute).ifPresent(a -> user.setLocalId(context.getStringAttribute(a)));
			Optional.ofNullable(lockedAttribute).ifPresent(a -> fillLockedData(user, context.getStringAttribute(a)));

			// Save the normalized CN of the company
			user.setCompany(toCompany(user.getDn()));

			if (context.attributeExists(PWD_ACCOUNT_LOCKED_ATTRIBUTE)) {
				user.setLockedBy(PPOLICY_NAME);
				user.setLocked(parseLdapDate(context.getStringAttribute(PWD_ACCOUNT_LOCKED_ATTRIBUTE)));
			}

			// Save the mails
			user.setMails(
					new ArrayList<>(CollectionUtils.emptyIfNull(context.getAttributeSortedStringSet(MAIL_ATTRIBUTE))));
			return user;
		}

		/**
		 * Extract the {@link Date}, author, and the previous company from the locked attribute if available and matched
		 * to the expected {@link UserLdapRepository#lockedValue}
		 * 
		 * @param user
		 *            The user to update.
		 * @param lockedValue
		 *            The locked value flag. May be <code>null</code>.
		 */
		private void fillLockedData(final SimpleUserOrg user, final String lockedValue) {
			if (StringUtils.startsWith(lockedValue, UserLdapRepository.this.lockedValue)) {
				// A locked account
				final String[] fragments = StringUtils.splitPreserveAllTokens(lockedValue, '|');
				user.setLocked(new Date(Long.parseLong(fragments[1])));
				user.setLockedBy(fragments[2]);
				user.setIsolated(StringUtils.defaultIfEmpty(fragments[3], null));
			}
		}
	}

	/**
	 * Extract the company from the DN of this user.
	 * 
	 * @param dn
	 *            The user DN.
	 * @return The company identifier from the DN of the user.
	 */
	protected String toCompany(final String dn) {
		final Matcher matcher = companyPattern.matcher(Normalizer.normalize(dn));
		if (matcher.matches()) {
			if (matcher.groupCount() > 0) {
				return Normalizer.normalize(matcher.group(1));
			}
			// Pattern match, but there is no capturing group
			return null;
		}

		// No matches
		if (matcher.groupCount() > 0) {
			// There is a capturing group but did not succeed
			return null;
		}
		// Constant form
		return Normalizer.normalize(companyPattern.pattern());
	}

	@Override
	public Page<UserOrg> findAll(final Collection<GroupOrg> requiredGroups, final Set<String> companies,
			final String criteria, final Pageable pageable) {
		// Create the set with the right comparator
		final List<Sort.Order> orders = IteratorUtils
				.toList(ObjectUtils.defaultIfNull(pageable.getSort(), new ArrayList<Sort.Order>()).iterator());
		orders.add(DEFAULT_ORDER);
		final Sort.Order order = orders.get(0);
		Comparator<UserOrg> comparator = ObjectUtils.defaultIfNull(COMPARATORS.get(order.getProperty()),
				DEFAULT_COMPARATOR);
		if (order.getDirection() == Direction.DESC) {
			comparator = Collections.reverseOrder(comparator);
		}
		final Set<UserOrg> result = new TreeSet<>(comparator);

		// Filter the users traversing firstly the required groups and their members,
		// the companies, then the criteria
		final Map<String, UserOrg> users = findAll();
		if (requiredGroups == null) {
			// No constraint on group
			addFilteredByCompaniesAndPattern(users.keySet(), companies, criteria, result, users);
		} else {
			// User must be within one the given groups
			for (final GroupOrg requiredGroup : requiredGroups) {
				addFilteredByCompaniesAndPattern(requiredGroup.getMembers(), companies, criteria, result, users);
			}
		}

		// Apply in-memory pagination
		return inMemoryPagination.newPage(result, pageable);
	}

	/**
	 * Add the members to the result if they match to the required company and the pattern.
	 */
	private void addFilteredByCompaniesAndPattern(final Set<String> members, final Set<String> companies,
			final String criteria, final Set<UserOrg> result, final Map<String, UserOrg> users) {
		// Filter by company for each members
		for (final String member : members) {
			final UserOrg userLdap = users.get(member);

			// User is always found since #findAll() ensure the members of the groups exist
			addFilteredByCompaniesAndPattern(companies, criteria, result, userLdap);
		}

	}

	private void addFilteredByCompaniesAndPattern(final Set<String> companies, final String criteria,
			final Set<UserOrg> result, final UserOrg userLdap) {
		final List<CompanyOrg> userCompanies = companyRepository.findAll().get(userLdap.getCompany()).getCompanyTree();
		if (userCompanies.stream().map(CompanyOrg::getId).anyMatch(companies::contains)) {
			addFilteredByPattern(criteria, result, userLdap);
		}
	}

	private void addFilteredByPattern(final String criteria, final Set<UserOrg> result, final UserOrg userLdap) {
		if (criteria == null || matchPattern(userLdap, criteria)) {
			// Company and pattern match
			result.add(userLdap);
		}
	}

	/**
	 * Indicates the given user match to the given pattern.
	 */
	private boolean matchPattern(final UserOrg userLdap, final String criteria) {
		return StringUtils.containsIgnoreCase(userLdap.getFirstName(), criteria)
				|| StringUtils.containsIgnoreCase(userLdap.getLastName(), criteria)
				|| StringUtils.containsIgnoreCase(userLdap.getId(), criteria) || !userLdap.getMails().isEmpty()
						&& StringUtils.containsIgnoreCase(userLdap.getMails().get(0), criteria);
	}

	@Override
	public void updateMembership(final Collection<String> groups, final UserOrg user) {
		// Add new groups
		addUserToGroups(user, CollectionUtils.subtract(groups, user.getGroups()));

		// Remove old groups
		removeUserFromGroups(user, CollectionUtils.subtract(user.getGroups(), groups));
	}

	/**
	 * Add the user from the given groups. Cache is also updated.
	 * 
	 * @param user
	 *            The user to add to the given groups.
	 * @param groups
	 *            the groups to add, normalized.
	 */
	protected void addUserToGroups(final UserOrg user, final Collection<String> groups) {
		groups.forEach(g -> groupLdapRepository.addUser(user, g));
	}

	/**
	 * Remove the user from the given groups.Cache is also updated.
	 * 
	 * @param user
	 *            The user to remove from the given groups.
	 * @param groups
	 *            the groups to remove, normalized.
	 */
	protected void removeUserFromGroups(final UserOrg user, final Collection<String> groups) {
		groups.forEach(g -> groupLdapRepository.removeUser(user, g));
	}

	@Override
	public void updateUser(final UserOrg user) {
		final DirContextOperations context = template
				.lookupContext(org.springframework.ldap.support.LdapUtils.newLdapName(user.getDn()));
		mapToContext(user, context);
		template.modifyAttributes(context);

		// Also, update the cache
		final UserOrg userLdap = findById(user.getId());
		user.copy((SimpleUser) userLdap);
		userLdap.setMails(user.getMails());

		ldapCacheRepository.update(user);
	}

	@Override
	public void delete(final UserOrg user) {
		final Name userDn = org.springframework.ldap.support.LdapUtils.newLdapName(user.getDn());

		// Delete the user from LDAP
		template.unbind(userDn);

		// Remove user from all groups
		removeUserFromGroups(user, user.getGroups());

		// Remove the user from the cache
		ldapCacheRepository.delete(user);
	}

	@Override
	public void lock(final String principal, final UserOrg user) {
		lock(principal, user, false);
	}

	@Override
	public void isolate(final String principal, final UserOrg user) {
		if (user.getIsolated() == null) {
			// Not yet isolated
			lock(principal, user, true);
			final String previousCompany = user.getCompany();
			move(user, companyRepository.findById(companyRepository.getQuarantineCompany()));
			user.setIsolated(previousCompany);
		}
	}

	@Override
	public void restore(final UserOrg user) {
		if (user.getIsolated() != null) {
			move(user, companyRepository.findById(user.getIsolated()));
			user.setIsolated(null);
			unlock(user);
		}
	}

	@Override
	public void move(final UserOrg user, final CompanyOrg company) {
		final LdapName newDn = org.springframework.ldap.support.LdapUtils
				.newLdapName(buildDn(user.getId(), company.getDn()));
		final LdapName oldDn = org.springframework.ldap.support.LdapUtils.newLdapName(user.getDn());
		template.rename(oldDn, newDn);
		user.setDn(newDn.toString());
		user.setCompany(company.getId());
		ldapCacheRepository.update(user);

		// Also, update the groups of this user
		user.getGroups().forEach(g -> groupLdapRepository.updateMemberDn(g, oldDn.toString(), newDn.toString()));
	}

	/**
	 * Lock an user :
	 * <ul>
	 * <li>Clear the password to prevent new authentication</li>
	 * <li>Set the disabled flag.</li>
	 * </ul>
	 * 
	 * @param principal
	 *            Principal user requesting the lock.
	 * @param user
	 *            The LDAP user to disable.
	 * @param isolate
	 *            When <code>true</code>, the user will be isolated in addition.
	 */
	private void lock(final String principal, final UserOrg user, final boolean isolate) {
		if (user.getLockedBy() == null) {
			// Not yet locked
			final ModificationItem[] mods = new ModificationItem[2];
			final long timeInMillis = DateUtils.newCalendar().getTimeInMillis();
			mods[0] = new ModificationItem(DirContext.REPLACE_ATTRIBUTE, new BasicAttribute(lockedAttribute, String
					.format("%s|%s|%s|%s|", lockedValue, timeInMillis, principal, isolate ? user.getCompany() : "")));
			mods[1] = new ModificationItem(DirContext.REPLACE_ATTRIBUTE, new BasicAttribute(PASSWORD_ATTRIBUTE, null));
			template.modifyAttributes(org.springframework.ldap.support.LdapUtils.newLdapName(user.getDn()), mods);

			// Also update the disabled date
			user.setLocked(new Date(timeInMillis));
			user.setLockedBy(principal);
		}
	}

	@Override
	public void unlock(final UserOrg user) {
		if (user.getIsolated() == null && user.getLockedBy() != null) {

			// remove locked attribute when exists
			set(user, lockedAttribute, null);

			// remove ppolicy pwdAccountLockedTime attribute when exists
			set(user, PWD_ACCOUNT_LOCKED_ATTRIBUTE, null);

			// Also clear the disabled state from cache
			user.setLocked(null);
			user.setLockedBy(null);
		}
	}

	@Override
	public boolean authenticate(final String name, final String password) {
		log.info("Authenticating {} ...", name);
		final String property = getAuthenticateProperty(name);
		final AndFilter filter = new AndFilter().and(new EqualsFilter("objectclass", peopleClass))
				.and(new EqualsFilter(property, name));
		final boolean result = template.authenticate(peopleBaseDn, filter.encode(), password);
		log.info("Authenticate {} : {}", name, result);
		return result;
	}

	/**
	 * Return the property name used to match the user name.
	 * 
	 * @param name
	 *            The current principal.
	 * @return the property name used to match the user name.
	 */
	public String getAuthenticateProperty(final String name) {
		return new EmailValidator().isValid(name, null) ? MAIL_ATTRIBUTE : uidAttribute;
	}

	@Override
	public String getToken(final String login) {
		final AndFilter filter = new AndFilter().and(new EqualsFilter(OBJECT_CLASS, peopleClass))
				.and(new EqualsFilter(uidAttribute, login));
		return template.search(peopleBaseDn, filter.encode(), new AbstractContextMapper<String>() {
			@Override
			public String doMapFromContext(final DirContextOperations context) {
				// Get the password
				return new String(
						ObjectUtils.defaultIfNull((byte[]) context.getObjectAttribute(PASSWORD_ATTRIBUTE), new byte[0]),
						StandardCharsets.UTF_8);
			}
		}).stream().findFirst().orElse(null);
	}

	/**
	 * Validate and set the company pattern.
	 * 
	 * @param companyPattern
	 *            Pattern capturing the company from the DN of the user. May be a row string for constant.
	 */
	public void setCompanyPattern(final String companyPattern) {
		this.companyPattern = Pattern.compile(companyPattern);
	}

	/**
	 * Digest with SSHA the given clear password.
	 * 
	 * @param password
	 *            the clear password to digest.
	 * @return a SSHA digest.
	 */
	@SuppressWarnings("deprecation")
	private String digest(final String password) {
		return isClearPassword() ? password
				: new org.springframework.security.crypto.password.LdapShaPasswordEncoder().encode(password);
	}

	@Override
	public void setPassword(final UserOrg userLdap, final String password) {
		set(userLdap, PASSWORD_ATTRIBUTE, digest(password));
	}

	@Override
	public void setPassword(final UserOrg userLdap, final String password, final String newPassword) {
		log.info("Changing password for {} ...", userLdap.getId());
		final ModificationItem[] passwordChange = { new ModificationItem(DirContext.REPLACE_ATTRIBUTE,
				new BasicAttribute(PASSWORD_ATTRIBUTE, digest(newPassword))) };

		// Unlock account when the user is locked by ppolicy
		set(userLdap, PWD_ACCOUNT_LOCKED_ATTRIBUTE, null);

		// Authenticate the user is needed before changing the password.
		template.executeReadWrite(new ContextExecutor<Object>() {
			@Override
			public Object executeWithContext(final DirContext dirCtx) throws NamingException {
				LdapContext ctx = (LdapContext) dirCtx;
				ctx.removeFromEnvironment(LDAP_CONNECT_POOL);
				ctx.addToEnvironment(Context.SECURITY_PRINCIPAL, userLdap.getDn());
				ctx.addToEnvironment(Context.SECURITY_CREDENTIALS,
						password == null ? getTmpPassword(userLdap) : password);

				try {
					ctx.reconnect(null);
					ctx.modifyAttributes(userLdap.getDn(), passwordChange);
				} catch (@SuppressWarnings("unused") final AuthenticationException e) {
					log.info("Authentication failed for {} ...", userLdap.getId());
					throw new ValidationJsonException("password", "login");
				} catch (final InvalidAttributeValueException e) {
					log.info("Password change failed due to: {}", e.getMessage());
					throw new ValidationJsonException("password", "password-policy");
				}
				return null;
			}
		});
	}

	/**
	 * Generate and set a temporary password to specified user.
	 * 
	 * @param user
	 *            User to update.
	 * @return current user password.
	 */
	private String getTmpPassword(final UserOrg user) {
		final String tmpPassword = GENERATOR.generate(10);
		// set the new generated password
		setPassword(user, tmpPassword);
		return tmpPassword;
	}

	/**
	 * Normalize OpenLdap date format.
	 * 
	 * @param utc
	 *            OpenLdap date format.
	 * @return normalized date.
	 */
	public Date parseLdapDate(final String utc) {
		Date date = null;
		// setup x.208 generalized time formatter
		final DateFormat formatter = new SimpleDateFormat(OPEN_LDAP_DATE_FORMAT);
		try {
			// parse utc into Date
			date = formatter.parse(utc);
		} catch (@SuppressWarnings("unused") java.text.ParseException e) {
			log.info("Error while parsing date {}", utc);
			throw new BusinessException(BusinessException.KEY_UNKNOW_ID);
		}
		return date;
	}

	@Override
	public void checkLockStatus(final UserOrg user) {
		// List of attributes to retrieve from LDAP.
		final String[] returnAttrs = new String[] { PWD_ACCOUNT_LOCKED_ATTRIBUTE };

		final AndFilter filter = new AndFilter().and(new EqualsFilter(OBJECT_CLASS, peopleClass))
				.and(new EqualsFilter(uidAttribute, user.getId()));
		template.search(peopleBaseDn, filter.encode(), SearchControls.SUBTREE_SCOPE, returnAttrs,
				new AbstractContextMapper<UserOrg>() {
					@Override
					public UserOrg doMapFromContext(final DirContextOperations context) {
						// Get the pwdAccountLockedTime ppolicy attribute when exists
						if (context.attributeExists(PWD_ACCOUNT_LOCKED_ATTRIBUTE)) {
							user.setLockedBy(PPOLICY_NAME);
							user.setLocked(parseLdapDate(context.getStringAttribute(PWD_ACCOUNT_LOCKED_ATTRIBUTE)));
						}
						return user;
					}
				});
	}

}
