/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.id.ldap.resource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import javax.transaction.Transactional;
import javax.transaction.Transactional.TxType;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.lang3.StringUtils;
import org.ligoj.app.api.Normalizer;
import org.ligoj.app.api.ServicePlugin;
import org.ligoj.app.api.SubscriptionStatusWithData;
import org.ligoj.app.iam.Activity;
import org.ligoj.app.iam.IamConfiguration;
import org.ligoj.app.iam.IamProvider;
import org.ligoj.app.iam.UserOrg;
import org.ligoj.app.model.CacheProjectGroup;
import org.ligoj.app.model.ContainerType;
import org.ligoj.app.model.Node;
import org.ligoj.app.model.Subscription;
import org.ligoj.app.plugin.id.dao.CacheProjectGroupRepository;
import org.ligoj.app.plugin.id.ldap.dao.CompanyLdapRepository;
import org.ligoj.app.plugin.id.ldap.dao.GroupLdapRepository;
import org.ligoj.app.plugin.id.ldap.dao.ProjectCustomerLdapRepository;
import org.ligoj.app.plugin.id.ldap.dao.UserLdapRepository;
import org.ligoj.app.plugin.id.model.ContainerScope;
import org.ligoj.app.plugin.id.resource.AbstractPluginIdResource;
import org.ligoj.app.plugin.id.resource.CompanyResource;
import org.ligoj.app.plugin.id.resource.ContainerScopeResource;
import org.ligoj.app.plugin.id.resource.GroupResource;
import org.ligoj.app.plugin.id.resource.IdentityResource;
import org.ligoj.app.resource.ActivitiesProvider;
import org.ligoj.app.resource.ServicePluginLocator;
import org.ligoj.bootstrap.core.INamableBean;
import org.ligoj.bootstrap.core.NamedBean;
import org.ligoj.bootstrap.core.SpringUtils;
import org.ligoj.bootstrap.core.resource.BusinessException;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.LdapContextSource;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * LDAP resource.
 */
@Path(LdapPluginResource.URL)
@Service
@Transactional
@Produces(MediaType.APPLICATION_JSON)
@Slf4j
public class LdapPluginResource extends AbstractPluginIdResource<UserLdapRepository> {

	private static final String PATTERN_PROPERTY = "pattern";

	private static final String LDAP_VERSION = "3";

	/**
	 * Plug-in key.
	 */
	public static final String URL = IdentityResource.SERVICE_URL + "/ldap";

	/**
	 * Plug-in key.
	 */
	public static final String KEY = URL.replace('/', ':').substring(1);

	/**
	 * Full URL like "ldap/myhost:389/"
	 */
	public static final String PARAMETER_URL = KEY + ":url";

	/**
	 * DN of administrative user that can fetch the repository
	 */
	public static final String PARAMETER_USER = KEY + ":user-dn";

	/**
	 * Referral option as "follow"
	 */
	public static final String PARAMETER_REFERRAL = KEY + ":referral";

	/**
	 * Password of administrative user
	 */
	public static final String PARAMETER_PASSWORD = KEY + ":password";

	/**
	 * Base DN where people, groups and companies are located
	 */
	public static final String PARAMETER_BASE_BN = KEY + ":base-dn";

	/**
	 * LDAP schema attribute name of login.
	 */
	public static final String PARAMETER_UID_ATTRIBUTE = KEY + ":uid-attribute";

	/**
	 * DN of location of users can login
	 */
	public static final String PARAMETER_PEOPLE_DN = KEY + ":people-dn";

	/**
	 * LDAP schema attribute name of department.
	 */
	public static final String PARAMETER_DEPARTMENT_ATTRIBUTE = KEY + ":department-attribute";
	/**
	 * LDAP schema attribute name of internal id of login. May not be unique.
	 */
	public static final String PARAMETER_LOCAL_ID_ATTRIBUTE = KEY + ":local-id-attribute";

	/**
	 * DN of location where isolated users are moved to.
	 */
	public static final String PARAMETER_QUARANTINE_DN = KEY + ":quarantine-dn";

	/**
	 * LDAP schema attribute holding the locked state of a user.
	 */
	public static final String PARAMETER_LOCKED_ATTRIBUTE = KEY + ":locked-attribute";

	/**
	 * Value used as flag for a locked user inside the locked attribute
	 */
	public static final String PARAMETER_LOCKED_VALUE = KEY + ":locked-value";

