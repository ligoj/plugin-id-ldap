package org.ligoj.app.iam.ldap.dao;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.naming.directory.AttributeInUseException;
import javax.naming.directory.SchemaViolationException;
import javax.naming.ldap.LdapName;
import javax.transaction.Transactional;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.mockito.internal.verification.VerificationModeFactory;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import org.ligoj.bootstrap.AbstractDataGeneratorTest;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.ligoj.app.MatcherUtil;
import org.ligoj.app.model.ldap.GroupLdap;
import org.ligoj.app.model.ldap.UserLdap;

/**
 * Test class of {@link GroupLdapRepository}
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = "classpath:/META-INF/spring/application-context-test.xml")
@Rollback
@Transactional
@org.junit.FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class GroupLdapRepositoryTest extends AbstractDataGeneratorTest {

	@Test
	public void addUser() {
		final Set<String> users = new HashSet<>();
		final GroupLdapRepository groupRepository = new GroupLdapRepository() {
			@Override
			public GroupLdap findById(final String name) {
				return new GroupLdap("dc=" + name, name, users);
			}

		};
		final LdapCacheRepository cacheRepository = Mockito.mock(LdapCacheRepository.class);
		groupRepository.setLdapCacheRepository(cacheRepository);
		final LdapTemplate ldapTemplate = Mockito.mock(LdapTemplate.class);
		groupRepository.setTemplate(ldapTemplate);
		addUser(groupRepository);

		Mockito.verify(cacheRepository, VerificationModeFactory.times(1)).addUserToGroup(ArgumentMatchers.any(UserLdap.class), ArgumentMatchers.any(GroupLdap.class));
	}

	@Test
	public void addUserAlreadyMember() {
		final Set<String> users = new HashSet<>();
		users.add("flast1");
		final GroupLdapRepository groupRepository = new GroupLdapRepository() {
			@Override
			public GroupLdap findById(final String name) {
				return new GroupLdap("dc=" + name, name, users);
			}

		};
		final LdapCacheRepository cacheRepository = Mockito.mock(LdapCacheRepository.class);
		groupRepository.setLdapCacheRepository(cacheRepository);
		final LdapTemplate ldapTemplate = Mockito.mock(LdapTemplate.class);
		groupRepository.setTemplate(ldapTemplate);
		addUser(groupRepository);

		Assert.assertEquals(1, users.size());
		Assert.assertTrue(users.contains("flast1"));
	}

	/**
	 * Mock a not managed LDAP desynchronization
	 */
	@Test(expected = org.springframework.ldap.AttributeInUseException.class)
	public void addUserSyncError() {
		final GroupLdapRepository groupRepository = newGroupLdapRepository();
		final LdapTemplate ldapTemplate = Mockito.mock(LdapTemplate.class);
		groupRepository.setTemplate(ldapTemplate);
		Mockito.doThrow(new org.springframework.ldap.AttributeInUseException(new AttributeInUseException("any"))).when(ldapTemplate)
				.modifyAttributes(ArgumentMatchers.any(LdapName.class), ArgumentMatchers.any());

		addUser(groupRepository);
	}

	/**
	 * Mock a managed LDAP desynchronization
	 */
	@Test
	public void addUserSync1() {
		final GroupLdapRepository groupRepository = newGroupLdapRepository();
		final LdapTemplate ldapTemplate = Mockito.mock(LdapTemplate.class);
		groupRepository.setTemplate(ldapTemplate);
		Mockito.doThrow(new org.springframework.ldap.AttributeInUseException(new AttributeInUseException("value #0 already exists")))
				.when(ldapTemplate).modifyAttributes(ArgumentMatchers.any(LdapName.class), ArgumentMatchers.any());

		addUser(groupRepository);
	}

	/**
	 * Mock a managed LDAP desynchronization
	 */
	@Test
	public void addUserSync2() {
		final GroupLdapRepository groupRepository = newGroupLdapRepository();
		final LdapTemplate ldapTemplate = Mockito.mock(LdapTemplate.class);
		groupRepository.setTemplate(ldapTemplate);
		Mockito.doThrow(new org.springframework.ldap.AttributeInUseException(new AttributeInUseException("ATTRIBUTE_OR_VALUE_EXISTS")))
				.when(ldapTemplate).modifyAttributes(ArgumentMatchers.any(LdapName.class), ArgumentMatchers.any());

		addUser(groupRepository);
	}

	@Test
	public void removeUser() {
		final GroupLdapRepository groupRepository = newGroupLdapRepository();
		final LdapTemplate ldapTemplate = Mockito.mock(LdapTemplate.class);
		groupRepository.setTemplate(ldapTemplate);
		removeUser(groupRepository);
	}

	/**
	 * Mock a managed LDAP schema violation
	 */
	@Test
	public void removeUserNotMember() {
		final GroupLdapRepository groupRepository = newGroupLdapRepository();
		final LdapTemplate ldapTemplate = Mockito.mock(LdapTemplate.class);
		groupRepository.setTemplate(ldapTemplate);
		Mockito.doThrow(new org.springframework.ldap.SchemaViolationException(new SchemaViolationException("any"))).when(ldapTemplate)
				.modifyAttributes(ArgumentMatchers.any(LdapName.class), ArgumentMatchers.any());
		removeUser(groupRepository);
	}

	/**
	 * Mock a managed LDAP schema violation
	 */
	@Test
	public void removeUserSchema() {
		thrown.expect(ValidationJsonException.class);
		thrown.expect(MatcherUtil.validationMatcher("groups", "last-member-of-group"));
		final GroupLdapRepository groupRepository = new GroupLdapRepository() {
			@Override
			public GroupLdap findById(final String name) {
				// The group has only the user user we want to remove
				return new GroupLdap("dc=" + name, name, Collections.singleton("flast1"));
			}

		};
		groupRepository.setLdapCacheRepository(Mockito.mock(LdapCacheRepository.class));

		final LdapTemplate ldapTemplate = Mockito.mock(LdapTemplate.class);
		groupRepository.setTemplate(ldapTemplate);
		Mockito.doThrow(new org.springframework.ldap.SchemaViolationException(new SchemaViolationException("any"))).when(ldapTemplate)
				.modifyAttributes(ArgumentMatchers.any(LdapName.class), ArgumentMatchers.any());
		removeUser(groupRepository);
	}

	@Test
	public void removeGroup() {
		final GroupLdapRepository groupRepository = newGroupLdapRepository();
		final LdapTemplate ldapTemplate = Mockito.mock(LdapTemplate.class);
		groupRepository.setTemplate(ldapTemplate);
		groupRepository.removeGroup(new GroupLdap("any", "any", null), "DIG RHA");
	}

	@Test
	public void addGroup() {
		final GroupLdapRepository groupRepository = newGroupLdapRepository();
		final LdapTemplate ldapTemplate = Mockito.mock(LdapTemplate.class);
		groupRepository.setTemplate(ldapTemplate);
		groupRepository.addGroup(new GroupLdap("dc=any", "any", null), "DIG RHA");
	}

	/**
	 * Mock a managed LDAP desynchronization
	 */
	@Test
	public void removeUserSync() {
		final GroupLdapRepository groupRepository = new GroupLdapRepository() {
			@Override
			public GroupLdap findById(final String name) {
				// The group has only the user user we want to remove
				return new GroupLdap("dc=" + name, name, Collections.singleton("flast1"));
			}

		};
		groupRepository.setLdapCacheRepository(Mockito.mock(LdapCacheRepository.class));
		final LdapTemplate ldapTemplate = Mockito.mock(LdapTemplate.class);
		groupRepository.setTemplate(ldapTemplate);
		Mockito.doThrow(new org.springframework.ldap.AttributeInUseException(new AttributeInUseException("any"))).when(ldapTemplate)
				.modifyAttributes(ArgumentMatchers.any(LdapName.class), ArgumentMatchers.any());
		removeUser(groupRepository);
	}

	private GroupLdapRepository newGroupLdapRepository() {
		final GroupLdapRepository groupRepository = new GroupLdapRepository() {
			@Override
			public GroupLdap findById(final String name) {
				return new GroupLdap("dc=" + name, name, new HashSet<>());
			}

		};
		groupRepository.setLdapCacheRepository(Mockito.mock(LdapCacheRepository.class));
		return groupRepository;
	}

	private void removeUser(final GroupLdapRepository groupRepository) {
		final UserLdap user = new UserLdap();
		user.setId("flast1");
		user.setDn("dc=com");
		user.setCompany("ing");
		groupRepository.removeUser(user, "DIG RHA");
	}

	private void addUser(final GroupLdapRepository groupRepository) {
		final UserLdap user = new UserLdap();
		user.setId("flast1");
		user.setDn("dc=com");
		user.setCompany("ing");
		groupRepository.addUser(user, "DIG RHA");
	}

}
