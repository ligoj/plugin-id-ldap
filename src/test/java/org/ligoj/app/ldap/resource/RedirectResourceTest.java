package org.ligoj.app.ldap.resource;

import java.io.IOException;
import java.net.URISyntaxException;

import javax.transaction.Transactional;
import javax.ws.rs.core.Response;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ligoj.bootstrap.dao.system.SystemUserSettingRepository;
import org.ligoj.bootstrap.model.system.SystemConfiguration;
import org.ligoj.bootstrap.model.system.SystemUserSetting;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * Test class of {@link RedirectResource}
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = "classpath:/META-INF/spring/application-context-test.xml")
@Rollback
@Transactional
public class RedirectResourceTest extends AbstractLdapTest {

	@Autowired
	private RedirectResource resource;

	@Autowired
	private SystemUserSettingRepository userSettingRepository;

	@Before
	public void prepareConfiguration() throws IOException {
		persistEntities("csv/app-test", SystemConfiguration.class);
	}

	@Test
	public void handleRedirectAnonymousNoCookie() throws URISyntaxException {
		SecurityContextHolder.clearContext();

		final Response response = resource.handleRedirect(null);
		Assert.assertNull(response.getCookies().get(RedirectResource.PREFERRED_COOKIE_HASH));
		Assert.assertEquals("http://localhost:8081/external", response.getHeaderString("location"));
	}

	@Test
	public void handleRedirectAnonymousCookieNoSetting() throws URISyntaxException {
		SecurityContextHolder.clearContext();

		final Response response = resource.handleRedirect(DEFAULT_USER + "|hash");
		Assert.assertNull(response.getCookies().get(RedirectResource.PREFERRED_COOKIE_HASH));
		Assert.assertEquals("http://localhost:8081/external", response.getHeaderString("location"));
	}

	@Test
	public void handleRedirectAnonymousCookieNotMatch() throws URISyntaxException {
		SecurityContextHolder.clearContext();

		final SystemUserSetting setting = new SystemUserSetting();
		setting.setLogin(DEFAULT_USER);
		setting.setName(RedirectResource.PREFERRED_HASH);
		setting.setValue("-");
		userSettingRepository.save(setting);
		em.flush();
		em.clear();

		final Response response = resource.handleRedirect(DEFAULT_USER + "|hash");
		Assert.assertNull(response.getCookies().get(RedirectResource.PREFERRED_COOKIE_HASH));
		Assert.assertEquals("http://localhost:8081/external", response.getHeaderString("location"));
	}

	@Test
	public void handleRedirectAnonymous() throws URISyntaxException {
		SecurityContextHolder.clearContext();

		final SystemUserSetting setting = new SystemUserSetting();
		setting.setLogin(DEFAULT_USER);
		setting.setName(RedirectResource.PREFERRED_HASH);
		setting.setValue("hash");
		userSettingRepository.save(setting);
		final SystemUserSetting setting2 = new SystemUserSetting();
		setting2.setLogin(DEFAULT_USER);
		setting2.setName(RedirectResource.PREFERRED_URL);
		setting2.setValue("http://localhost:1/any");
		userSettingRepository.save(setting2);
		em.flush();
		em.clear();

		final Response response = resource.handleRedirect(DEFAULT_USER + "|hash");
		Assert.assertNull(response.getCookies().get(RedirectResource.PREFERRED_COOKIE_HASH));
		Assert.assertEquals("http://localhost:1/any", response.getHeaderString("location"));
	}

	@Test
	public void handleRedirect() throws URISyntaxException {
		final SystemUserSetting setting = new SystemUserSetting();
		setting.setLogin(DEFAULT_USER);
		setting.setName(RedirectResource.PREFERRED_HASH);
		setting.setValue("hash");
		userSettingRepository.save(setting);
		final SystemUserSetting setting2 = new SystemUserSetting();
		setting2.setLogin(DEFAULT_USER);
		setting2.setName(RedirectResource.PREFERRED_URL);
		setting2.setValue("http://localhost:1/any");
		userSettingRepository.save(setting2);
		em.flush();
		em.clear();

		final Response response = resource.handleRedirect("any");
		Assert.assertEquals(DEFAULT_USER + "|hash", response.getCookies().get(RedirectResource.PREFERRED_COOKIE_HASH).getValue());
		Assert.assertEquals("http://localhost:1/any", response.getHeaderString("location"));
	}

	@Test
	public void handleRedirectNoCookie() throws URISyntaxException {
		final SystemUserSetting setting = new SystemUserSetting();
		setting.setLogin(DEFAULT_USER);
		setting.setName(RedirectResource.PREFERRED_HASH);
		setting.setValue("hash");
		userSettingRepository.save(setting);
		final SystemUserSetting setting2 = new SystemUserSetting();
		setting2.setLogin(DEFAULT_USER);
		setting2.setName(RedirectResource.PREFERRED_URL);
		setting2.setValue("http://localhost:1/any");
		userSettingRepository.save(setting2);
		em.flush();
		em.clear();

		final Response response = resource.handleRedirect(null);
		Assert.assertEquals(DEFAULT_USER + "|hash", response.getCookies().get(RedirectResource.PREFERRED_COOKIE_HASH).getValue());
		Assert.assertEquals("http://localhost:1/any", response.getHeaderString("location"));
	}

	@Test
	public void buildCookieResponseNoHash() {
		final Response response = resource.buildCookieResponse("any").build();
		Assert.assertNull(response.getCookies().get(RedirectResource.PREFERRED_COOKIE_HASH));
		Assert.assertNull(response.getHeaderString("location"));
	}

