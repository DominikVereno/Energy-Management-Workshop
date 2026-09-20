package io.openems.edge.price.service;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(//
		name = "Price Service (read-only)", //
		description = "Reads the current electricity price from the price-service HTTP API. Read-only.")
@interface Config {

	@AttributeDefinition(name = "Component-ID", description = "Unique ID of this Component")
	String id() default "price0";

	@AttributeDefinition(name = "Alias", description = "Human-readable name of this Component; defaults to Component-ID")
	String alias() default "";

	@AttributeDefinition(name = "Is enabled?", description = "Is this Component enabled?")
	boolean enabled() default true;

	@AttributeDefinition(name = "Base URL", description = "Base URL of the price-service, e.g. http://price-service:8200")
	String baseUrl() default "http://price-service:8200";

	@AttributeDefinition(name = "Provider", description = "price-service provider, e.g. awattar, entsoe")
	String provider() default "awattar";

	@AttributeDefinition(name = "Zone", description = "Market zone, e.g. AT")
	String zone() default "AT";

	@AttributeDefinition(name = "Poll interval [min]", description = "Minutes between two price fetches from the price-service")
	int pollIntervalMinutes() default 15;

	String webconsole_configurationFactory_nameHint() default "Price Service [{id}]";

}