	/**
	 * Object Class of people : organizationalPerson, inetOrgPerson
	 */
	public static final String PARAMETER_PEOPLE_CLASS = KEY + ":people-class";

	/**
	 * Pattern capturing the company from the DN of the user. May be a row string for constant.
	 */
	public static final String PARAMETER_COMPANY_PATTERN = KEY + ":company-pattern";

	/**
	 * DN of location of groups
	 */
	public static final String PARAMETER_GROUPS_DN = KEY + ":groups-dn";

	/**
	 * DN of location of companies
	 */
	public static final String PARAMETER_COMPANIES_DN = KEY + ":companies-dn";

	/**
	 * DN of location of people considered as internal. May be the same than people
	 */
	public static final String PARAMETER_PEOPLE_INTERNAL_DN = KEY + ":people-internal-dn";

	/**
	 * Value used as flag to hash or not the password
	 */
	public static final String PARAMETER_CLEAR_PASSWORD = KEY + ":clear-password";

	@Autowired
	protected ProjectCustomerLdapRepository projectCustomerLdapRepository;

	@Autowired
	protected CompanyResource companyResource;

	@Autowired
	protected GroupResource groupLdapResource;

	@Autowired
	private ContainerScopeResource containerScopeResource;

	@Autowired
	private CacheProjectGroupRepository cacheProjectGroupRepository;

	@Autowired
	private IamProvider[] iamProvider;

	@Autowired
	protected ServicePluginLocator servicePluginLocator;

	@Autowired
	@Getter
	protected LdapPluginResource self;

	@Override
	protected UserLdapRepository getUserRepository(final String node) {
		log.info("Build ldap template for node {}", node);
		final var parameters = pvResource.getNodeParameters(node);
		final var contextSource = new LdapContextSource();
		contextSource.setReferral(parameters.get(PARAMETER_REFERRAL));
		contextSource.setPassword(parameters.get(PARAMETER_PASSWORD));
		contextSource.setUrl(parameters.get(PARAMETER_URL));
		contextSource.setUserDn(parameters.get(PARAMETER_USER));
		contextSource.setBase(parameters.get(PARAMETER_BASE_BN));
		contextSource.afterPropertiesSet();
		final var template = new LdapTemplate();
		template.setContextSource(contextSource);
		template.setIgnorePartialResultException(true);

		// A new repository instance
		final var repository = new UserLdapRepository();
		repository.setTemplate(template);
		repository.setPeopleBaseDn(StringUtils.trimToEmpty(parameters.get(PARAMETER_PEOPLE_DN)));
		repository.setPeopleInternalBaseDn(parameters.get(PARAMETER_PEOPLE_INTERNAL_DN));
		repository.setQuarantineBaseDn(StringUtils.trimToEmpty(parameters.get(PARAMETER_QUARANTINE_DN)));
		repository.setDepartmentAttribute(parameters.get(PARAMETER_DEPARTMENT_ATTRIBUTE));
		repository.setLocalIdAttribute(parameters.get(PARAMETER_LOCAL_ID_ATTRIBUTE));
		repository.setUidAttribute(parameters.get(PARAMETER_UID_ATTRIBUTE));
		repository.setLockedAttribute(parameters.get(PARAMETER_LOCKED_ATTRIBUTE));
		repository.setLockedValue(parameters.get(PARAMETER_LOCKED_VALUE));
		repository.setPeopleClass(parameters.get(PARAMETER_PEOPLE_CLASS));
		repository.setCompanyPattern(StringUtils.trimToEmpty(parameters.get(PARAMETER_COMPANY_PATTERN)));
		repository.setClearPassword(Boolean.parseBoolean(parameters.get(PARAMETER_CLEAR_PASSWORD)));

		// Complete the bean
		SpringUtils.getApplicationContext().getAutowireCapableBeanFactory().autowireBean(repository);

		return repository;
	}

	/**
	 * Build a group LDAP repository from the given node.
	 *
	 * @param node     The node, also used as cache key.
	 * @param template The {@link LdapTemplate} used to query the repository.
	 * @return The {@link UserLdapRepository} instance. Cache is involved.
	 */
	public GroupLdapRepository newGroupLdapRepository(final String node, final LdapTemplate template) {
		final var parameters = pvResource.getNodeParameters(node);

		// A new repository instance
		final var repository = new GroupLdapRepository();
		repository.setTemplate(template);
		repository.setGroupsBaseDn(StringUtils.trimToEmpty(parameters.get(PARAMETER_GROUPS_DN)));

		// Complete the bean
		SpringUtils.getApplicationContext().getAutowireCapableBeanFactory().autowireBean(repository);
		return repository;
	}

