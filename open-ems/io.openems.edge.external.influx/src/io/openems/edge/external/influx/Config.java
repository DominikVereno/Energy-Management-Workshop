package io.openems.edge.external.influx;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(//
		name = "External Influx", //
		description = "Reads PV and Grid power from an external InfluxDB.")
public @interface Config {

	@AttributeDefinition(name = "Component-ID", description = "Unique ID of this Component")
	String id() default "externalInflux0";

	@AttributeDefinition(name = "Alias", description = "Human-readable name")
	String alias() default "";

	@AttributeDefinition(name = "Is enabled?")
	boolean enabled() default true;

	@AttributeDefinition(name = "InfluxDB URL")
	String url() default "http://193.170.119.68:8086";

	@AttributeDefinition(name = "Organization")
	String org() default "";

	@AttributeDefinition(name = "API Token")
	String token() default "utxiQyn6zT9RGO2hDKzmvxGCKM3sUgAwJfUDBKTHxPPnOyoCqiWvkPsnJ0kHqicvzQeV7VgJR9a2Fydsi_NCWQ==";

	String webconsole_configurationFactory_nameHint() default "External Influx [{id}]";
}