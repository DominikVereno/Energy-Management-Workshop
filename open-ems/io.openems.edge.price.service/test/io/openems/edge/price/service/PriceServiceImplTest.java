package io.openems.edge.price.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.util.List;

import org.junit.Test;

import io.openems.edge.common.test.ComponentTest;

public class PriceServiceImplTest {

	private static final String PRICES_RESPONSE = """
			{
				"points": [
					{"start": "2026-08-20T10:00:00+02:00", "end": "2026-08-20T10:15:00+02:00", "price_eur_per_kwh": 0.12, "source": "mock", "zone": "AT"},
					{"start": "2026-08-20T10:15:00+02:00", "end": "2026-08-20T10:30:00+02:00", "price_eur_per_kwh": 0.18, "source": "mock", "zone": "AT"}
				]
			}
			""";

	@Test
	public void testActivateAndDeactivate() throws Exception {
		var sut = new PriceServiceImpl();
		var test = new ComponentTest(sut) //
				.activate(MyConfig.create() //
						.setId("price0") //
						.setBaseUrl("http://127.0.0.1:1") // nothing listens here -> fetch fails fast
						.build());
		test.deactivate();
	}

	@Test
	public void testParsesMatchingPoint() throws Exception {
		var now = Instant.parse("2026-08-20T08:20:00Z"); // 10:20 +02:00 -> second point
		var price = PriceServiceImpl.pickPrice(PriceServiceImpl.parsePoints(PRICES_RESPONSE), now);
		assertEquals(0.18, price, 0.0001);
	}

	@Test
	public void testReturnsNullWhenNoPointCoversNow() throws Exception {
		var now = Instant.parse("2026-08-20T09:00:00Z"); // 11:00 +02:00 -> outside both points
		var price = PriceServiceImpl.pickPrice(PriceServiceImpl.parsePoints(PRICES_RESPONSE), now);
		assertNull(price);
	}

	@Test
	public void testParsesEveryPoint() throws Exception {
		var points = PriceServiceImpl.parsePoints(PRICES_RESPONSE);
		assertEquals(2, points.size());
		assertEquals(0.12, points.get(0).price(), 0.0001);
		assertEquals(Instant.parse("2026-08-20T08:00:00Z"), points.get(0).start());
		assertEquals(Instant.parse("2026-08-20T08:15:00Z"), points.get(0).end());
	}

	/**
	 * The same fetched window must yield different prices as time moves on —
	 * otherwise the published price stays with the slot that was current when the
	 * data was fetched.
	 */
	@Test
	public void testTheSamePointsYieldTheSlotOfTheGivenInstant() throws Exception {
		var points = PriceServiceImpl.parsePoints(PRICES_RESPONSE);
		assertEquals(0.12, PriceServiceImpl.pickPrice(points, Instant.parse("2026-08-20T08:05:00Z")), 0.0001);
		assertEquals(0.18, PriceServiceImpl.pickPrice(points, Instant.parse("2026-08-20T08:20:00Z")), 0.0001);
	}

	@Test
	public void testSlotBoundaryBelongsToTheLaterSlot() throws Exception {
		var points = PriceServiceImpl.parsePoints(PRICES_RESPONSE);
		assertEquals(0.18, PriceServiceImpl.pickPrice(points, Instant.parse("2026-08-20T08:15:00Z")), 0.0001);
	}

	@Test
	public void testEmptyPointsYieldNoPrice() {
		assertNull(PriceServiceImpl.pickPrice(List.of(), Instant.parse("2026-08-20T08:20:00Z")));
	}

	@Test
	public void testBuildUrlContainsQueryParameters() {
		var url = PriceServiceImpl.buildUrl("http://price-service:8200/", "awattar", "AT",
				Instant.parse("2026-08-20T08:00:00Z"));
		assertTrue(url.startsWith("http://price-service:8200/prices?"));
		assertTrue(url.contains("provider=awattar"));
		assertTrue(url.contains("zone=AT"));
		assertTrue(url.contains("timeframe=15min"));
	}

	@Test
	public void testBuildUrlFloorsStartToCurrentHour() {
		// "now" mid-hour: start must be floored to the hour's top, otherwise the
		// price-service drops the current hour and no point covers "now".
		var url = PriceServiceImpl.buildUrl("http://price-service:8200", "awattar", "AT",
				Instant.parse("2026-08-20T08:43:00Z"));
		assertTrue(url.contains("start=2026-08-20T08:00"));
		assertFalse(url.contains("start=2026-08-20T08:43"));
	}

}
