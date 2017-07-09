package org.ligoj.app.plugin.id.ldap.dao;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.collections4.IteratorUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.ligoj.app.api.Normalizer;
import org.ligoj.app.iam.ContainerOrg;
import org.ligoj.app.iam.IContainerRepository;
import org.ligoj.app.iam.dao.CacheContainerRepository;
import org.ligoj.app.iam.model.CacheContainer;
import org.ligoj.app.model.ContainerType;
import org.ligoj.bootstrap.core.json.InMemoryPagination;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.ldap.core.DirContextAdapter;
import org.springframework.ldap.core.DirContextOperations;
import org.springframework.ldap.core.LdapTemplate;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * A LDAP container repository.
 * 
 * @param <T>
 *            The container type.
 * @param <C>
 *            The container cache type.
 */
@Slf4j
public abstract class AbstractContainerLdaRepository<T extends ContainerOrg, C extends CacheContainer> implements IContainerRepository<T> {

	protected static final Sort.Order DEFAULT_ORDER = new Sort.Order(Direction.ASC, "name");

	@Autowired
	protected InMemoryPagination inMemoryPagination;

	@Setter
	protected LdapTemplate template;

	@Autowired
	@Setter
	protected LdapCacheRepository ldapCacheRepository;

	/**
	 * LDAP class name of this container.
	 */
	private final String className;

	/**
	 * Human readable type name.
	 */
	@Getter
	protected final String typeName;

	/**
	 * Container type.
	 */
	private final ContainerType type;

	protected AbstractContainerLdaRepository(final ContainerType type, final String className) {
		this.type = type;
		this.className = className;
		this.typeName = this.type.name().toLowerCase(Locale.ENGLISH);
	}

	/**
	 * Return the repository managing the container as cache.
	 * 
	 * @return the repository managing the container as cache.
	 */
	protected abstract CacheContainerRepository<C> getCacheRepository();

	/**
	 * Map a container <T> to LDAP.
	 */
	protected abstract void mapToContext(T entry, DirContextOperations context);

	/**
	 * Create a new container bean. Not in LDAP repository.
	 */
	protected abstract T newContainer(String dn, String cn);

	@Override
	public T create(final String dn, final String cn) {
		final T container = newContainer(dn, cn);

		// First create the LDAP entry
		log.info("{} {} will be created as {}", type.name(), container.getName(), dn);
		final DirContextAdapter context = new DirContextAdapter(dn);
		context.setAttributeValues("objectClass", new String[] { className });
		mapToContext(container, context);
		template.bind(context);

		// Return the new container
		return container;
	}

	@Override
	public Page<T> findAll(final Set<T> groups, final String criteria, final Pageable pageable, final Map<String, Comparator<T>> customComparators) {
		// Create the set with the right comparator
		final List<Sort.Order> orders = IteratorUtils.toList(ObjectUtils.defaultIfNull(pageable.getSort(), new ArrayList<Sort.Order>()).iterator());
		orders.add(DEFAULT_ORDER);
		final Sort.Order order = orders.get(0);
		Comparator<T> comparator = customComparators.get(order.getProperty());
		if (order.getDirection() == Direction.DESC) {
			comparator = Collections.reverseOrder(comparator);
		}
		final Set<T> result = new TreeSet<>(comparator);

		// Filter the groups, filtering by the criteria
		addFilteredByPattern(groups, criteria, result);

		// Apply in-memory pagination
		return inMemoryPagination.newPage(result, pageable);
	}

	/**
	 * For each group, check and add it.
	 */
	private void addFilteredByPattern(final Set<T> visibleGroups, final String criteria, final Set<T> result) {
		for (final T group : visibleGroups) {
			// Check the group matches
			addFilteredByPattern(criteria, result, group);
		}
	}

	/**
	 * Check the pattern against the given group.
	 */
	private void addFilteredByPattern(final String criteria, final Set<T> result, final T group) {
		if (StringUtils.isEmpty(criteria) || matchPattern(group, criteria)) {
			// Pattern match
			result.add(group);
		}
	}

	/**
	 * Indicates the given group matches to the given pattern.
	 */
	private boolean matchPattern(final T group, final String criteria) {
		return StringUtils.containsIgnoreCase(group.getName(), criteria);
	}


	/**
	 * Find a container from its identifier. Security is applied regarding the given user.
	 * 
	 * @param user
	 *            The user requesting this container.
	 * @param id
	 *            The container's identifier. Will be normalized.
	 * @return The container from its identifier. <code>null</code> if the container is not found or cannot be seen by
	 *         the given user.
	 */
	@Override
	public T findById(final String user, final String id) {
		// Check the container exists and return the in memory object.
		return Optional.ofNullable(getCacheRepository().findById(user, Normalizer.normalize(id))).map(CacheContainer::getId).map(this::findById)
				.orElse(null);
	}
}
