package io.openems.edge.controller.heatpump.idm;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(//
		name = "Controller Heat-Pump IDM", //
		description = "Steuert eine IDM-Waermepumpe im Normal- oder Manual-Modus.")
public @interface Config {

	@AttributeDefinition(name = "Component-ID", description = "Unique ID of this Component")
	String id() default "ctrlHeatpumpIdm0";

	@AttributeDefinition(name = "Alias", description = "Human-readable name of this Component; defaults to Component-ID")
	String alias() default "";

	@AttributeDefinition(name = "Is enabled?", description = "Is this Component enabled?")
	boolean enabled() default true;

	@AttributeDefinition(name = "Mode", description = "NORMAL: Waermepumpe arbeitet mit ihrer normalen Heizkurve. "
			+ "MANUAL: Der Controller gibt eine feste Soll-Vorlauftemperatur vor.")
	Mode mode() default Mode.NORMAL;

	@AttributeDefinition(name = "Heatpump-ID", description = "ID der IDM-Waermepumpe.")
	String heatpump_id() default "heatpump0";

	@AttributeDefinition(name = "Manual Setpoint [°C]", description = "Feste Soll-Vorlauftemperatur im MANUAL-Modus.")
	int manualSetpoint() default 42;

	String webconsole_configurationFactory_nameHint() default "Controller Heat-Pump IDM [{id}]";
}