package org.ligoj.app.resource.ldap;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.SequenceInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.task.TaskExecutor;
import org.springframework.security.core.context.SecurityContextHolder;

import org.ligoj.bootstrap.core.SpringUtils;
import org.ligoj.bootstrap.core.csv.CsvForBean;
import org.ligoj.bootstrap.core.resource.BusinessException;
import org.ligoj.bootstrap.core.resource.OnNullReturn404;
import org.ligoj.bootstrap.core.validation.ValidatorBean;

/**
 * LDAP batch resource.
 */
public abstract class AbstractBatchResource {

	@Autowired
	protected TaskExecutor executor;

	@Autowired
	private CsvForBean csvForBean;

	/**
	 * Hold pending and previous imports. Key is an identifier built from the user name requesting the import, and a
	 * random String. This table is clean before each import.
	 */
	private final Map<String, BatchTaskVo<?>> imports = new ConcurrentHashMap<>();

	@Autowired
	private ValidatorBean validator;

	/**
	 * Return the import task from its identifier. The internal identifier is build from the current user and the formal
	 * identifier parameter.
	 * 
	 * @param id
	 *            task's identifier.
	 * @return <code>null</code> or corresponding task.
	 */
	@GET
	@Path("{id:\\d+}")
	@OnNullReturn404
	public BatchTaskVo<? extends BatchElement> getImportTask(@PathParam("id") final long id) {
		return imports.get(SecurityContextHolder.getContext().getAuthentication().getName() + "-" + id);
	}

	/**
	 * Return the status of given task
	 * 
	 * @param id
	 *            Identifier of the task.
	 * @return status or <code>null</code> when no task matches.
	 */
	@GET
	@Path("{id:\\d+}/status")
	@OnNullReturn404
	public ImportStatus getImportStatus(@PathParam("id") final long id) {
		return Optional.ofNullable(getImportTask(id)).map(BatchTaskVo::getStatus).orElse(null);
	}

	/**
	 * Cleanup the previous tasks.
	 */
	private void cleanup() {
		for (final Entry<String, BatchTaskVo<?>> entry : imports.entrySet()) {
			if (isFinished(entry.getValue())) {
				// This task is finished since yesterday
				imports.remove(entry.getKey());
			}
		}
	}

	/**
	 * Is the current task is finished.
	 */
	private boolean isFinished(final BatchTaskVo<?> task) {
		return task.getStatus().getEnd() != null && task.getStatus().getEnd().getTime() + DateUtils.MILLIS_PER_DAY < System.currentTimeMillis();
	}

	protected <B extends BatchElement, T extends AbstractLdapBatchTask<B>> long batch(final InputStream uploadedFile, final String[] columns,
			final String encoding, final String[] defaultColumns, final Class<B> batchType, final Class<T> taskType) throws IOException {

		// Public identifier is based on system date
		final long id = System.currentTimeMillis();

		// Check column's name validity
		final String[] sanitizeColumns = ArrayUtils.isEmpty(columns) ? defaultColumns : columns;
		checkHeaders(defaultColumns, sanitizeColumns);

		// Build CSV header from array
		final String csvHeaders = StringUtils.chop(ArrayUtils.toString(sanitizeColumns)).substring(1).replace(',', ';') + "\n";

		// Build entries
		final List<B> entries = csvForBean.toBean(batchType,
				new InputStreamReader(new SequenceInputStream(
						new ByteArrayInputStream(csvHeaders.getBytes(ObjectUtils.defaultIfNull(encoding, StandardCharsets.UTF_8.name()))),
						uploadedFile), ObjectUtils.defaultIfNull(encoding, StandardCharsets.UTF_8.name())));
		entries.removeIf(Objects::isNull);

		// Validate them
		validator.validateCheck(entries);

		// Clone the context for the asynchronous import
		final BatchTaskVo<B> importTask = new BatchTaskVo<>();
		importTask.setEntries(entries);
		importTask.setPrincipal(SecurityContextHolder.getContext().getAuthentication().getName());
		importTask.setId(id);

		// Schedule the import
		final T task = SpringUtils.getBean(taskType);
		task.configure(importTask);
		executor.execute(task);

		// Also cleanup the previous tasks
		cleanup();

		// Expose the task with internal identifier, based on current user PLUS the public identifier
		imports.put(importTask.getPrincipal() + "-" + importTask.getId(), importTask);

		// Return private task identifier
		return id;
	}

	/**
	 * Check column's name validity
	 */
	private void checkHeaders(final String[] requested, final String... columns) {
		for (final String column : columns) {
			if (!ArrayUtils.contains(requested, column.trim())) {
				throw new BusinessException("Invalid header", column);
			}
		}
	}
}