	/**
	 * Build a group LDAP repository from the given node.
	 *
	 * @param node     The node, also used as cache key.
	 * @param template The {@link LdapTemplate} used to query the repository.
	 * @return The {@link UserLdapRepository} instance. Cache is involved.
	 */
	public CompanyLdapRepository newCompanyLdapRepository(final String node, final LdapTemplate template) {
		final var parameters = pvResource.getNodeParameters(node);

		// A new repository instance
		final var repository = new CompanyLdapRepository();
		repository.setTemplate(template);
		repository.setCompanyBaseDn(parameters.get(PARAMETER_COMPANIES_DN));
		repository.setQuarantineBaseDn(parameters.get(PARAMETER_QUARANTINE_DN));

		// Complete the bean
		SpringUtils.getApplicationContext().getAutowireCapableBeanFactory().autowireBean(repository);
		return repository;
	}

	@Override
	public boolean accept(final Authentication authentication, final String node) {
		final var parameters = pvResource.getNodeParameters(node);
		return !parameters.isEmpty() && authentication.getName()
				.matches(StringUtils.defaultString(parameters.get(IdentityResource.PARAMETER_UID_PATTERN), ".*"));
	}

	@Override
	public void create(final int subscription) {
		final var parameters = subscriptionResource.getParameters(subscription);
		final var group = parameters.get(IdentityResource.PARAMETER_GROUP);
		final var parentGroup = parameters.get(IdentityResource.PARAMETER_PARENT_GROUP);
		final var ou = parameters.get(IdentityResource.PARAMETER_OU);
		final var project = subscriptionRepository.findOne(subscription).getProject();
		final var pkey = project.getPkey();

		// Check the relationship between group, OU and project
		validateGroup(group, ou, pkey);

		// Check the relationship between group, and parent
		final var parentDn = validateAndCreateParent(group, parentGroup, ou, pkey);

		// Create the group inside the parent (OU or parent CN)
		final var groupDn = "cn=" + group + "," + parentDn;
		log.info("New Group CN would be created {} project {} and subscription {}", group, pkey);
		final var repository = getGroup();
		final var groupLdap = repository.create(groupDn, group);

		// Complete as needed the relationship between parent and this new group
		if (StringUtils.isNotBlank(parentGroup)) {
			// This group will be added as "uniqueMember" of its parent
			repository.addGroup(groupLdap, parentGroup);
		}

		// Associate the project to this group in the cache
		final var projectGroup = new CacheProjectGroup();
		projectGroup.setProject(project);
		projectGroup.setGroup(repository.getCacheRepository().findOneExpected(groupLdap.getId()));
		cacheProjectGroupRepository.saveAndFlush(projectGroup);
	}

	private boolean startsWithAndDifferent(final String provided, final String expected) {
		return provided.startsWith(expected) && !provided.equals(expected);
	}

	/**
	 * Validate the parent and return its DN. OU must be normalized.
	 */
	private String validateAndCreateParent(final String group, final String parentGroup, final String ou,
			final String pkey) {
		// Check the creation mode
		if (StringUtils.isBlank(parentGroup)) {
			// Parent as not been defined, so will be the specified OU. that
			// would be created if it does not exist
			return validateAndCreateParentOu(group, ou, pkey);
		}

		// Parent has been specified, so will be another group we need to check
		return validateParentGroup(group, parentGroup);
	}

	/**
	 * Validate the group against its direct parent (a normalized OU) and return its DN.
	 */
	private String validateAndCreateParentOu(final String group, final String ou, final String pkey) {
		final var groupTypeLdap = containerScopeResource.findByName(ContainerScope.TYPE_PROJECT);
		final var parentDn = groupTypeLdap.getDn();

		// Build the complete normalized DN from the OU and new Group
		final var ouDn = "ou=" + ou + "," + parentDn;

		// Check the target OU exists or not and create the OU as needed
		if (projectCustomerLdapRepository.findById(parentDn, ou) == null) {
			// Create the OU in LDAP
			log.info("New OU would be created {} for group {}, project {} and subscription {}", ou, group, pkey);
			projectCustomerLdapRepository.create(parentDn, ou, ouDn);
		}

		// Parent will be an organizationalUnit (OU)
		return ouDn;
	}

