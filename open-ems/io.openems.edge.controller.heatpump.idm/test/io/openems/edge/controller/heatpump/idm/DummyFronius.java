package io.openems.edge.controller.heatpump.idm;

import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.test.AbstractDummyOpenemsComponent;
import io.openems.edge.electrical.fronius.Fronius;

public class DummyFronius extends AbstractDummyOpenemsComponent<DummyFronius> implements Fronius {

	public DummyFronius(String id) {
		super(id, //
				OpenemsComponent.ChannelId.values(), //
				Fronius.ChannelId.values() //
		);
	}

	@Override
	protected DummyFronius self() {
		return this;
	}

	/**
	 * Sets GRID_POWER, following the Fronius convention: positive means power drawn
	 * from the grid, negative means feed-in.
	 *
	 * @param value the grid power in W, or null for "no data"
	 * @return myself
	 */
	public DummyFronius withGridPower(Integer value) {
		this._setGridPower(value);
		this.getGridPowerChannel().nextProcessImage();
		return this;
	}
}
