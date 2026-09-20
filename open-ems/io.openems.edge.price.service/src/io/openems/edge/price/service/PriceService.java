package io.openems.edge.price.service;

import io.openems.common.channel.Level;
import io.openems.common.types.OpenemsType;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.channel.FloatReadChannel;
import io.openems.edge.common.channel.StateChannel;
import io.openems.edge.common.channel.value.Value;
import io.openems.edge.common.component.OpenemsComponent;

public interface PriceService extends OpenemsComponent {

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {

		CURRENT_PRICE(Doc.of(OpenemsType.FLOAT) //
				.text("Aktueller Strompreis in EUR/kWh, geliefert vom price-service")), //

		COMMUNICATION_FAILED(Doc.of(Level.FAULT) //
				.text("Communication with the price-service failed")), //

		NO_PRICE_FOR_CURRENT_TIME(Doc.of(Level.WARNING) //
				.text("Der price-service antwortet, aber keiner der gelieferten Zeitpunkte deckt den "
						+ "aktuellen Zeitpunkt ab"));

		private final Doc doc;

		ChannelId(Doc doc) {
			this.doc = doc;
		}

		@Override
		public Doc doc() {
			return this.doc;
		}
	}

	default FloatReadChannel getCurrentPriceChannel() {
		return this.channel(ChannelId.CURRENT_PRICE);
	}

	default Value<Float> getCurrentPrice() {
		return this.getCurrentPriceChannel().value();
	}

	default void _setCurrentPrice(Float value) {
		this.getCurrentPriceChannel().setNextValue(value);
	}

	default StateChannel getCommunicationFailedChannel() {
		return this.channel(ChannelId.COMMUNICATION_FAILED);
	}

	default void _setCommunicationFailed(boolean value) {
		this.getCommunicationFailedChannel().setNextValue(value);
	}

	default StateChannel getNoPriceForCurrentTimeChannel() {
		return this.channel(ChannelId.NO_PRICE_FOR_CURRENT_TIME);
	}

	default void _setNoPriceForCurrentTime(boolean value) {
		this.getNoPriceForCurrentTimeChannel().setNextValue(value);
	}

}
