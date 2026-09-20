package io.openems.edge.price.service;

import java.time.Instant;

/**
 * One price slot as delivered by the price-service: a price in EUR/kWh and the
 * half-open interval {@code [start, end)} it is valid for.
 */
public record PricePoint(Instant start, Instant end, double price) {

	/**
	 * Checks whether this slot covers the given instant.
	 *
	 * @param instant the instant to check
	 * @return true if the instant falls into {@code [start, end)}
	 */
	public boolean covers(Instant instant) {
		return !instant.isBefore(this.start) && instant.isBefore(this.end);
	}
}
