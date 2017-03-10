package org.ligoj.app.resource.ldap;

import java.util.*;
import java.util.regex.Pattern;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import javax.transaction.Transactional;

import org.joda.time.DateTime;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessagePreparator;
import org.springframework.security.util.FieldUtils;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import org.ligoj.bootstrap.core.resource.BusinessException;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.ligoj.app.MatcherUtil;
import org.ligoj.app.dao.PasswordResetRepository;
import org.ligoj.app.model.PasswordReset;
import org.ligoj.app.model.ldap.UserLdap;

/**
 * Test of {@link PasswordResource}
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = "classpath:/META-INF/spring/application-context-test.xml")
@Rollback
@Transactional
public class PasswordResourceTest extends AbstractContainerLdapResourceTest {

	@Autowired
	private PasswordResource resource;

	@Autowired
	private PasswordResetRepository repository;

	@After
	@Before
	public void restore() {
		getUser().set(getUser().findById("fdaugan"), "userPassword", "secret");
		getUser().unlock(getUser().findById("fdaugan"));
	}

	@Test
	public void generate() {
		final String password = resource.generate();
		Assert.assertNotNull(password);
		Assert.assertEquals(10, password.length());
	}

	@Test
	public void generateForUnknownUser() {
		thrown.expect(BusinessException.class);
		thrown.expectMessage("unknown-id");
		resource.generate(DEFAULT_USER);
	}

	@Test
	public void generateForUser() {
		final String password = resource.generate("fdaugan");
		Assert.assertNotNull(password);
		Assert.assertEquals(10, password.length());
		getUser().authenticate("fdaugan", password);
	}

	@Test
	public void digest() {
		Assert.assertTrue(resource.digest("test").startsWith("{SSHA}"));
	}

	private Exception exOnPrepare = null;

	@Test
	public void sendMail() throws MessagingException {
		final PasswordResource resource = newResource();

		exOnPrepare = null;
		final MimeMessage message = Mockito.mock(MimeMessage.class);
		final JavaMailSender mailSender = new JavaMailSenderImpl() {
			@Override
			public void send(final MimeMessagePreparator mimeMessagePreparator) throws MailException {
				try {
					mimeMessagePreparator.prepare(message);
				} catch (final Exception e) {
					exOnPrepare = e;
				}
			}
		};
		FieldUtils.setProtectedFieldValue("mailSender", resource, mailSender);
		FieldUtils.setProtectedFieldValue("sspUrl", resource, "host");

		final UserLdap user = new UserLdap();
		user.setFirstName("John");
		user.setLastName("Doe");
		user.setId("fdauganB");
		user.setMails(Collections.singletonList("f.g@sample.com"));
		resource.sendPasswordMail(user, "password");
		Assert.assertNull(exOnPrepare);
		Mockito.verify(message, Mockito.atLeastOnce()).setContent(
				"John Doe-fdauganB-password-<a href=\"host\">host</a>-John Doe-fdauganB-password-<a href=\"host\">host</a>",
				"text/html; charset=UTF-8");
	}

	private PasswordResource newResource() {
		final PasswordResource resource = new PasswordResource();
		resource.messageFrom = "FROM";
		resource.messageNew = "%s-%s-%s-%s-%s-%s-%s-%s";
		resource.messageNewSubject = "NEW-%s";
		resource.messageReset = "%s-%s-%s-%s";
		resource.messageResetSubject = "RESET-%s";
		return resource;
	}

	@Test
	public void sendMailNoPassword() throws MessagingException {
		final PasswordResource resource = newResource();
		exOnPrepare = null;
		final MimeMessage message = Mockito.mock(MimeMessage.class);
		final JavaMailSender mailSender = new JavaMailSenderImpl() {
			@Override
			public void send(final MimeMessagePreparator mimeMessagePreparator) throws MailException {
				try {
					mimeMessagePreparator.prepare(message);
				} catch (final Exception e) {
					exOnPrepare = e;
				}
			}
		};
		FieldUtils.setProtectedFieldValue("mailSender", resource, mailSender);
		FieldUtils.setProtectedFieldValue("sspUrl", resource, "host");

		final UserLdap user = new UserLdap();
		user.setFirstName("John");
		user.setLastName("Doe");
		user.setId("fdauganB");
		user.setMails(Collections.singletonList("f.g@sample.com"));
		resource.sendPasswordMail(user, null);
		Assert.assertNull(exOnPrepare);
		Mockito.verify(message, Mockito.atLeastOnce()).setContent(
				"John Doe-fdauganB-null-<a href=\"host\">host</a>-John Doe-fdauganB-null-<a href=\"host\">host</a>", "text/html; charset=UTF-8");
	}

	@Test
	public void requestRecoveryUserNotFound() {
		resource.requestRecovery("fdauganB", "f.d@sample.com");
		Assert.assertEquals(0, repository.findAll().size());
	}

	@Test
	public void requestRecoveryBadMail() {
		resource.requestRecovery("fdaugan", "f.d@sample.com");
		Assert.assertEquals(0, repository.findAll().size());
	}

	@Test
	public void requestRecoveryLocked() {
		getUser().lock(DEFAULT_USER, getUser().findById("fdaugan"));
		resource.requestRecovery("fdaugan", "f.d@sample.com");
		Assert.assertEquals(0, repository.findAll().size());
	}

	@Test
	public void requestRecovery() throws MessagingException {
		final PasswordResource resource = newResource();
		exOnPrepare = null;
		final MimeMessage message = Mockito.mock(MimeMessage.class);
		final JavaMailSender mailSender = new JavaMailSenderImpl() {
			@Override
			public void send(final MimeMessagePreparator mimeMessagePreparator) throws MailException {
				try {
					mimeMessagePreparator.prepare(message);
				} catch (final Exception e) {
					exOnPrepare = e;
				}
			}
		};
		FieldUtils.setProtectedFieldValue("mailSender", resource, mailSender);
		FieldUtils.setProtectedFieldValue("iamProvider", resource, iamProvider);
		FieldUtils.setProtectedFieldValue("repository", resource, repository);
		FieldUtils.setProtectedFieldValue("sspUrl", resource, "host");

		resource.requestRecovery("fdaugan", "Fabrice.DAugan@sample.com");
		em.flush();

		Assert.assertNull(exOnPrepare);
		final List<PasswordReset> requests = repository.findAll();
		Assert.assertEquals(1, requests.size());
		final PasswordReset passwordReset = requests.get(0);
		Assert.assertEquals("fdaugan", passwordReset.getLogin());

		Mockito.verify(message, Mockito.atLeastOnce())
				.setContent("Fabrice Daugan-<a href=\"host#reset=" + passwordReset.getToken() + "/fdaugan\">host#reset=" + passwordReset.getToken()
						+ "/fdaugan</a>-Fabrice Daugan-<a href=\"host#reset=" + passwordReset.getToken() + "/fdaugan\">host#reset="
						+ passwordReset.getToken() + "/fdaugan</a>", "text/html; charset=UTF-8");
	}

	@Test
	public void resetTooOld() {
		final PasswordResource resource = newResource();
		FieldUtils.setProtectedFieldValue("iamProvider", resource, iamProvider);
		FieldUtils.setProtectedFieldValue("repository", resource, repository);

		// prepare existing request
		final PasswordReset pwdReset = new PasswordReset();
		pwdReset.setDate(new Date());
		pwdReset.setLogin("fdaugan");
		pwdReset.setToken("t-t-t-t");
		repository.save(pwdReset);
		resource.requestRecovery("fdaugan", "fabrice.daugan@sample.com");
		em.flush();

		Assert.assertNull(exOnPrepare);
		final PasswordReset passwordReset = repository.findAll().get(0);

		Assert.assertEquals(pwdReset.getDate(), passwordReset.getDate());
	}

	@Test
	public void reset() {
		resource.reset(prepareReset("fdaugan"), "fdaugan");

		// check mocks
		Assert.assertNull(repository.findByLoginAndTokenAndDateAfter("fdaugan", "t-t-t-t", new Date()));
		getUser().authenticate("fdaugan", "Strong3r");
	}

	@Test
	public void resetInvalidUser() {
		thrown.expect(BusinessException.class);
		thrown.expectMessage("unknown-id");
		resource.reset(prepareReset("any"), "any");
	}

	@Test
	public void resetLockedUser() {
		thrown.expect(BusinessException.class);
		thrown.expectMessage("unknown-id");

		getUser().lock(DEFAULT_USER, getUser().findById("fdaugan"));
		resource.reset(prepareReset("fdaugan"), "fdaugan");
	}

	@Test
	public void restInvalidToken() {
		thrown.expect(BusinessException.class);
		thrown.expectMessage("unknown-id");

		// call business
		final ResetPasswordByMailChallenge userResetPassword = new ResetPasswordByMailChallenge();
		userResetPassword.setToken("bad-token");
		userResetPassword.setPassword("Strong3r");
		resource.reset(userResetPassword, "mdupont");
		em.flush();
	}

	private ResetPasswordByMailChallenge prepareReset(final String user) {
		// create dataset
		final PasswordReset pwdReset = new PasswordReset();
		pwdReset.setLogin(user);
		pwdReset.setToken("t-t-t-t");
		pwdReset.setDate(new Date());
		repository.save(pwdReset);
		em.flush();

		// call business
		final ResetPasswordByMailChallenge userResetPassword = new ResetPasswordByMailChallenge();
		userResetPassword.setToken("t-t-t-t");
		userResetPassword.setPassword("Strong3r");
		return userResetPassword;
	}

	@Test
	public void confirmRecoveryOldToken() {
		thrown.expect(BusinessException.class);
		thrown.expectMessage("unknown-id");

		// create dataset
		final PasswordReset pwdReset = createRequest();
		repository.save(pwdReset);
		em.flush();

		// call business
		final ResetPasswordByMailChallenge userResetPassword = new ResetPasswordByMailChallenge();
		userResetPassword.setToken("t-t-t-t");
		userResetPassword.setPassword("Strong3r");
		resource.reset(userResetPassword, "mdupont");
		Assert.assertEquals(1, repository.count());
		final PasswordReset passwordReset = repository.findAll().get(0);
		Assert.assertEquals("mdupont", passwordReset.getLogin());
	}

	@Test
	public void updateAuthenticationFailed() {
		thrown.expect(ValidationJsonException.class);
		thrown.expect(MatcherUtil.validationMatcher("password", "login"));

		final ResetPassword request = new ResetPassword();
		request.setNewPassword("Strong3r");
		request.setPassword("any");
		resource.update(request, getJaxRsSecurityContext(DEFAULT_USER));
	}

	@Test
	public void update() {
		final ResetPassword request = new ResetPassword();
		request.setPassword("Azerty01");
		request.setNewPassword("Azerty02");
		resource.update(request, getJaxRsSecurityContext("fdauganA"));
		getUser().authenticate("fdauganA", "Azerty02");

		// Restore old value
		request.setPassword("Azerty02");
		request.setNewPassword("Azerty01");
		resource.update(request, getJaxRsSecurityContext("fdauganA"));
	}

	@Test
	public void cleanRecoveriesAllRequests() {
		// create dataset
		final PasswordReset pwdResetOld = createRequest();
		repository.save(pwdResetOld);
		em.flush();

		// call
		resource.cleanRecoveries();

		// check
		Assert.assertEquals(0, repository.count());
	}

	@Test
	public void cleanRecoveriesOneRequest() {
		// create dataset
		final PasswordReset pwdResetOld = createRequest();
		repository.save(pwdResetOld);
		final PasswordReset pwdReset = createRequest();
		pwdReset.setDate(new Date());
		pwdReset.setLogin(DEFAULT_USER);
		repository.save(pwdReset);
		em.flush();

		// call
		resource.cleanRecoveries();

		// check
		Assert.assertEquals(1, repository.count());
	}

	@Test
	public void cleanRecoveriesNoRequests() {
		// create dataset
		final PasswordReset pwdReset1 = createRequest();
		pwdReset1.setDate(new Date());
		repository.save(pwdReset1);
		final PasswordReset pwdReset2 = createRequest();
		pwdReset2.setDate(DateTime.now().minusHours(1).toDate());
		pwdReset2.setLogin(DEFAULT_USER);
		repository.save(pwdReset2);
		em.flush();

		// call
		resource.cleanRecoveries();

		// check
		Assert.assertEquals(2, repository.count());

	}

	/**
	 * create basic data
	 * 
	 * @return password reset
	 */
	private PasswordReset createRequest() {
		final PasswordReset pwdReset = new PasswordReset();
		pwdReset.setLogin("mdupont");
		pwdReset.setToken("t-t-t-t");
		pwdReset.setDate(new GregorianCalendar(2012, 2, 2).getTime());
		return pwdReset;
	}

	@Test
	public void checkPassword() {
		final Pattern pattern = Pattern.compile(ResetPassword.COMPLEXITY_PATTERN);

		// Accepted password
		Assert.assertTrue(pattern.matcher("aZ1-----").matches());
		Assert.assertTrue(pattern.matcher("aZ3rty?;").matches());
		Assert.assertTrue(pattern.matcher("azertyY2").matches());
		Assert.assertTrue(pattern.matcher("AZERTYa0").matches());
		Assert.assertTrue(pattern.matcher("b1234567890&#'{}()[].,;:!|<>-=+*_@$?§/£Y").matches());
		Assert.assertTrue(pattern.matcher("b0&#$%_-/:µ,.~¤!§*£=+|{}[]?<>;'&B").matches());

		// Rejected password
		Assert.assertFalse(pattern.matcher("AZERYa0").matches());
		Assert.assertFalse(pattern.matcher("AZERYUIO").matches());
		Assert.assertFalse(pattern.matcher("azertyop").matches());
		Assert.assertFalse(pattern.matcher("azerty0p").matches());
		Assert.assertFalse(pattern.matcher("AZERYUI0").matches());
		Assert.assertFalse(pattern.matcher("AZéRYUI0").matches());
	}
}
