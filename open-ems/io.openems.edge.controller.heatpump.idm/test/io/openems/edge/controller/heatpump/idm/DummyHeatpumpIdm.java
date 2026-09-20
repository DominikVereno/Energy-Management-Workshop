package io.openems.edge.controller.heatpump.idm;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.edge.common.channel.Channel;
import io.openems.edge.common.channel.FloatWriteChannel;
import io.openems.edge.common.channel.IntegerWriteChannel;
import io.openems.edge.common.channel.WriteChannel;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.test.AbstractDummyOpenemsComponent;
import io.openems.edge.heatpump.idm.HeatpumpIdm;

public class DummyHeatpumpIdm extends AbstractDummyOpenemsComponent<DummyHeatpumpIdm> implements HeatpumpIdm {

	public DummyHeatpumpIdm(String id) {
		super(id, //
				OpenemsComponent.ChannelId.values(), //
				HeatpumpIdm.ChannelId.values() //
		);
	}

	@Override
	protected DummyHeatpumpIdm self() {
		return this;
	}

	/**
	 * Reads the value a Controller has written to the given register in this cycle.
	 *
	 * @param channelId the register Channel-ID
	 * @return the written value, or null if nothing was written
	 */
	public Integer getWriteValue(HeatpumpIdm.ChannelId channelId) {
		IntegerWriteChannel channel = this.channel(channelId);
		return channel.getNextWriteValue().orElse(null);
	}

	/**
	 * Reads the value a Controller has written to the given FLOAT register in this
	 * cycle.
	 *
	 * @param channelId the register Channel-ID
	 * @return the written value, or null if nothing was written
	 */
	public Float getFloatWriteValue(HeatpumpIdm.ChannelId channelId) {
		FloatWriteChannel channel = this.channel(channelId);
		return channel.getNextWriteValue().orElse(null);
	}

	/**
	 * Pins the value the plant reports back for the given register, as the Modbus
	 * bridge would after reading it.
	 *
	 * @param channelId the register Channel-ID
	 * @param value     the value the plant reports
	 * @return this
	 */
	public DummyHeatpumpIdm withRegister(HeatpumpIdm.ChannelId channelId, Object value) {
		Channel<?> channel = this.channel(channelId);
		channel.setNextValue(value);
		channel.nextProcessImage();
		return this;
	}

	/**
	 * Drops the value a Controller has written, so a following cycle shows whether
	 * the register is written again.
	 *
	 * @param channelId the register Channel-ID
	 * @throws OpenemsNamedException on error
	 */
	public void clearWriteValue(HeatpumpIdm.ChannelId channelId) throws OpenemsNamedException {
		WriteChannel<?> channel = this.channel(channelId);
		channel.setNextWriteValueFromObject(null);
	}
}
