package io.openems.edge.heatpump.idm;

import io.openems.common.channel.AccessMode;
import io.openems.common.types.OpenemsType;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.component.OpenemsComponent;

public interface HeatpumpIdm extends OpenemsComponent {

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {

		OUTDOOR_TEMPERATURE(Doc.of(OpenemsType.FLOAT) //
				.accessMode(AccessMode.READ_ONLY) //
				.text("Außentemperatur (B32) [°C]")), //

		FLOW_TEMPERATURE(Doc.of(OpenemsType.FLOAT) //
				.accessMode(AccessMode.READ_ONLY) //
				.text("Wärmepumpen Vorlauftemperatur (B33) [°C]")), //

		RETURN_TEMPERATURE(Doc.of(OpenemsType.FLOAT) //
				.accessMode(AccessMode.READ_ONLY) //
				.text("Wärmepumpen Rücklauftemperatur (B34) [°C]")), //

		ROOM_TEMPERATURE(Doc.of(OpenemsType.FLOAT) //
				.accessMode(AccessMode.READ_ONLY) //
				.text("Heizkreis A Raumtemperatur (B61) [°C]")), //

		HEATING_CURVE_FLOW_TEMPERATURE_SETPOINT(Doc.of(OpenemsType.FLOAT) //
				.accessMode(AccessMode.READ_ONLY) //
				.text("Sollvorlauftemperatur Heizkurve HK A [°C]")), //

		OPERATION_MODE(Doc.of(OpenemsType.INTEGER) //
				.accessMode(AccessMode.READ_WRITE) //
				.text("Betriebsart Heizkreis A") //
				.onInit(RegisterRange.limit("Betriebsart Heizkreis A", 0.0, 5.0))), //


		HEAT_DEMAND(Doc.of(OpenemsType.INTEGER) //
				.accessMode(AccessMode.READ_WRITE) //
				.text("Wärmeanforderung") //
				.onInit(RegisterRange.limit("Wärmeanforderung", 0.0, 1.0))), //

		ELECTRICAL_POWER_CONSUMPTION(Doc.of(OpenemsType.FLOAT) //
				.accessMode(AccessMode.READ_ONLY) //
				.text("Aktuelle Leistungsaufnahme Wärmepumpe [kW]")), //

		THERMAL_POWER(Doc.of(OpenemsType.FLOAT) //
				.accessMode(AccessMode.READ_ONLY) //
				.text("Thermische Leistung [kW]")), //
		;

		private final Doc doc;

		private ChannelId(Doc doc) {
			this.doc = doc;
		}

		@Override
		public Doc doc() {
			return this.doc;
		}
	}
}