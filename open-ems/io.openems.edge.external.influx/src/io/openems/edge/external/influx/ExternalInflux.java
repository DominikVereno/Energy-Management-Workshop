package io.openems.edge.external.influx;

import io.openems.common.channel.Unit;
import io.openems.common.types.OpenemsType;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.component.OpenemsComponent;

public interface ExternalInflux extends OpenemsComponent {

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {

		PV_POWER(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.WATT) //
				.text("PV Power")), //

		GRID_POWER(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.WATT) //
				.text("Grid Power")), //
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

	public default void _setPvPower(Integer value) {
		this.channel(ChannelId.PV_POWER).setNextValue(value);
	}

	public default void _setGridPower(Integer value) {
		this.channel(ChannelId.GRID_POWER).setNextValue(value);
	}
}