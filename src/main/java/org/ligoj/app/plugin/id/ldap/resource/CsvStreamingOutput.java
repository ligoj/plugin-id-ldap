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
import org.ligoj.app.api.Activity;
import org.ligoj.app.api.UserOrg;
import org.ligoj.bootstrap.core.IDescribableBean;

/**
 * CSV output writer for user activities.
 */
public class CsvStreamingOutput implements StreamingOutput {

	protected final ActivitiesComputations computations;

	/**
	 * Constructor for database offline data.
	 * 
	 * @param computations
	 *            Activities computations with issues.
	 */
	public CsvStreamingOutput(final ActivitiesComputations computations) {
		this.computations = computations;
	}

	@Override
	public void write(final OutputStream output) throws IOException {
		final Writer writer = new BufferedWriter(new OutputStreamWriter(output, "cp1252"));
		final FastDateFormat df = FastDateFormat.getInstance("yyyy/MM/dd HH:mm:ss");

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

	protected void writeNonStandardHeaders(final Writer writer) throws IOException {
		// Write static headers
		writer.write("user;firstName;lastName;mail");
	}

	/**
	 * Write headers
	 */
	protected void writeNodeHeaders(final Writer writer) throws IOException {
		// Iterate over audited nodes
		for (final IDescribableBean<String> node : computations.getNodes()) {
			writer.write(";");
			writer.write(node.getName());
		}
	}

	/**
	 * Write activities data. Ends with new line.
	 */
	protected void writeData(final Writer writer, final Format df) throws IOException {
		for (final UserOrg user : computations.getUsers()) {
			// Write user data
			writeUserData(writer, user);

			// Write activities of this user
			final Map<String, Activity> activities = computations.getActivities().get(user.getId());
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
	protected void writeNodeActivities(final Map<String, Activity> activities, final Writer writer, final Format df) throws IOException {
		for (final IDescribableBean<String> node : computations.getNodes()) {
			writer.write(';');

			// Last connection, if available
			final Activity activity = activities.get(node.getId());
			if (activity != null) {
				// There is an activity for this user and this node
				writer.write(df.format(activity.getLastConnection()));
			}
		}
	}

}
