/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.ldap.dao;

import java.time.Instant;

import jakarta.annotation.Priority;
import jakarta.transaction.Transactional;

import org.ligoj.app.plugin.id.dao.IdCopy;
import org.springframework.stereotype.Repository;

/**
 * {@link IdCopy} writing in the test transaction, rolled back after each test: the test reset holds the locks of the
 * copy state, a new transaction would wait for them forever.
 */
@Transactional
@Repository
@Priority(2)
public class IdCopyNoTransaction extends IdCopy {

	@Override
	@Transactional(Transactional.TxType.REQUIRED)
	public void saveState(final String node, final String fingerprint, final Instant lastFullRead) {
		super.saveState(node, fingerprint, lastFullRead);
	}
}