	/**
	 * Validate the group against its parent and return the corresponding DN.
	 */
	private String validateParentGroup(final String group, final String parentGroup) {
		final var parentGroupLdap = groupLdapResource.findById(parentGroup);
		if (parentGroupLdap == null) {
			// The parent group does not exists
			throw new ValidationJsonException(IdentityResource.PARAMETER_PARENT_GROUP, BusinessException.KEY_UNKNOWN_ID,
					parentGroup);
		}

		// Compare the group and its parent
		if (!group.startsWith(parentGroup + "-")) {
			// This sub-group has not a correct form
			throw new ValidationJsonException(IdentityResource.PARAMETER_GROUP, PATTERN_PROPERTY, parentGroup + "-.*");
		}

		// Parent will be another group, return its DN
		return parentGroupLdap.getDn();
	}

	@Override
	public void link(final int subscription) {
		final var parameters = subscriptionResource.getParameters(subscription);

		// Validate the job settings
		validateGroup(parameters);

		// There is no additional step since the group is already created in
		// LDAP
	}

	@Override
	public String getVersion(final Map<String, String> parameters) {
		// LDAP version is fixed
		return LDAP_VERSION;
	}

	/**
	 * Return activities of all users in the group of this subscription as CSV input stream.
	 *
	 * @param subscription The subscription identifier.
	 * @param file         The target file name.
	 * @return the stream ready to be read during the serialization.
	 * @throws Exception When any technical error occurs. Caught at upper level for the right mapping.
	 */
	@GET
	@Path("activity/{subscription:\\d+}/{file:group-.*.csv}")
	@Produces(MediaType.APPLICATION_OCTET_STREAM)
	public Response getGroupActivitiesCsv(@PathParam("subscription") final int subscription,
			@PathParam("file") final String file) throws Exception {
		log.info("Group activities report requested by '{}' for subscription '{}'",
				SecurityContextHolder.getContext().getAuthentication().getName(), subscription);
		return download(new CsvStreamingOutput(getActivities(subscription, false)), file).build();
	}

	/**
	 * Return activities of all users in any group subscribed by the same project of this subscription as CSV input
	 * stream.
	 *
	 * @param subscription The subscription identifier.
	 * @param file         The target file name.
	 * @return the stream ready to be read during the serialization.
	 * @throws Exception When any technical error occurs. Caught at upper level for the right mapping.
	 */
	@GET
	@Path("activity/{subscription:\\d+}/{file:project-.*.csv}")
	@Produces(MediaType.APPLICATION_OCTET_STREAM)
	public Response getProjectActivitiesCsv(@PathParam("subscription") final int subscription,
			@PathParam("file") final String file) throws Exception {
		log.info("Project activities report requested by '{}' for subscription '{}'",
				SecurityContextHolder.getContext().getAuthentication().getName(), subscription);
		return download(new CsvStreamingOutput(getActivities(subscription, true)), file).build();
	}

	/**
	 * Return activities associates by given a subscription.
	 */
	private ActivitiesComputations getActivities(final int subscription, final boolean global) throws Exception {
		// Get users from other LDAP subscriptions
		final var main = subscriptionResource.checkVisible(subscription);
		final var subscriptions = subscriptionRepository.findAllOnSameProject(subscription);
		final var users = global ? getMembersOfAllSubscriptions(subscriptions) : getMembersOfSubscription(main);

		// Get the activities from each subscription of the same project,
		final var result = new ActivitiesComputations();
		result.setUsers(users);
		final var userLogins = users.stream().map(UserOrg::getId).collect(Collectors.toList());
		final var activities = new HashMap<String, Map<String, Activity>>();
		final var nodes = new LinkedHashSet<INamableBean<String>>();
		for (final var projectSubscription : subscriptions) {
			final var resource = servicePluginLocator.getResource(projectSubscription.getNode().getId());
			addSubscriptionActivities(activities, userLogins, projectSubscription, resource, nodes);
		}
		result.setNodes(nodes);
		result.setActivities(activities);
		return result;
	}

	/**
	 * Return members of all LDAP subscriptions
	 */
	private Set<UserOrg> getMembersOfAllSubscriptions(final Collection<Subscription> projectSubscriptions) {
		return projectSubscriptions.stream().flatMap(s -> getMembersOfSubscription(s).stream())
				.collect(Collectors.toSet());
	}

	/**
	 * Return members of given subscription.
	 */
	private Set<UserOrg> getMembersOfSubscription(final Subscription subscription) {
		final var users = new HashSet<UserOrg>();
		final var plugin = servicePluginLocator.getResource(subscription.getNode().getId());
		if (plugin instanceof LdapPluginResource) {
			users.addAll(((LdapPluginResource) plugin).getMembers(subscription.getId()));
		}
		return users;
	}

