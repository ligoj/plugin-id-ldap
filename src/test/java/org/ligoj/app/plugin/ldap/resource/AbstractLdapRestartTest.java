/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.ldap.resource;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.ligoj.app.iam.IamConfiguration;
import org.ligoj.app.iam.IamProvider;
import org.ligoj.app.plugin.id.dao.CacheCopyStateRepository;
import org.ligoj.app.plugin.id.model.CacheCopyState;
import org.ligoj.app.plugin.ldap.dao.AbstractManagedLdapRepository;
import org.ligoj.app.plugin.ldap.dao.CacheLdapRepository;
import org.ligoj.app.plugin.ldap.dao.UserLdapRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Restart simulation of the API: the memory and the cache marker are lost, the database copy stays; and a way to
 * prove nothing is read from LDAP.
 */
public abstract class AbstractLdapRestartTest extends AbstractLdapPluginResourceTest {

	@Autowired
	protected IamProvider[] iamProviders;

	@Autowired
	protected CacheCopyStateRepository stateRepository;

	/**
	 * The real templates of the repositories replaced by {@link #failLdap()}.
	 */
	private final Map<AbstractManagedLdapRepository<?>, LdapTemplate> templates = new HashMap<>();

	protected IamConfiguration configuration() {
		return iamProviders[0].getConfiguration();
	}

	protected CacheLdapRepository target() {
		return AopTestUtils.getTargetObject(cache);
	}

	/**
	 * Simulate a restart: the memory and the cache marker are lost, the database stays.
	 */
	protected void restart() {
		ReflectionTestUtils.setField(target(), "data", null);
		cacheManager.getCache("id-ldap-data").clear();
	}

	/**
	 * Make every LDAP read fail: a test passing with it read nothing from LDAP.
	 */
	protected void failLdap() {
		final var configuration = configuration();
		for (final var repository : List.<AbstractManagedLdapRepository<?>>of((UserLdapRepository) configuration.getUserRepository(),
				(AbstractManagedLdapRepository<?>) configuration.getGroupRepository(),
				(AbstractManagedLdapRepository<?>) configuration.getCompanyRepository())) {
			final var original = repository.getTemplate();
			templates.putIfAbsent(repository, original);
			final var failing = mock(LdapTemplate.class, i -> {
				throw new IllegalStateException("LDAP read: " + i.getMethod().getName());
			});
			doReturn(original.getContextSource()).when(failing).getContextSource();
			repository.setTemplate(failing);
		}
	}

	@AfterEach
	protected void restoreLdap() {
		templates.forEach(AbstractManagedLdapRepository::setTemplate);
		templates.clear();
	}

	protected CacheCopyState state() {
		return stateRepository.findById(configuration().getNode()).orElse(null);
	}

	protected void setLastFullRead(final Instant lastFullRead) {
		final var state = state();
		state.setLastFullRead(lastFullRead);
		stateRepository.saveAndFlush(state);
	}
}
