package org.ligoj.app.ldap.resource;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

import javax.validation.ValidationException;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.ligoj.app.api.UserLdap;
import org.ligoj.app.dao.ldap.DelegateLdapRepository;

//http://docs.spring.io/spring/docs/3.2.x/spring-framework-reference/html/scheduling.html
/**
 * LDAP import from list of bean entries.
 */
@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class UserAtomicLdapTask extends AbstractLdapBatchTask<UserUpdateEntry> {

	@Autowired
	protected UserLdapResource resource;

	@Autowired
	protected DelegateLdapRepository repository;

	/**
	 * Accepted update action types.
	 */
	private static final Map<String, UserBatchUpdateType> UPDATE_ACTION_TYPES = new HashMap<>();
	static {
		UPDATE_ACTION_TYPES.put("firstname", UserBatchUpdateType.ATTRIBUTE);
		UPDATE_ACTION_TYPES.put("lastname", UserBatchUpdateType.ATTRIBUTE);
		UPDATE_ACTION_TYPES.put("department", UserBatchUpdateType.ATTRIBUTE);
		UPDATE_ACTION_TYPES.put("localid", UserBatchUpdateType.ATTRIBUTE);
		UPDATE_ACTION_TYPES.put("company", UserBatchUpdateType.ATTRIBUTE);
		UPDATE_ACTION_TYPES.put("mail", UserBatchUpdateType.ATTRIBUTE);
		UPDATE_ACTION_TYPES.put("isolate", UserBatchUpdateType.ISOLATE);
		UPDATE_ACTION_TYPES.put("restore", UserBatchUpdateType.RESTORE);
		UPDATE_ACTION_TYPES.put("lock", UserBatchUpdateType.LOCK);
		UPDATE_ACTION_TYPES.put("delete", UserBatchUpdateType.DELETE);
	}

	/**
	 * Function for update action.
	 */
	private static final Map<String, BiConsumer<UserAtomicLdapTask, UserUpdateEntry>> FUNCTIONS = new HashMap<>();
	static {
		FUNCTIONS.put("firstname", (u, e) -> e.getUserLdap().setFirstName(e.getValue()));
		FUNCTIONS.put("lastname", (u, e) -> e.getUserLdap().setLastName(e.getValue()));
		FUNCTIONS.put("department", (u, e) -> e.getUserLdap().setDepartment(e.getValue()));
		FUNCTIONS.put("localid", (u, e) -> e.getUserLdap().setLocalId(e.getValue()));
		FUNCTIONS.put("company", (u, e) -> e.getUserLdap().setCompany(e.getValue()));
		FUNCTIONS.put("mail", (u, e) -> e.getUserLdap().setMail(e.getValue()));
		FUNCTIONS.put("isolate", (u, e) -> u.resource.isolate(e.getUser()));
		FUNCTIONS.put("restore", (u, e) -> u.resource.restore(e.getUser()));
		FUNCTIONS.put("lock", (u, e) -> u.resource.lock(e.getUser()));
		FUNCTIONS.put("delete", (u, e) -> u.resource.delete(e.getUser()));
	}

	@Override
	protected void doBatch(final UserUpdateEntry entry) throws Exception {

		final UserBatchUpdateType type = UPDATE_ACTION_TYPES.get(entry.getOperation());
		if (type == null) {
			// Non supported operation
			throw new ValidationException("unsupported-operation");
		}

		// Check the null value for non attribute operation
		if (type != UserBatchUpdateType.ATTRIBUTE && StringUtils.isNotBlank(entry.getValue())) {
			// Non supported operation
			throw new ValidationException("null-value-expected");
		}

		// Update the user
		if (type == UserBatchUpdateType.ATTRIBUTE) {
			// Fetch the user
			final UserLdap user = resource.findById(entry.getUser());

			// Prepare the local entity
			final UserLdapEdition editUser = new UserLdapEdition();
			editUser.setId(user.getId());
			editUser.setFirstName(user.getFirstName());
			editUser.setLastName(user.getLastName());
			editUser.setCompany(user.getCompany());
			editUser.setLastName(user.getLastName());
			editUser.setMail(user.getMails().stream().findFirst().orElse(null));
			editUser.setDepartment(user.getDepartment());
			editUser.setLocalId(user.getLocalId());
			editUser.setGroups(user.getGroups());

			// Save the initial state user
			entry.setUserLdap(editUser);

			// Execute atomic operation
			FUNCTIONS.get(entry.getOperation()).accept(this, entry);
			resource.update(entry.getUserLdap());
		} else {
			// Other self managed operation
			FUNCTIONS.get(entry.getOperation()).accept(this, entry);
		}
	}

}