/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.id.dao;

import jakarta.annotation.Priority;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.ligoj.app.iam.CompanyOrg;
import org.ligoj.app.iam.GroupOrg;
import org.ligoj.app.iam.UserOrg;
import org.springframework.stereotype.Repository;

import java.util.Map;

/**
 * Cache synchronization from SQL cache to database without internal new transaction.
 */
@Transactional
@Repository
@Slf4j
@Priority(2)
public class IdCacheDaoNoTransaction extends IdCacheDaoImpl {

	@Override
	@Transactional(Transactional.TxType.REQUIRED)
	public void reset(final Map<String, CompanyOrg> companies, final Map<String, GroupOrg> groups,
			final Map<String, UserOrg> users) {
		super.reset(companies, groups, users);
	}
}