	/**
	 * Return users member of associated subscription.
	 *
	 * @param subscription The subscription identifier used to get the related group and members.
	 * @return The members of related groups of the subscription.
	 */
	public Collection<UserOrg> getMembers(final int subscription) {
		// Get current subscription parameters
		final var parameters = subscriptionResource.getParameters(subscription);
		final var group = parameters.get(IdentityResource.PARAMETER_GROUP);
		return userResource.findAllNotSecure(null, group);
	}

	/**
	 * Add activities related to given subscription.
	 *
	 * @param activities   The collected activities.
	 * @param users        The implied users.
	 * @param subscription The related subscription of theses activities.
	 * @param plugin       The plug-in associated to this subscription.
	 * @param nodes        The nodes that have already been processed. This set will be updated by this function.
	 * @throws Exception When any technical error occurs. Caught at upper level for the right mapping.
	 */
	protected void addSubscriptionActivities(final Map<String, Map<String, Activity>> activities,
			final Collection<String> users, final Subscription subscription, final ServicePlugin plugin,
			final Set<INamableBean<String>> nodes) throws Exception {

		// Collect activities of each subscription of unique node
		if (plugin instanceof ActivitiesProvider && nodes.add(subscription.getNode())) {
			final var subscriptionActivities = ((ActivitiesProvider) plugin).getActivities(subscription.getId(), users);
			for (final var userActivity : subscriptionActivities.entrySet()) {
				addUserActivities(activities, subscription.getNode(), userActivity);
			}
		}
	}

	/**
	 * Add activities related to a single node.
	 */
	private void addUserActivities(final Map<String, Map<String, Activity>> activities, final Node node,
			final Entry<String, Activity> userActivity) {
		final var user = userActivity.getKey();
		activities.computeIfAbsent(user, k -> new HashMap<>()).put(node.getId(), userActivity.getValue());
	}

	/**
	 * Validate the group settings.
	 *
	 * @param parameters the administration parameters.
	 * @return real group name.
	 */
	protected INamableBean<String> validateGroup(final Map<String, String> parameters) {
		// Get group configuration
		final var group = parameters.get(IdentityResource.PARAMETER_GROUP);
		final var groupLdap = groupLdapResource.findByName(group);

		// Check the group exists
		if (groupLdap == null) {
			throw new ValidationJsonException(IdentityResource.PARAMETER_GROUP, BusinessException.KEY_UNKNOWN_ID,
					group);
		}

		// Check the group has type TYPE_PROJECT
		if (!ContainerScope.TYPE_PROJECT.equals(groupLdap.getScope())) {
			// Invalid type
			throw new ValidationJsonException(IdentityResource.PARAMETER_GROUP, "group-type", group);
		}

		// Return the nice name
		final var result = new NamedBean<String>();
		result.setName(groupLdap.getName());
		result.setId(group);
		return result;
	}

	/**
	 * Validate the group against the OU and the linked project.
	 */
	private void validateGroup(final String group, final String ou, final String pkey) {
		// Check the group does not exists
		if (groupLdapResource.findById(group) != null) {
			// This group already exists
			throw new ValidationJsonException(IdentityResource.PARAMETER_GROUP, "already-exist", "0",
					GroupResource.GROUP_ATTRIBUTE, "1", group);
		}

		// Compare the project's key with the OU, and the name of the group

		// The group must start with the target OU
		if (!startsWithAndDifferent(group, ou + "-")) {
			// This group has not a correct form
			throw new ValidationJsonException(IdentityResource.PARAMETER_GROUP, PATTERN_PROPERTY, ou + "-.+");
		}

		// The name of the group must start with the PKEY of project
		if (!group.equals(pkey) && !startsWithAndDifferent(group, pkey + "-")) {
			// This group has not a correct form
			throw new ValidationJsonException(IdentityResource.PARAMETER_GROUP, PATTERN_PROPERTY, pkey + "(-.+)?");
		}
	}

