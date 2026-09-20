package io.openems.edge.controller.heatpump.idm;

import io.openems.common.channel.Unit;
import io.openems.common.types.OpenemsType;
import io.openems.edge.common.channel.Channel;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.channel.value.Value;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.controller.api.Controller;

public interface ControllerHeatpumpIdm extends Controller, OpenemsComponent {

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {

		ACTIVE_MODE(Doc.of(Mode.values()) //
				.text("Currently active mode")), //

		APPLIED_SETPOINT(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.DEGREE_CELSIUS) //
				.text("Applied flow temperature setpoint in MANUAL mode")), //
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

	public default Channel<Mode> getActiveModeChannel() {
		return this.channel(ChannelId.ACTIVE_MODE);
	}

	public default void _setActiveMode(Mode value) {
		this.getActiveModeChannel().setNextValue(value);
	}

	public default Channel<Integer> getAppliedSetpointChannel() {
		return this.channel(ChannelId.APPLIED_SETPOINT);
	}

	public default Value<Integer> getAppliedSetpoint() {
		return this.getAppliedSetpointChannel().value();
	}

	public default void _setAppliedSetpoint(Integer value) {
		this.getAppliedSetpointChannel().setNextValue(value);
	}
}