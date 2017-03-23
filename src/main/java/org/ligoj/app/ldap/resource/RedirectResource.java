package org.ligoj.app.ldap.resource;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Date;
import java.util.Map;

import javax.transaction.Transactional;
import javax.ws.rs.CookieParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.ligoj.bootstrap.core.security.SecurityHelper;
import org.ligoj.bootstrap.dao.system.SystemUserSettingRepository;
import org.ligoj.bootstrap.model.system.SystemUserSetting;
import org.ligoj.bootstrap.resource.system.configuration.ConfigurationResource;
import org.ligoj.bootstrap.resource.system.user.UserSettingResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * Handle redirect request with a redirect either to login page, either the preferred page
 * of requesting user.<br>
 */
@Path("redirect")
@Service
@Transactional
@Slf4j
public class RedirectResource {

	/**
	 * Max age of cookie.
	 */
	private static final int COOKIE_AGE = (int) (DateUtils.MILLIS_PER_DAY / DateUtils.MILLIS_PER_SECOND * 365);

	@Autowired
	private SecurityHelper securityHelper;

	@Autowired
	private UserSettingResource userSettingResource;

	@Autowired
	private ConfigurationResource configuration;

	@Autowired
	private SystemUserSettingRepository repository;

	@Autowired
	private CompanyLdapResource companyLdapResource;

	public static final String PREFERRED_URL = "preferred-url";

	public static final String PREFERRED_HASH = "preferred-hash";

	public static final String PREFERRED_COOKIE_HASH = "saas-preferred-hash";

	/**
	 * Handle redirect request using cookie (checked, and updated), and the stored preferred URL.
	 * 
	 * @param cookieHash
	 *            the optional stored cookie URL.
	 * @return the computed redirect URL.
	 */
	@GET
	public Response handleRedirect(@CookieParam(PREFERRED_COOKIE_HASH) final String cookieHash) throws URISyntaxException {
		// Check the user is authenticated or not
		final String user = securityHelper.getLogin();

		if (isAnonymous(user)) {
			// Anonymous request, use the cookie hash to retrieve the user's preferred URL
			return redirect(getUrlFromCookie(cookieHash)).build();
		}
		// Authenticated user, use preferred URL if defined, and also republish the hash value
		final Map<String, Object> settings = userSettingResource.findAll(user);
		return addCookie(redirect((String) settings.get(PREFERRED_URL)), user, (String) settings.get(PREFERRED_HASH)).build();
	}

	/**
	 * Extract the user and the hash from the cookie value, then check the match and retrieve the associated URL.
	 */
	private String getUrlFromCookie(final String cookieHash) {
		final String[] cookieData = StringUtils.split(StringUtils.defaultIfBlank(cookieHash, ""), '|');
		if (cookieData.length == 2) {
			// It's a valid cookie syntax
			final String user = cookieData[0];
			return checkUrl(user, cookieData[1], userSettingResource.findAll(user));
		}

		// No URL found
		return null;
	}

	/**
	 * Check the hash for the given user against the stored value from the data base.
	 */
	private String checkUrl(final String user, final String hashData, final Map<String, Object> settings) {
		if (settings.containsKey(PREFERRED_HASH)) {
			// User has selected a preferred URL, check the corresponding hash
			if (hashData.equals(settings.get(PREFERRED_HASH))) {
				// Hash matches, return the URL
				return (String) settings.get(PREFERRED_URL);
			}

			// Attempt or bug? report it.
			log.error("Attempt to access preferred URL with cookie value : {}|{}", user, hashData);
		}
		return null;
	}

	/**
	 * Create a setting for current user, and assign as a cookie the hash value.
	 * 
	 * @param newPreferred
	 *            The new preferred URL.
	 * @return The response containing the cookie.
	 */
	@POST
	@PUT
	public Response saveOrUpdate(final String newPreferred) {

		// Save the new URL
		userSettingResource.saveOrUpdate(PREFERRED_URL, newPreferred);

		// Check the current hash
		SystemUserSetting setting = repository.findByLoginAndName(securityHelper.getLogin(), PREFERRED_HASH);
		if (setting == null) {

			// No hash generated for this user, create a new one
			setting = new SystemUserSetting();
			setting.setLogin(SecurityContextHolder.getContext().getAuthentication().getName());
			setting.setName(PREFERRED_HASH);
			setting.setValue(RandomStringUtils.randomAlphanumeric(100));
			repository.saveAndFlush(setting);
		}

		// Send back to the user the cookie
		return addCookie(Response.noContent(), securityHelper.getLogin(), setting.getValue()).build();
	}

	/**
	 * Return the stored hash as cookie for the current authenticated used.
	 * 
	 * @param login
	 *            related user.
	 * @return the {@link Response} including the stored cookie value from the data base.
	 */
	public ResponseBuilder buildCookieResponse(final String login) {
		// Return the stored hash as cookie
		return addCookie(Response.noContent(), login, userSettingResource.findByName(login, PREFERRED_HASH));
	}

	/**
	 * Return the generated hash as cookie
	 * 
	 * @param login
	 *            User login used to match the hash.
	 * @param hash
	 *            The cookie value also stored in data base.
	 * @return the {@link Response} including cookie value.
	 */
	public ResponseBuilder addCookie(final ResponseBuilder response, final String login, final String hash) {
		if (hash != null) {
			// There is a preference, add it to a cookie
			final Date expire = new Date(System.currentTimeMillis() + COOKIE_AGE * DateUtils.MILLIS_PER_SECOND);
			final NewCookie cookieHash = new NewCookie(PREFERRED_COOKIE_HASH, login + "|" + hash, "/", null, Cookie.DEFAULT_VERSION, null, COOKIE_AGE,
					expire, true, true);
			response.cookie(cookieHash);
		}
		return response;
	}

	/**
	 * Redirect to the given URL.
	 */
	private ResponseBuilder redirect(final String url) throws URISyntaxException {
		if (url == null) {
			// No URL found or guessed from the header, redirect to the home page
			return redirectToHome();
		}
		// Preferred URL has been found, use it and, also update the cookie
		return redirectToUrl(url);
	}

	private boolean isAnonymous(final String user) {
		return user == null || "anonymousUser".equals(user);
	}

	/**
	 * Redirect to main home page.
	 */
	protected ResponseBuilder redirectToHome() throws URISyntaxException {
		final String user = securityHelper.getLogin();
		if (isAnonymous(user)) {
			// We know nothing about the user
			return redirectToUrl(configuration.get("redirect.external.home"));
		}

		// Guess the company of this user
		if (companyLdapResource.isUserInternalCommpany()) {
			// Internal user, redirect to the default URL of corporate user
			return redirectToUrl(configuration.get("redirect.internal.home"));
		}

		// Not internal user, redirect to the other home page
		return redirectToUrl(configuration.get("redirect.external.home"));

	}

	/**
	 * Build a redirect to a specific URL.
	 */
	private ResponseBuilder redirectToUrl(final String url) throws URISyntaxException {
		return Response.status(Status.FOUND).location(new URI(url));
	}
}