	@Test
	public void buildCookieResponse() {
		final SystemUserSetting setting = new SystemUserSetting();
		setting.setLogin(DEFAULT_USER);
		setting.setName(RedirectResource.PREFERRED_HASH);
		setting.setValue("hash");
		userSettingRepository.save(setting);

		final Response response = resource.buildCookieResponse(DEFAULT_USER).build();
		Assert.assertEquals("junit|hash", response.getCookies().get(RedirectResource.PREFERRED_COOKIE_HASH).getValue());
		Assert.assertNull(response.getHeaderString("location"));
	}

	@Test
	public void redirectToHomeAnonymous() throws URISyntaxException {
		SecurityContextHolder.clearContext();
		final Response response = resource.redirectToHome().build();
		Assert.assertEquals(302, response.getStatus());
		Assert.assertEquals("http://localhost:8081/external", response.getHeaderString("location"));
	}

	/**
	 * Special Spring-security anonymous user.
	 */
	@Test
	public void redirectToHomeAnonymous2() throws URISyntaxException {
		initSpringSecurityContext("anonymousUser");
		final Response response = resource.redirectToHome().build();
		Assert.assertEquals(302, response.getStatus());
		Assert.assertEquals("http://localhost:8081/external", response.getHeaderString("location"));
	}

	@Test
	public void redirectToHomeInternal() throws URISyntaxException {
		initSpringSecurityContext("fdaugan");
		final Response response = resource.redirectToHome().build();
		Assert.assertEquals(302, response.getStatus());
		Assert.assertEquals("http://localhost:8081/internal", response.getHeaderString("location"));
	}

	@Test
	public void redirectToHomeExternal() throws URISyntaxException {
		initSpringSecurityContext("fdoe2");
		final Response response = resource.redirectToHome().build();
		Assert.assertEquals(302, response.getStatus());
		Assert.assertEquals("http://localhost:8081/external", response.getHeaderString("location"));
	}

	@Test
	public void saveOrUpdate() throws URISyntaxException {
		Response response = resource.saveOrUpdate("http://localhost:1/any");
		final String cookieValue = response.getCookies().get(RedirectResource.PREFERRED_COOKIE_HASH).getValue();
		Assert.assertTrue(cookieValue.startsWith(DEFAULT_USER + "|"));
		Assert.assertNull(response.getHeaderString("location"));

		response = resource.handleRedirect(null);
		Assert.assertEquals(cookieValue, response.getCookies().get(RedirectResource.PREFERRED_COOKIE_HASH).getValue());
		Assert.assertEquals("http://localhost:1/any", response.getHeaderString("location"));

		// Logout
		SecurityContextHolder.clearContext();
		response = resource.handleRedirect(cookieValue);
		Assert.assertNull(response.getCookies().get(RedirectResource.PREFERRED_COOKIE_HASH));
		Assert.assertEquals("http://localhost:1/any", response.getHeaderString("location"));

		// Change URL
		userSettingRepository.findByLoginAndName(DEFAULT_USER, RedirectResource.PREFERRED_URL).setValue("http://localhost:2/any");
		em.flush();
		em.clear();

		response = resource.handleRedirect(cookieValue);
		Assert.assertNull(response.getCookies().get(RedirectResource.PREFERRED_COOKIE_HASH));
		Assert.assertEquals("http://localhost:2/any", response.getHeaderString("location"));

		// Change hash
		userSettingRepository.findByLoginAndName(DEFAULT_USER, RedirectResource.PREFERRED_HASH).setValue("new-hash");
		userSettingRepository.findByLoginAndName(DEFAULT_USER, RedirectResource.PREFERRED_URL).setValue("http://localhost:2/any");
		em.flush();
		em.clear();
		response = resource.handleRedirect(cookieValue);
		Assert.assertNull(response.getCookies().get(RedirectResource.PREFERRED_COOKIE_HASH));
		Assert.assertEquals("http://localhost:8081/external", response.getHeaderString("location"));

		// Login
		initSpringSecurityContext(DEFAULT_USER);
		response = resource.handleRedirect(null);
		Assert.assertEquals(DEFAULT_USER + "|new-hash", response.getCookies().get(RedirectResource.PREFERRED_COOKIE_HASH).getValue());
		Assert.assertEquals("http://localhost:2/any", response.getHeaderString("location"));
	}

	@Test
	public void saveOrUpdateUpdate() throws URISyntaxException {
		final SystemUserSetting setting = new SystemUserSetting();
		setting.setLogin(DEFAULT_USER);
		setting.setName(RedirectResource.PREFERRED_HASH);
		setting.setValue("hash");
		userSettingRepository.save(setting);
		final SystemUserSetting setting2 = new SystemUserSetting();
		setting2.setLogin(DEFAULT_USER);
		setting2.setName(RedirectResource.PREFERRED_URL);
		setting2.setValue("http://localhost:1/any");
		userSettingRepository.save(setting2);
		em.flush();
		em.clear();

		Response response = resource.saveOrUpdate("http://localhost:2/any");
		em.flush();
		em.clear();
		final String cookieValue = response.getCookies().get(RedirectResource.PREFERRED_COOKIE_HASH).getValue();
		Assert.assertEquals(DEFAULT_USER + "|hash", cookieValue);
		Assert.assertNull(response.getHeaderString("location"));

		response = resource.handleRedirect(null);
		em.flush();
		em.clear();
		Assert.assertEquals(DEFAULT_USER + "|hash", response.getCookies().get(RedirectResource.PREFERRED_COOKIE_HASH).getValue());
		Assert.assertEquals("http://localhost:2/any", response.getHeaderString("location"));
	}

	public void saveOrUpdateNotCreated() {
		final Response response = resource.saveOrUpdate("http://localhost:2/any");
		Assert.assertNull(response.getCookies().get(RedirectResource.PREFERRED_COOKIE_HASH));
		Assert.assertNull(response.getHeaderString("location"));
	}
}
