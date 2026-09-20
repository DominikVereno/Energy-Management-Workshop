package io.openems.edge.electrical.fronius;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import io.openems.common.bridge.http.api.HttpError;
import io.openems.common.bridge.http.api.HttpResponse;
import io.openems.common.bridge.http.dummy.DummyBridgeHttpBundle;
import io.openems.edge.bridge.http.cycle.HttpBridgeCycleServiceDefinition;
import io.openems.edge.bridge.http.cycle.dummy.DummyCycleSubscriber;
import io.openems.edge.common.test.AbstractComponentTest.TestCase;
import io.openems.edge.common.test.ComponentTest;

public class FroniusImplTest {

	private static final String POWER_FLOW_RESPONSE = """
			{
				"Head": { "Status": { "Code": 0 } },
				"Body": { "Data": {
					"Site": {
						"P_Akku": -1234.5,
						"P_Grid": 678.9,
						"P_Load": -2000.0,
						"P_PV": 3210.4
					},
					"Inverters": { "1": {
						"SOC": 87.6,
						"Battery_Mode": "normal"
					}}
				}}
			}
			""";

	private static final String POWER_FLOW_RESPONSE_STATUS_ERROR = """
			{
				"Head": { "Status": { "Code": 255 } },
				"Body": { "Data": {} }
			}
			""";

	private final DummyBridgeHttpBundle bridge = DummyBridgeHttpBundle.of();
	private final DummyCycleSubscriber cycleSubscriber = new DummyCycleSubscriber();
	private final FroniusImpl sut = new FroniusImpl();

	private ComponentTest activate() throws Exception {
		return new ComponentTest(this.sut) //
				.addReference("httpBridgeFactory", this.bridge.factory()) //
				.addReference("httpBridgeCycleServiceDefinition",
						new HttpBridgeCycleServiceDefinition(this.cycleSubscriber)) //
				.activate(MyConfig.create() //
						.setId("fronius0") //
						.setBaseUrl("http://127.0.0.1/") //
						.build());
	}

	private void fetch(ComponentTest test) throws Exception {
		this.cycleSubscriber.triggerNextCycle();
		this.bridge.runTasksImmediately();
		test.next(new TestCase());
	}

	@Test
	public void testReadsPowerFlow() throws Exception {
		var test = this.activate();

		this.bridge.forceNextSuccessfulResult(HttpResponse.ok(POWER_FLOW_RESPONSE));
		this.fetch(test);

		assertEquals(Integer.valueOf(-1234), this.sut.getBatteryPower().get());
		assertEquals(Integer.valueOf(679), this.sut.getGridPower().get());
		assertEquals(Integer.valueOf(-2000), this.sut.getLoadPower().get());
		assertEquals(Integer.valueOf(3210), this.sut.getPvPower().get());
		assertEquals(Integer.valueOf(88), this.sut.getSoc().get());
		assertEquals("normal", this.sut.getBatteryMode().get());
		assertEquals(Boolean.FALSE, this.sut.getCommunicationFailedChannel().value().get());

		test.deactivate();
	}

	@Test
	public void testClearsValuesOnHttpError() throws Exception {
		var test = this.activate();

		this.bridge.forceNextSuccessfulResult(HttpResponse.ok(POWER_FLOW_RESPONSE));
		this.fetch(test);
		assertEquals(Integer.valueOf(679), this.sut.getGridPower().get());

		this.bridge.forceNextFailedResult(new HttpError.UnknownError(new RuntimeException("no route to host")));
		this.fetch(test);

		this.assertNoStaleValues();

		test.deactivate();
	}

	@Test
	public void testClearsValuesOnApiStatusError() throws Exception {
		var test = this.activate();

		this.bridge.forceNextSuccessfulResult(HttpResponse.ok(POWER_FLOW_RESPONSE));
		this.fetch(test);
		assertEquals(Integer.valueOf(679), this.sut.getGridPower().get());

		this.bridge.forceNextSuccessfulResult(HttpResponse.ok(POWER_FLOW_RESPONSE_STATUS_ERROR));
		this.fetch(test);

		this.assertNoStaleValues();

		test.deactivate();
	}

	private void assertNoStaleValues() {
		assertTrue(this.sut.getCommunicationFailedChannel().value().get());
		assertNull(this.sut.getBatteryPower().get());
		assertNull(this.sut.getGridPower().get());
		assertNull(this.sut.getLoadPower().get());
		assertNull(this.sut.getPvPower().get());
		assertNull(this.sut.getSoc().get());
		assertNull(this.sut.getBatteryMode().get());
	}

}
