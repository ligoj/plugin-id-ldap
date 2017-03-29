package org.ligoj.app.plugin.id.ldap.resource;

import java.util.Collection;
import java.util.Map;

import org.ligoj.app.api.ServicePlugin;
import org.ligoj.app.iam.Activity;
import org.ligoj.app.resource.ActivitiesProvider;

public class SampleActivityProvider implements ActivitiesProvider, ServicePlugin {

	@Override
	public void delete(final int subscription, final boolean deleteRemoteData) throws Exception {
		// Mock
	}

	@Override
	public void create(final int subscription) throws Exception {
		// Mock
	}

	@Override
	public void link(final int subscription) throws Exception {
		// Mock
	}

	@Override
	public String getKey() {
		// Mock
		return "service:bt:jira:6";
	}

	@Override
	public Map<String, Activity> getActivities(final int subscription, final Collection<String> users) throws Exception {
		// Mock
		return null;
	}

}
