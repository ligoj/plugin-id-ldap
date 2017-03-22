package org.ligoj.app.ldap.resource;

import org.ligoj.app.api.ContainerOrg;

public class Main {

	public static void main(final String[] args) {
		java.util.regex.Pattern.compile(ContainerOrg.NAME_PATTERN).matcher("New-Ax-1-zZ").matches();

	}

}
