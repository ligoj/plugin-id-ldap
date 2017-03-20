package org.ligoj.app.ldap.resource;

import javax.validation.constraints.NotNull;

import org.hibernate.validator.constraints.NotBlank;

import org.ligoj.bootstrap.core.NamedBean;
import org.ligoj.app.model.DelegateLdapType;
import org.ligoj.app.model.ReceiverType;

import lombok.Getter;
import lombok.Setter;

/**
 * LDAP delegation business object for updates. The name corresponds to the CN of target. It will be normalized before
 * the match to a real LDAP CN.
 */
@Getter
@Setter
public class DelegateLdapEditionVo extends NamedBean<Integer> {

	/**
	 * The people receiving the delegation.
	 */
	@NotBlank
	@NotNull
	private String receiver;

	/**
	 * The type of people receiving this delegate.
	 */
	private ReceiverType receiverType = ReceiverType.USER;

	/**
	 * The delegate type.
	 */
	@NotNull
	private DelegateLdapType type;

	/**
	 * Can write with this delegate.
	 */
	private boolean canWrite;

	/**
	 * Can manage all delegation within the same scope.
	 */
	private boolean canAdmin;

}
