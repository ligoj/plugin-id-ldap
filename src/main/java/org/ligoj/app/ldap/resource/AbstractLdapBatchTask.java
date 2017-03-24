package org.ligoj.app.ldap.resource;

import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.ws.rs.ext.ExceptionMapper;

import org.apache.commons.lang3.StringUtils;
import org.apache.cxf.jaxrs.provider.ServerProviderFactory;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.PhaseInterceptorChain;
import org.ligoj.app.api.Normalizer;
import org.ligoj.bootstrap.core.security.SecurityHelper;
import org.springframework.beans.factory.annotation.Autowired;

import lombok.extern.slf4j.Slf4j;

/**
 * LDAP batch processor.
 * 
 * @param <B>
 *            The batch element type.
 */
@Slf4j
public abstract class AbstractLdapBatchTask<B extends BatchElement> implements Runnable { // NOPMD

	@Autowired
	protected SecurityHelper securityHelper;

	protected ServerProviderFactory jaxrsFactory;

	/**
	 * The import to proceed.
	 */
	protected BatchTaskVo<B> task;

	@Override
	public void run() {
		log.info("Executing " + task.toString());

		// Expose the current user
		securityHelper.setUserName(task.getPrincipal());

		doBatch();

		// The import is completed
		this.task.getStatus().setStatus(Boolean.TRUE);
		this.task.getStatus().setEnd(new Date());
	}

	/**
	 * Process an entry.
	 * 
	 * @param entry
	 *            A batch entry.
	 * @throws Exception
	 *             Any error cause the abortion for this entry.
	 */
	protected abstract void doBatch(B entry) throws Exception; // NOSONAR Allow global error there

	/**
	 * Process the entries
	 */
	private void doBatch() {
		for (final B importEntry : task.getEntries()) {
			// Override previous status
			importEntry.setStatus(null);
			importEntry.setStatusText(null);
			try {
				doBatch(importEntry);

				// Success
				importEntry.setStatus(Boolean.TRUE);
				log.info("Import of {} succeed", importEntry);
			} catch (final Exception ne) {
				// The entry creation failed : entity itself of group membership
				log.info("Import of {} failed : {}", importEntry, ne.getMessage());
				importEntry.setStatus(Boolean.FALSE);
				final ExceptionMapper<Throwable> mapper = jaxrsFactory.createExceptionMapper(ne.getClass(), null);
				importEntry.setStatusText(mapper == null ? ne.getMessage() : mapper.toResponse(ne).getEntity().toString());
			}
			task.getStatus().setDone(task.getStatus().getDone() + 1);
		}
	}

	/**
	 * Configure the task.
	 * 
	 * @param task
	 *            the LDAP batch task.
	 */
	public void configure(final BatchTaskVo<B> task) {
		this.task = task;
		this.task.getStatus().setStart(new Date());
		this.task.getStatus().setEntries(task.getEntries().size());

		// Save the CXF factory for JSON serialization
		this.jaxrsFactory = getMessage() == null ? ServerProviderFactory.getInstance()
				: (ServerProviderFactory) getMessage().getExchange().getEndpoint().get("org.apache.cxf.jaxrs.provider.ServerProviderFactory");
	}

	protected Message getMessage() {
		return PhaseInterceptorChain.getCurrentMessage();
	}

	/**
	 * Split and normalize a string to a collection, ignoring empty items.
	 * 
	 * @param rawValue
	 *            The raw string to split.
	 * @return A collection from the raw string.
	 */
	protected List<String> toList(final String rawValue) {
		return Pattern.compile(",").splitAsStream(StringUtils.trimToEmpty(rawValue)).map(Normalizer::normalize).filter(StringUtils::isNotBlank)
				.collect(Collectors.toList());
	}

}