	/**
	 * Search the LDAP Groups matching to the given criteria and for type "Project". Node identifier is ignored for now.
	 *
	 * @param criteria the search criteria.
	 * @return LDAP Groups matching the criteria.
	 * @see ContainerScope#TYPE_PROJECT
	 */
	@GET
	@Path("group/{node}/{criteria}")
	@Consumes(MediaType.APPLICATION_JSON)
	public List<INamableBean<String>> findGroupsByName(@PathParam("criteria") final String criteria) {
		final var result = new ArrayList<INamableBean<String>>();
		final var criteriaClean = Normalizer.normalize(criteria);
		final var visibleGroups = groupLdapResource.getContainers();
		final var types = containerScopeResource.findAllDescOrder(ContainerType.GROUP);
		for (final var group : visibleGroups) {
			final var scope = groupLdapResource.toScope(types, group);

			// Check type and criteria
			if (scope != null && ContainerScope.TYPE_PROJECT.equals(scope.getName())
					&& group.getId().contains(criteriaClean)) {
				// Return the nice name
				final var bean = new NamedBean<String>();
				NamedBean.copy(group, bean);
				result.add(bean);
			}
		}

		return result;
	}

	/**
	 * Search the LDAP Customers matching to the given criteria and for type "Project". Node identifier is ignored for
	 * now. Node is ignored.
	 *
	 * @param criteria the search criteria.
	 * @return LDAP Customers matching the criteria.
	 * @see ContainerScope#TYPE_PROJECT
	 */
	@GET
	@Path("customer/{node}/{criteria}")
	@Consumes(MediaType.APPLICATION_JSON)
	public Collection<INamableBean<String>> findCustomersByName(@PathParam("criteria") final String criteria) {
		final var result = new TreeSet<INamableBean<String>>();
		final var criteriaClean = Normalizer.normalize(criteria);
		final var findByName = containerScopeResource.findByName(ContainerScope.TYPE_PROJECT);
		final var allCustomers = projectCustomerLdapRepository.findAll(findByName.getDn());

		// Check type and criteria
		allCustomers.stream().filter(customer -> customer.contains(criteriaClean)).forEach(customer -> {
			// Return the nice name
			final var bean = new NamedBean<String>();
			// Return the nice name
			bean.setName(customer);
			bean.setId(customer);
			result.add(bean);
		});
		return result;
	}

	@Override
	public void delete(final int subscription, final boolean deleteRemoteData) {
		if (deleteRemoteData) {
			// Data are removed from the LDAP
			final var parameters = subscriptionResource.getParameters(subscription);
			final var group = parameters.get(IdentityResource.PARAMETER_GROUP);

			// Check the group exists, but is not required to continue the
			// process
			final var repository = getGroup();
			final var groupLdap = repository.findById(group);
			if (groupLdap != null) {
				// Perform the deletion
				repository.delete(groupLdap);
			}
		}
	}

	@Override
	@Transactional(value = TxType.NOT_SUPPORTED)
	public String getKey() {
		return KEY;
	}

	@Override
	@Transactional(value = TxType.NOT_SUPPORTED)
	public String getLastVersion() {
		return LDAP_VERSION;
	}

	@Override
	public boolean checkStatus(final String node, final Map<String, String> parameters) {
		// Query the LDAP, the user is not important, we expect no error, that's all
		self.getConfiguration(node).getUserRepository().findByIdNoCache("-any-");
		return true;
	}

	@Override
	public SubscriptionStatusWithData checkSubscriptionStatus(final Map<String, String> parameters) {
		final var groupLdap = getGroup().findById(parameters.get(IdentityResource.PARAMETER_GROUP));
		if (groupLdap == null) {
			return new SubscriptionStatusWithData(false);
		}

		// Non empty group, return amount of members
		final var result = new SubscriptionStatusWithData(true);
		result.put("members", groupLdap.getMembers().size());
		return result;
	}

	@Override
	protected void copyConfiguration(final IamConfiguration iam, final UserLdapRepository repository) {
		iam.setCompanyRepository(newCompanyLdapRepository(iam.getNode(), repository.getTemplate()));
		iam.setGroupRepository(newGroupLdapRepository(iam.getNode(), repository.getTemplate()));
		repository.setCompanyRepository((CompanyLdapRepository) iam.getCompanyRepository());
		repository.setGroupLdapRepository((GroupLdapRepository) iam.getGroupRepository());
	}

	/**
	 * Group repository provider.
	 *
	 * @return Group repository provider.
	 */
	private GroupLdapRepository getGroup() {
		return (GroupLdapRepository) iamProvider[0].getConfiguration().getGroupRepository();
	}

	@Override
	protected String getAuthenticateProperty(final UserLdapRepository repository, final Authentication authentication) {
		return repository.getAuthenticateProperty(authentication.getName());
	}
}
