package org.ligoj.app.resource.ldap;

import java.security.SecureRandom;
import java.util.Date;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import javax.mail.Message;
import javax.mail.internet.InternetAddress;
import javax.transaction.Transactional;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.SecurityContext;

import org.apache.commons.lang3.CharEncoding;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessagePreparator;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.encoding.LdapShaPasswordEncoder;
import org.springframework.stereotype.Service;

import org.ligoj.bootstrap.core.SpringUtils;
import org.ligoj.bootstrap.core.resource.BusinessException;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.ligoj.app.dao.PasswordResetRepository;
import org.ligoj.app.iam.IamProvider;
import org.ligoj.app.iam.ldap.dao.UserLdapRepository;
import org.ligoj.app.model.PasswordReset;
import org.ligoj.app.model.ldap.UserLdap;
import lombok.extern.slf4j.Slf4j;

/**
 * LDAP password resource.
 */
@Path("/ldap/password")
@Service
@Transactional
@Produces(MediaType.APPLICATION_JSON)
@Slf4j
public class PasswordResource {

	@Value("${message.reset.subject}")
	protected String messageResetSubject;

	@Value("${message.reset}")
	protected String messageReset;

	@Value("${message.new.subject}")
	protected String messageNewSubject;

	@Value("${message.new}")
	protected String messageNew;

	@Value("${message.from}")
	protected String messageFrom;

	@Autowired
	private JavaMailSender mailSender;

	/**
	 * Shared Random instance.
	 */
	private static final Random RANDOM = new SecureRandom();

	/**
	 * IAM provider.
	 */
	@Autowired
	protected IamProvider iamProvider;

	@Autowired
	private PasswordResetRepository repository;

	@Value("${smtp.from}")
	private String mailFrom;

	@Value("${ldap.ssp}")
	private String sspUrl;

	/**
	 * Digest with SSHA the given clear password.
	 * 
	 * @param password
	 *            the clear password to digest.
	 * @return a SSHA digest.
	 */
	public String digest(final String password) {
		final byte[] bytes = new byte[4];
		RANDOM.nextBytes(bytes);
		return new LdapShaPasswordEncoder().encodePassword(password, bytes);
	}

	/**
	 * Generate a random password.
	 * 
	 * @return a generated password.
	 */
	public String generate() {
		return RandomStringUtils.randomAlphanumeric(10);
	}

	/**
	 * Update user password for current user.
	 * 
	 * @param request
	 *            the user request.
	 */
	@PUT
	@Consumes(MediaType.APPLICATION_JSON)
	public void update(final ResetPassword request, @Context final SecurityContext context) {
		final String login = context.getUserPrincipal().getName();

		// Check user and password
		if (!getUser().authenticate(login, request.getPassword())) {
			throw new ValidationJsonException("password", "login");
		}

		// Update password
		create(login, request.getNewPassword(), false);
	}

	/**
	 * Reset password from a mail challenge :token + mail + user name.
	 * 
	 * @param request
	 *            the user request.
	 * @param uid
	 *            the user UID.
	 */
	@POST
	@Path("reset/{uid}")
	@Consumes(MediaType.APPLICATION_JSON)
	public void reset(final ResetPasswordByMailChallenge request, @PathParam("uid") final String uid) {
		// check token in database : Invalid token, or out-dated, or invalid user ?
		final PasswordReset passwordReset = repository.findByLoginAndTokenAndDateAfter(uid, request.getToken(),
				DateTime.now().minusHours(NumberUtils.INTEGER_ONE).toDate());
		if (passwordReset == null) {
			throw new BusinessException(BusinessException.KEY_UNKNOW_ID);
		}

		// Check the user and update his/her password
		create(uid, request.getPassword(), false);

		// Remove password reset request since this token is no more valid
		repository.delete(passwordReset);
	}

	/**
	 * Manage user password recovery with valid user name and mail.
	 * 
	 * @param uid
	 *            user identifier.
	 * @param mail
	 *            user mail to match.
	 */
	@POST
	@Path("recovery/{uid}/{mail}")
	public void requestRecovery(@PathParam("uid") final String uid, @PathParam("mail") final String mail) {
		// Check user, then mail, then brute force resistance
		final UserLdap userLdap = getUser().findById(uid);
		if (userLdap != null && userLdap.getLocked() == null) {
			// Case insensitive match
			final Set<String> mails = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
			mails.addAll(userLdap.getMails());
			if (!mails.add(mail) && repository.findByLoginAndDateAfter(uid, DateTime.now().minusMinutes(5).toDate()) == null) {
				// We accept password reset only if no request has been done for 5 minutes
				createPasswordReset(uid, mail, userLdap, UUID.randomUUID().toString());
			}
		}
	}

	/**
	 * Create a password reset. Previous token are kept.
	 */
	private void createPasswordReset(final String uid, final String mail, final UserLdap userLdap, final String token) {
		final PasswordReset passwordReset = new PasswordReset();
		passwordReset.setLogin(uid);
		passwordReset.setToken(token);
		passwordReset.setDate(new Date());
		repository.saveAndFlush(passwordReset);
		sendMailReset(userLdap, mail, token);
	}

