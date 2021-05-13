/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.id.ldap.resource;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.text.Format;
import java.util.Map;

import javax.ws.rs.core.StreamingOutput;

import org.apache.commons.lang3.time.FastDateFormat;
import org.ligoj.app.iam.Activity;
import org.ligoj.app.iam.UserOrg;

/**
 * CSV output writer for user activities.
 */
public class CsvStreamingOutput implements StreamingOutput {

	protected final ActivitiesComputations computations;

	/**
	 * Constructor for database offline data.
	 * 
	 * @param computations Activities computations with issues.
	 */
	public CsvStreamingOutput(final ActivitiesComputations computations) {
		this.computations = computations;
	}

	@Override
	public void write(final OutputStream output) throws IOException {
		final var writer = new BufferedWriter(new OutputStreamWriter(output, "cp1252"));
		final var df = FastDateFormat.getInstance("yyyy/MM/dd HH:mm:ss");

		// Write headers
		writeHeaders(writer);

		// Write data
		writeData(writer, df);
		writer.flush();
	}

	/**
	 * Write CSV header. Ends with new line.
	 */
	private void writeHeaders(final Writer writer) throws IOException {
		writeNonStandardHeaders(writer);

		// Write Node headers
		writeNodeHeaders(writer);

		writer.write('\n');
	}

	private void writeNonStandardHeaders(final Writer writer) throws IOException {
		// Write static headers
		writer.write("user;firstName;lastName;mail");
	}

	/**
	 * Write headers.
	 */
	private void writeNodeHeaders(final Writer writer) throws IOException {
		// Iterate over audited nodes
		for (final var node : computations.getNodes()) {
			writer.write(";");
			writer.write(node.getName());
		}
	}

	/**
	 * Write activities data. Ends with new line.
	 */
	private void writeData(final Writer writer, final Format df) throws IOException {
		for (final var user : computations.getUsers()) {
			// Write user data
			writeUserData(writer, user);

			// Write activities of this user
			final var activities = computations.getActivities().get(user.getId());
			if (activities != null) {
				// At least one activity for this user
				writeNodeActivities(activities, writer, df);
			}
			writer.write('\n');
		}
	}

	/**
	 * Write user data
	 */
	private void writeUserData(final Writer writer, final UserOrg user) throws IOException {
		writer.write(user.getId());
		writer.write(";");
		writer.write(user.getFirstName());
		writer.write(";");
		writer.write(user.getLastName());
		writer.write(";");
		writer.write(user.getMails().isEmpty() ? "" : user.getMails().get(0));
	}

	/**
	 * Write user's activities
	 */
	private void writeNodeActivities(final Map<String, Activity> activities, final Writer writer, final Format df)
			throws IOException {
		for (final var node : computations.getNodes()) {
			writer.write(';');

			// Last connection, if available
			final var activity = activities.get(node.getId());
			if (activity != null) {
				// There is an activity for this user and this node
				writer.write(df.format(activity.getLastConnection()));
			}
		}
	}

}
