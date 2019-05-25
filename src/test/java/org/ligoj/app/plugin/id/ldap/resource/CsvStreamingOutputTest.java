/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.id.ldap.resource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.ligoj.app.iam.Activity;
import org.ligoj.app.iam.UserOrg;
import org.ligoj.bootstrap.core.INamableBean;
import org.ligoj.bootstrap.core.NamedBean;

/**
 * Test class of {@link CsvStreamingOutput}
 */
class CsvStreamingOutputTest {

	@Test
	void write() throws IOException {
		final ActivitiesComputations computations = new ActivitiesComputations();

		// Nodes
		final Collection<INamableBean<String>> nodes = new ArrayList<>();
		final NamedBean<String> node1 = new NamedBean<>();
		nodes.add(node1);
		node1.setId("J0");
		node1.setName("J0N");
		final NamedBean<String> node2 = new NamedBean<>();
		nodes.add(node2);
		node2.setId("J1");
		node2.setName("J1N");
		computations.setNodes(nodes);

		// Activities
		final Map<String, Map<String, Activity>> activities = new HashMap<>();
		final Map<String, Activity> userActivities = new HashMap<>();
		activities.put("U0", userActivities);
		final Activity activity1 = new Activity();
		activity1.setLastConnection(new Date(0));
		userActivities.put("J0", activity1);
		computations.setActivities(activities);

		// Users
		final Collection<UserOrg> users = new ArrayList<>();
		final UserOrg user = new UserOrg();
		user.setFirstName("F0");
		user.setLastName("L0");
		user.setId("U0");
		user.setMails(new ArrayList<>());
		users.add(user);
		final UserOrg user2 = new UserOrg();
		user2.setFirstName("F1");
		user2.setLastName("L1");
		user2.setId("U1");
		user2.setMails(Arrays.asList("M1", "M2"));
		users.add(user2);
		computations.setUsers(users);

		// Call
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		new CsvStreamingOutput(computations).write(out);
		final List<String> lines = IOUtils.readLines(new ByteArrayInputStream(out.toByteArray()), StandardCharsets.UTF_8);

		// Check
		Assertions.assertEquals(3, lines.size());
		Assertions.assertEquals("user;firstName;lastName;mail;J0N;J1N", lines.get(0));
		Assertions.assertEquals("U0;F0;L0;;1970/01/01 01:00:00;", lines.get(1));
		Assertions.assertEquals("U1;F1;L1;M1", lines.get(2));

	}
}
