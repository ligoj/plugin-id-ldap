/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.id.ldap.dao;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.IteratorUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
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

import java.util.*;

/**
 * LDAP container repository.
 *
 * @param <T> The container type.
 * @param <C> The container cache type.
 */
@Slf4j
public abstract class AbstractContainerLdapRepository<T extends ContainerOrg, C extends CacheContainer> extends AbstractManagedLdapRepository<T>
		implements IContainerRepository<T> {

	protected static final Sort.Order DEFAULT_ORDER = new Sort.Order(Direction.ASC, "name");

	@Autowired
	protected InMemoryPagination inMemoryPagination;

	@Autowired
	@Setter
	protected CacheLdapRepository cacheRepository;

	/**
	 * Container type.
	 */
	private final ContainerType type;

	protected AbstractContainerLdapRepository(final ContainerType type) {
		super(type.name().toLowerCase(Locale.ENGLISH));
		this.type = type;
	}

	/**
	 * Return the repository managing the cached containers.
	 *
	 * @return the repository managing the cached containers.
	 */
	protected abstract CacheContainerRepository<C> getCacheRepository();

	/**
	 * Create a new container bean. Not in the LDAP repository.
	 *
	 * @param dn The unique DN of the container.
	 * @param cn The human-readable name (CN) that will be used to build the identifier.
	 * @return A new transient container bean. Never <code>null</code>.
	 */
	protected abstract T newContainer(String dn, String cn);

	@Override
	public T create(final String dn, final String cn) {
		final var container = newContainer(dn, cn);

		// First, create the LDAP entry
		log.info("{} {} will be created as {}", type.name(), container.getName(), dn);
		bind(container, dn);

		// Return the new container
		return container;
	}

	@Override
	public Page<T> findAll(final Set<T> groups, final String criteria, final Pageable pageable,
			final Map<String, Comparator<T>> customComparators) {
		// Create the set with the right comparator
		final var orders = IteratorUtils
				.toList(ObjectUtils.getIfNull(pageable.getSort(), new ArrayList<Sort.Order>()).iterator());
		orders.add(DEFAULT_ORDER);
		final var order = orders.getFirst();
		var comparator = customComparators.get(order.getProperty());
		if (order.getDirection() == Direction.DESC) {
			comparator = Collections.reverseOrder(comparator);
		}
		final var result = new TreeSet<>(comparator);

		// Filter the groups, filtering by the criteria
		addFilteredByPattern(groups, criteria, result);

		// Apply in-memory pagination
		return inMemoryPagination.newPage(result, pageable);
	}

	/**
	 * For each group, check and add it.
	 */
	private void addFilteredByPattern(final Set<T> visibleGroups, final String criteria, final Set<T> result) {
		for (final var group : visibleGroups) {
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
		return Strings.CI.contains(group.getName(), criteria);
	}

	/**
	 * Find a container from its identifier. Security is applied regarding the given user.
	 *
	 * @param user The user requesting this container.
	 * @param id   The container's identifier. Will be normalized.
	 * @return The container from its identifier. <code>null</code> if the container is not found or cannot be seen by
	 * the given user.
	 */
	@Override
	public T findById(final String user, final String id) {
		// Check the container exists and return the in memory object.
		return Optional.ofNullable(getCacheRepository().findById(user, Normalizer.normalize(id)))
				.map(CacheContainer::getId).map(this::findById).orElse(null);
	}
}