	/**
	 * Send mail for reset request
	 * 
	 * @param user
	 *            user account.
	 * @param mail
	 *            mail
	 * @param token
	 *            Random token.
	 */
	protected void sendMailReset(final UserLdap user, final String mail, final String token) {
		final MimeMessagePreparator preparator = mimeMessage -> {
			final String fullName = user.getFirstName() + " " + user.getLastName();
			final InternetAddress internetAddress = new InternetAddress(mail, fullName, CharEncoding.UTF_8);
			String link = sspUrl + "#reset=" + token + "/" + user.getId();
			link = "<a href=\"" + link + "\">" + link + "</a>";
			mimeMessage.setHeader("Content-Type", "text/plain; charset=UTF-8");
			mimeMessage.setFrom(new InternetAddress(mailFrom, messageFrom, CharEncoding.UTF_8));
			mimeMessage.setRecipient(Message.RecipientType.TO, internetAddress);
			mimeMessage.setSubject(messageResetSubject, CharEncoding.UTF_8);
			mimeMessage.setContent(String.format(messageReset, fullName, link, fullName, link), "text/html; charset=UTF-8");
		};
		this.mailSender.send(preparator);
	}

	/**
	 * Daily, clean old recovery requests.
	 */
	@Scheduled(cron = "0 0 1 1/1 * ?")
	public void cleanRecoveries() {
		// @Modifying + @Scheduled + @Transactional [+protected] --> No TX, wait for next release & TU
		SpringUtils.getBean(PasswordResource.class).cleanRecoveriesInternal();
	}

	/**
	 * Clean old recovery requests
	 */
	public void cleanRecoveriesInternal() {
		repository.deleteByDateBefore(DateTime.now().minusDays(1).toDate());
	}

	/**
	 * Set a generated password of given user (UID) and return the generated one. This password is stored as digested in
	 * corresponding LDAP entry.
	 * 
	 * @param uid
	 *            LDAP UID of user.
	 * @return the clear generated password.
	 */
	protected String generate(final String uid) {
		return create(uid, generate());
	}

	/**
	 * Set the password of given user (UID) and return the generated one. This password is stored as digested in
	 * corresponding LDAP entry.
	 * 
	 * @param uid
	 *            LDAP UID of user.
	 * @param password
	 *            The password to set.
	 * @return the clear generated password.
	 */
	protected String create(final String uid, final String password) {
		return create(uid, password, true);
	}

	/**
	 * Set the password of given user (UID) and return the generated one. This password is stored as digested in
	 * corresponding LDAP entry.
	 * 
	 * @param uid
	 *            LDAP UID of user.
	 * @param password
	 *            The password to set..
	 * @param sendMail
	 *            send a mail if true.
	 * @return the clear generated password.
	 */
	protected String create(final String uid, final String password, final boolean sendMail) {
		final UserLdap userLdap = checkUser(uid);

		// Replace the old or create a new one
		getUser().set(userLdap, "userPassword", digest(password));
		if (sendMail) {
			sendPasswordMail(userLdap, password);
		}
		return password;
	}

	/**
	 * Check the user exists.
	 * 
	 * @param uid
	 *            UID of user to lookup.
	 * @return {@link UserLdap} LDAP entry.
	 */
	private UserLdap checkUser(final String uid) {
		// Check the user without using cache
		final UserLdap userLdap = getUser().findByIdNoCache(uid);
		if (userLdap == null || userLdap.getLocked() != null) {
			throw new BusinessException(BusinessException.KEY_UNKNOW_ID, uid);
		}
		return userLdap;
	}

	/**
	 * Send the mail of password to the user.
	 */
	protected void sendPasswordMail(final UserLdap user, final String password) {
		log.info("Sending mail to '{}' at {}", user.getId(), user.getMails());
		final MimeMessagePreparator preparator = mimeMessage -> {
			final InternetAddress[] internetAddresses = new InternetAddress[user.getMails().size()];
			final String fullName = user.getFirstName() + " " + user.getLastName();
			final String link = "<a href=\"" + sspUrl + "\">" + sspUrl + "</a>";
			mimeMessage.setHeader("Content-Type", "text/plain; charset=UTF-8");
			mimeMessage.setFrom(new InternetAddress(mailFrom, messageFrom, CharEncoding.UTF_8));
			for (int i = 0; i < user.getMails().size(); i++) {
				internetAddresses[i] = new InternetAddress(user.getMails().get(i), fullName, CharEncoding.UTF_8);
			}
			mimeMessage.setSubject(String.format(messageNewSubject, fullName), CharEncoding.UTF_8);
			mimeMessage.setRecipients(Message.RecipientType.TO, internetAddresses);
			mimeMessage.setContent(String.format(messageNew, fullName, user.getId(), password, link, fullName, user.getId(), password, link),
					"text/html; charset=UTF-8");
		};
		this.mailSender.send(preparator);
	}

	/**
	 * User repository provider.
	 * 
	 * @return User repository provider.
	 */
	private UserLdapRepository getUser() {
		return (UserLdapRepository) iamProvider.getConfiguration().getUserLdapRepository();
	}

}
