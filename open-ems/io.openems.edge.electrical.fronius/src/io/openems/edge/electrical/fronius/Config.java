package io.openems.edge.electrical.fronius;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(//
		name = "Electrical Fronius (read-only)", //
		description = "Reads the power flow of a Fronius inverter via its Solar API. Read-only.")
@interface Config {

	@AttributeDefinition(name = "Component-ID", description = "Unique ID of this Component")
	String id() default "fronius0";

	@AttributeDefinition(name = "Alias", description = "Human-readable name of this Component; defaults to Component-ID")
	String alias() default "";

	@AttributeDefinition(name = "Is enabled?", description = "Is this Component enabled?")
	boolean enabled() default true;

	@AttributeDefinition(name = "Base URL", description = "Base URL of the Fronius Solar API, e.g. http://192.168.1.50")
	String baseUrl() default "http://192.168.1.50";

	@AttributeDefinition(name = "Read every n cycles", description = "Number of cycles between two GET requests; 1 means every cycle")
	int cycles() default 1;

	String webconsole_configurationFactory_nameHint() default "Electrical Fronius [{id}]";

}
