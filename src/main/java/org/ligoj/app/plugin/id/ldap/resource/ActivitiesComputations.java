/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.id.ldap.resource;

import java.util.Collection;
import java.util.Map;

import org.ligoj.app.iam.Activity;
import org.ligoj.app.iam.UserOrg;
import org.ligoj.bootstrap.core.INamableBean;

import lombok.Getter;
import lombok.Setter;

/**
 * Activities computation data model.
 */
@Getter
@Setter
public class ActivitiesComputations {

	/**
	 * User identifiers
	 */
	private Collection<UserOrg> users;

	/**
	 * Unique related nodes
	 */
	private Collection<INamableBean<String>> nodes;

	/**
	 * Activities of each user. K = user's login, V = activities, where K = node's identifier, and V is the activity.
	 */
	private Map<String, Map<String, Activity>> activities;

}
