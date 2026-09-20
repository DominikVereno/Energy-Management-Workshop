package io.openems.edge.controller.heatpump.idm;

import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.test.AbstractDummyOpenemsComponent;
import io.openems.edge.price.service.PriceService;

public class DummyPriceService extends AbstractDummyOpenemsComponent<DummyPriceService> implements PriceService {

	public DummyPriceService(String id) {
		super(id, //
				OpenemsComponent.ChannelId.values(), //
				PriceService.ChannelId.values() //
		);
	}

	@Override
	protected DummyPriceService self() {
		return this;
	}

	/**
	 * Sets CURRENT_PRICE.
	 *
	 * @param value the price in EUR/kWh, or null for "no data"
	 * @return myself
	 */
	public DummyPriceService withCurrentPrice(Float value) {
		this._setCurrentPrice(value);
		this.getCurrentPriceChannel().nextProcessImage();
		return this;
	}
}
