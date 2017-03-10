package org.ligoj.app.resource.ldap;

import java.io.IOException;
import java.io.InputStream;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.apache.cxf.jaxrs.ext.multipart.Multipart;
import org.springframework.stereotype.Service;

/**
 * LDAP batch resource for group.
 */
@Path("/ldap/group/batch")
@Service
@Produces(MediaType.APPLICATION_JSON)
public class GroupBatchLdapResource extends AbstractBatchResource {

	/**
	 * Default CSV headers for imports.
	 */
	private static final String[] DEFAULT_IMPORT_CSV_HEADERS = { "name", "type", "parent", "owner", "assistant", "department" };

	/**
	 * Upload a file of LDAP entries to create or update groups. The whole entry is replaced.
	 * 
	 * @param uploadedFile
	 *            LDAP entries files to import. Currently support only CSV format.
	 * @param columns
	 *            the CSV header names.
	 * @param encoding
	 *            CSV encoding. Default is UTF-8.
	 * @return the import identifier.
	 */
	@POST
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	@Path("full")
	public long full(@Multipart(value = "csv-file") final InputStream uploadedFile,
			@Multipart(value = "columns", required = false) final String[] columns,
			@Multipart(value = "encoding", required = false) final String encoding) throws IOException {
		return batch(uploadedFile, columns, encoding, DEFAULT_IMPORT_CSV_HEADERS, GroupImportEntry.class, GroupFullLdapTask.class);
	}
}
