package io.openems.edge.electrical.fronius;

import io.openems.common.channel.Level;
import io.openems.common.channel.Unit;
import io.openems.common.types.OpenemsType;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.channel.IntegerReadChannel;
import io.openems.edge.common.channel.StateChannel;
import io.openems.edge.common.channel.StringReadChannel;
import io.openems.edge.common.channel.value.Value;
import io.openems.edge.common.component.OpenemsComponent;

public interface Fronius extends OpenemsComponent {

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {

		BATTERY_POWER(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.WATT)),

		GRID_POWER(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.WATT)),

		LOAD_POWER(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.WATT)),

		PV_POWER(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.WATT)),

		SOC(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.PERCENT)),

		BATTERY_MODE(Doc.of(OpenemsType.STRING)),

		COMMUNICATION_FAILED(Doc.of(Level.FAULT) //
				.text("Communication with the Fronius Solar API failed"));

		private final Doc doc;

		ChannelId(Doc doc) {
			this.doc = doc;
		}

		@Override
		public Doc doc() {
			return this.doc;
		}
	}

	default IntegerReadChannel getBatteryPowerChannel() {
		return this.channel(ChannelId.BATTERY_POWER);
	}

	default Value<Integer> getBatteryPower() {
		return this.getBatteryPowerChannel().value();
	}

	default void _setBatteryPower(Integer value) {
		this.getBatteryPowerChannel().setNextValue(value);
	}

	default IntegerReadChannel getGridPowerChannel() {
		return this.channel(ChannelId.GRID_POWER);
	}

	default Value<Integer> getGridPower() {
		return this.getGridPowerChannel().value();
	}

	default void _setGridPower(Integer value) {
		this.getGridPowerChannel().setNextValue(value);
	}

	default IntegerReadChannel getLoadPowerChannel() {
		return this.channel(ChannelId.LOAD_POWER);
	}

	default Value<Integer> getLoadPower() {
		return this.getLoadPowerChannel().value();
	}

	default void _setLoadPower(Integer value) {
		this.getLoadPowerChannel().setNextValue(value);
	}

	default IntegerReadChannel getPvPowerChannel() {
		return this.channel(ChannelId.PV_POWER);
	}

	default Value<Integer> getPvPower() {
		return this.getPvPowerChannel().value();
	}

	default void _setPvPower(Integer value) {
		this.getPvPowerChannel().setNextValue(value);
	}

	default IntegerReadChannel getSocChannel() {
		return this.channel(ChannelId.SOC);
	}

	default Value<Integer> getSoc() {
		return this.getSocChannel().value();
	}

	default void _setSoc(Integer value) {
		this.getSocChannel().setNextValue(value);
	}

	default StringReadChannel getBatteryModeChannel() {
		return this.channel(ChannelId.BATTERY_MODE);
	}

	default Value<String> getBatteryMode() {
		return this.getBatteryModeChannel().value();
	}

	default void _setBatteryMode(String value) {
		this.getBatteryModeChannel().setNextValue(value);
	}

	default StateChannel getCommunicationFailedChannel() {
		return this.channel(ChannelId.COMMUNICATION_FAILED);
	}

	default void _setCommunicationFailed(boolean value) {
		this.getCommunicationFailedChannel().setNextValue(value);
	}

}
