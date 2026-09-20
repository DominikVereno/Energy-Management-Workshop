package io.openems.edge.controller.heatpump.idm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;

import io.openems.common.test.TimeLeapClock;
import io.openems.edge.common.test.AbstractComponentTest.TestCase;
import io.openems.edge.common.test.DummyComponentManager;
import io.openems.edge.controller.test.ControllerTest;
import io.openems.edge.heatpump.idm.HeatpumpIdm;

public class ControllerHeatpumpIdmImplTest {

	private static final String CTRL_ID = "ctrlHeatpumpIdm0";
	private static final String HEATPUMP_ID = "heatpump0";
	private static final String ELECTRICAL_ID = "fronius0";
	private static final String PRICE_ID = "price0";

	private static TimeLeapClock clock() {
		return new TimeLeapClock(Instant.parse("2026-08-13T12:00:00.00Z"), ZoneOffset.UTC);
	}

	private static ControllerTest test(ControllerHeatpumpIdmImpl controller, DummyHeatpumpIdm heatpump,
			DummyFronius electrical) throws Exception {
		return test(controller, heatpump, electrical, clock());
	}

	private static ControllerTest test(ControllerHeatpumpIdmImpl controller, DummyHeatpumpIdm heatpump,
			DummyFronius electrical, TimeLeapClock clock) throws Exception {
		return new ControllerTest(controller) //
				.addReference("componentManager", new DummyComponentManager(clock)) //
				.addReference("heatpump", heatpump) //
				.addReference("electrical", electrical);
	}

	private static ControllerTest priceTest(ControllerHeatpumpIdmImpl controller, DummyHeatpumpIdm heatpump,
			DummyPriceService price) throws Exception {
		return priceTest(controller, heatpump, price, clock());
	}

	private static ControllerTest priceTest(ControllerHeatpumpIdmImpl controller, DummyHeatpumpIdm heatpump,
			DummyPriceService price, TimeLeapClock clock) throws Exception {
		return new ControllerTest(controller) //
				.addReference("componentManager", new DummyComponentManager(clock)) //
				.addReference("heatpump", heatpump) //
				.addReference("electrical", new DummyFronius(ELECTRICAL_ID)) //
				.addReference("price", price);
	}

	@Test
	public void manualWritesConfiguredSetpoints() throws Exception {
		var heatpump = new DummyHeatpumpIdm(HEATPUMP_ID);

		test(new ControllerHeatpumpIdmImpl(), heatpump, new DummyFronius(ELECTRICAL_ID)) //
				.activate(MyConfig.create() //
						.setId(CTRL_ID) //
						.setMode(Mode.MANUAL) //
						.setManualHeating(true) //
						.setManualCooling(false) //
						.setManualHeatingSetpoint(47) //
						.setManualCoolingSetpoint(16) //
						.build()) //
				.next(new TestCase());

		assertEquals(1, heatpump.getWriteValue(HeatpumpIdm.ChannelId.REG_1710));
		assertEquals(0, heatpump.getWriteValue(HeatpumpIdm.ChannelId.REG_1711));
		assertEquals(47, heatpump.getWriteValue(HeatpumpIdm.ChannelId.REG_1449));
		assertEquals(16, heatpump.getWriteValue(HeatpumpIdm.ChannelId.REG_1491));
	}

	@Test
	public void manualLeavesHeatpumpInStandbyByDefault() throws Exception {
		var heatpump = new DummyHeatpumpIdm(HEATPUMP_ID);

		test(new ControllerHeatpumpIdmImpl(), heatpump, new DummyFronius(ELECTRICAL_ID)) //
				.activate(MyConfig.create() //
						.setId(CTRL_ID) //
						.setMode(Mode.MANUAL) //
						.build()) //
				.next(new TestCase());

		assertEquals(0, heatpump.getWriteValue(HeatpumpIdm.ChannelId.REG_1710));
		assertEquals(0, heatpump.getWriteValue(HeatpumpIdm.ChannelId.REG_1711));
	}

	@Test
	public void pvBoostsAboveThreshold() throws Exception {
		var heatpump = new DummyHeatpumpIdm(HEATPUMP_ID);
		var electrical = new DummyFronius(ELECTRICAL_ID).withGridPower(-2500);

		test(new ControllerHeatpumpIdmImpl(), heatpump, electrical) //
				.activate(pvConfig().build()) //
				.next(new TestCase());

		assertEquals(24f, heatpump.getFloatWriteValue(HeatpumpIdm.ChannelId.REG_1401));
		assertEquals(1, heatpump.getWriteValue(HeatpumpIdm.ChannelId.REG_1710));
	}

	@Test
	public void pvLeavesTheHeatingCurveInCharge() throws Exception {
		var heatpump = new DummyHeatpumpIdm(HEATPUMP_ID);
		var electrical = new DummyFronius(ELECTRICAL_ID).withGridPower(-2500);

		test(new ControllerHeatpumpIdmImpl(), heatpump, electrical) //
				.activate(pvConfig().build()) //
				.next(new TestCase());

		// The constant-circuit setpoint stays untouched and the operating mode is a
		// weather-compensated one, so the curve keeps deciding the flow temperature
		assertNull(heatpump.getWriteValue(HeatpumpIdm.ChannelId.REG_1449));
		assertEquals(CircuitMode.NORMAL.getValue(), heatpump.getWriteValue(HeatpumpIdm.ChannelId.REG_1393));
	}

	@Test
	public void pvStaysNormalBelowThreshold() throws Exception {
		var heatpump = new DummyHeatpumpIdm(HEATPUMP_ID);
		var electrical = new DummyFronius(ELECTRICAL_ID).withGridPower(-1000);

		test(new ControllerHeatpumpIdmImpl(), heatpump, electrical) //
				.activate(pvConfig().build()) //
				.next(new TestCase());

		assertEquals(22f, heatpump.getFloatWriteValue(HeatpumpIdm.ChannelId.REG_1401));
	}

	@Test
	public void pvGridConsumptionIsNoSurplus() throws Exception {
		var heatpump = new DummyHeatpumpIdm(HEATPUMP_ID);
		var electrical = new DummyFronius(ELECTRICAL_ID).withGridPower(3000);
		var controller = new ControllerHeatpumpIdmImpl();

		test(controller, heatpump, electrical) //
				.activate(pvConfig().build()) //
				.next(new TestCase());

		assertEquals(22f, heatpump.getFloatWriteValue(HeatpumpIdm.ChannelId.REG_1401));
		assertEquals(-3000, controller.getSurplusPowerChannel().getNextValue().get());
	}

	@Test
	public void pvHysteresisHoldsBoostBetweenThresholds() throws Exception {
		var heatpump = new DummyHeatpumpIdm(HEATPUMP_ID);
		var electrical = new DummyFronius(ELECTRICAL_ID).withGridPower(-2500);
		var clock = clock();

		var test = test(new ControllerHeatpumpIdmImpl(), heatpump, electrical, clock) //
				.activate(pvConfig().build()) //
				.next(new TestCase());
		assertEquals(24f, heatpump.getFloatWriteValue(HeatpumpIdm.ChannelId.REG_1401));

		// 1700 W surplus is below the 2000 W start threshold but above the 1500 W
		// release threshold, so the boost must be held
		electrical.withGridPower(-1700);
		test.next(new TestCase().timeleap(clock, 20, ChronoUnit.MINUTES));
		assertEquals(24f, heatpump.getFloatWriteValue(HeatpumpIdm.ChannelId.REG_1401));

		// 1400 W surplus is below the release threshold
		electrical.withGridPower(-1400);
		test.next(new TestCase().timeleap(clock, 20, ChronoUnit.MINUTES));
		assertEquals(22f, heatpump.getFloatWriteValue(HeatpumpIdm.ChannelId.REG_1401));
	}

	@Test
	public void pvHoldTimeKeepsBoostThroughAShortSurplusDrop() throws Exception {
		var heatpump = new DummyHeatpumpIdm(HEATPUMP_ID);
		var electrical = new DummyFronius(ELECTRICAL_ID).withGridPower(-2500);
		var clock = clock();

		var test = test(new ControllerHeatpumpIdmImpl(), heatpump, electrical, clock) //
				.activate(pvConfig().build()) //
				.next(new TestCase());
		assertEquals(24f, heatpump.getFloatWriteValue(HeatpumpIdm.ChannelId.REG_1401));

		// A cloud takes the surplus far below the release threshold, but the boost has
		// only been running for 5 of the 15 minutes
		electrical.withGridPower(0);
		test.next(new TestCase().timeleap(clock, 5, ChronoUnit.MINUTES));
		assertEquals(24f, heatpump.getFloatWriteValue(HeatpumpIdm.ChannelId.REG_1401));

		test.next(new TestCase().timeleap(clock, 11, ChronoUnit.MINUTES));
		assertEquals(22f, heatpump.getFloatWriteValue(HeatpumpIdm.ChannelId.REG_1401));
	}

	@Test
	public void pvHoldTimeBlocksAnImmediateRestart() throws Exception {
		var heatpump = new DummyHeatpumpIdm(HEATPUMP_ID);
		var electrical = new DummyFronius(ELECTRICAL_ID).withGridPower(-2500);
		var clock = clock();

		var test = test(new ControllerHeatpumpIdmImpl(), heatpump, electrical, clock) //
				.activate(pvConfig().build()) //
				.next(new TestCase());

		electrical.withGridPower(0);
		test.next(new TestCase().timeleap(clock, 16, ChronoUnit.MINUTES));
		assertEquals(22f, heatpump.getFloatWriteValue(HeatpumpIdm.ChannelId.REG_1401));

		// The sun is back well above the threshold, but the released state is younger
		// than the hold time
		electrical.withGridPower(-4000);
		test.next(new TestCase().timeleap(clock, 2, ChronoUnit.MINUTES));
		assertEquals(22f, heatpump.getFloatWriteValue(HeatpumpIdm.ChannelId.REG_1401));

		test.next(new TestCase().timeleap(clock, 14, ChronoUnit.MINUTES));
		assertEquals(24f, heatpump.getFloatWriteValue(HeatpumpIdm.ChannelId.REG_1401));
	}

	@Test
	public void pvHoldTimeZeroSwitchesImmediately() throws Exception {
		var heatpump = new DummyHeatpumpIdm(HEATPUMP_ID);
		var electrical = new DummyFronius(ELECTRICAL_ID).withGridPower(-2500);

		var test = test(new ControllerHeatpumpIdmImpl(), heatpump, electrical) //
				.activate(pvConfig().setMinBoostHoldTime(0).build()) //
				.next(new TestCase());
		assertEquals(24f, heatpump.getFloatWriteValue(HeatpumpIdm.ChannelId.REG_1401));

		electrical.withGridPower(0);
		test.next(new TestCase());
		assertEquals(22f, heatpump.getFloatWriteValue(HeatpumpIdm.ChannelId.REG_1401));
	}

	@Test
	public void pvWithoutDataFallsBackToNormalRoomSetpointAndWarns() throws Exception {
		var heatpump = new DummyHeatpumpIdm(HEATPUMP_ID);
		var controller = new ControllerHeatpumpIdmImpl();

		test(controller, heatpump, new DummyFronius(ELECTRICAL_ID)) //
				.activate(pvConfig().build()) //
				.next(new TestCase());

		assertEquals(22f, heatpump.getFloatWriteValue(HeatpumpIdm.ChannelId.REG_1401));
		assertEquals(1, heatpump.getWriteValue(HeatpumpIdm.ChannelId.REG_1710));
		assertNull(controller.getSurplusPowerChannel().getNextValue().get());
		assertTrue(controller.getElectricalNotAvailableChannel().getNextValue().get());
	}

	@Test
	public void manualSwitchesTheCircuitToItsConstantModeButLeavesTheCurveUntouched() throws Exception {
		var heatpump = new DummyHeatpumpIdm(HEATPUMP_ID);

		test(new ControllerHeatpumpIdmImpl(), heatpump, new DummyFronius(ELECTRICAL_ID)) //
				.activate(MyConfig.create() //
						.setId(CTRL_ID) //
						.setMode(Mode.MANUAL) //
						.build()) //
				.next(new TestCase());

		assertEquals(CircuitMode.MANUAL_HEATING.getValue(), heatpump.getWriteValue(HeatpumpIdm.ChannelId.REG_1393));
		assertNull(heatpump.getFloatWriteValue(HeatpumpIdm.ChannelId.REG_1429));
		assertNull(heatpump.getWriteValue(HeatpumpIdm.ChannelId.REG_1442));
		assertNull(heatpump.getFloatWriteValue(HeatpumpIdm.ChannelId.REG_1401));
	}

	@Test
	public void manualCoolingSwitchesTheCircuitToItsCoolingMode() throws Exception {
		var heatpump = new DummyHeatpumpIdm(HEATPUMP_ID);

		test(new ControllerHeatpumpIdmImpl(), heatpump, new DummyFronius(ELECTRICAL_ID)) //
				.activate(MyConfig.create() //
						.setId(CTRL_ID) //
						.setMode(Mode.MANUAL) //
						.setManualCooling(true) //
						.build()) //
				.next(new TestCase());

		assertEquals(CircuitMode.MANUAL_COOLING.getValue(), heatpump.getWriteValue(HeatpumpIdm.ChannelId.REG_1393));
	}

	@Test
	public void manualWritesTheCircuitModeOnlyOnce() throws Exception {
		var heatpump = new DummyHeatpumpIdm(HEATPUMP_ID);

		var test = test(new ControllerHeatpumpIdmImpl(), heatpump, new DummyFronius(ELECTRICAL_ID)) //
				.activate(MyConfig.create() //
						.setId(CTRL_ID) //
						.setMode(Mode.MANUAL) //
						.build()) //
				.next(new TestCase());

		heatpump.clearWriteValue(HeatpumpIdm.ChannelId.REG_1393);
		test.next(new TestCase());

		assertNull(heatpump.getWriteValue(HeatpumpIdm.ChannelId.REG_1393));
	}

	@Test
	public void heatingCurveModeWritesAllThreeParametersAndPinsTheRoomSetpoint() throws Exception {
		var heatpump = new DummyHeatpumpIdm(HEATPUMP_ID);

		test(new ControllerHeatpumpIdmImpl(), heatpump, new DummyFronius(ELECTRICAL_ID)) //
				.activate(MyConfig.create() //
						.setId(CTRL_ID) //
						.setMode(Mode.HEATING_CURVE) //
						.setCircuitMode(CircuitMode.ECO) //
						.setCurveSlope(0.8f) //
						.setHeatingLimit(17) //
						.setRoomSetpoint(21f) //
						.build()) //
				.next(new TestCase());

		assertEquals(CircuitMode.ECO.getValue(), heatpump.getWriteValue(HeatpumpIdm.ChannelId.REG_1393));
		assertEquals(0.8f, heatpump.getFloatWriteValue(HeatpumpIdm.ChannelId.REG_1429));
		assertEquals(17, heatpump.getWriteValue(HeatpumpIdm.ChannelId.REG_1442));
		assertEquals(21f, heatpump.getFloatWriteValue(HeatpumpIdm.ChannelId.REG_1401));
		assertEquals(1, heatpump.getWriteValue(HeatpumpIdm.ChannelId.REG_1710));

		// No PV boost in this mode, whatever the surplus does
		assertNull(heatpump.getWriteValue(HeatpumpIdm.ChannelId.REG_1449));
	}

	@Test
	public void heatingCurveIsNotRewrittenEveryCycle() throws Exception {
		var heatpump = new DummyHeatpumpIdm(HEATPUMP_ID);

		var test = test(new ControllerHeatpumpIdmImpl(), heatpump, new DummyFronius(ELECTRICAL_ID)) //
				.activate(pvConfig().build()) //
				.next(new TestCase());
		assertEquals(15, heatpump.getWriteValue(HeatpumpIdm.ChannelId.REG_1442));

		// Reg1393 is an EEPROM register with a limited number of write cycles, so a
		// manual change must survive instead of being overwritten a second later
		heatpump.clearWriteValue(HeatpumpIdm.ChannelId.REG_1393);
		heatpump.clearWriteValue(HeatpumpIdm.ChannelId.REG_1429);
		heatpump.clearWriteValue(HeatpumpIdm.ChannelId.REG_1442);
		test.next(new TestCase());

		assertNull(heatpump.getWriteValue(HeatpumpIdm.ChannelId.REG_1393));
		assertNull(heatpump.getFloatWriteValue(HeatpumpIdm.ChannelId.REG_1429));
		assertNull(heatpump.getWriteValue(HeatpumpIdm.ChannelId.REG_1442));
	}

	@Test
	public void priceAllowsHeatingBelowThreshold() throws Exception {
		var heatpump = new DummyHeatpumpIdm(HEATPUMP_ID);
		var price = new DummyPriceService(PRICE_ID).withCurrentPrice(0.10f);

		priceTest(new ControllerHeatpumpIdmImpl(), heatpump, price) //
				.activate(priceConfig().build()) //
				.next(new TestCase());

		assertEquals(1, heatpump.getWriteValue(HeatpumpIdm.ChannelId.REG_1710));
		assertEquals(0, heatpump.getWriteValue(HeatpumpIdm.ChannelId.REG_1711));
	}

	@Test
	public void priceLeavesTheHeatingCurveInCharge() throws Exception {
		var heatpump = new DummyHeatpumpIdm(HEATPUMP_ID);
		var price = new DummyPriceService(PRICE_ID).withCurrentPrice(0.10f);

		priceTest(new ControllerHeatpumpIdmImpl(), heatpump, price) //
				.activate(priceConfig().build()) //
				.next(new TestCase());

		assertNull(heatpump.getWriteValue(HeatpumpIdm.ChannelId.REG_1449));
		assertEquals(22f, heatpump.getFloatWriteValue(HeatpumpIdm.ChannelId.REG_1401));
		assertEquals(CircuitMode.NORMAL.getValue(), heatpump.getWriteValue(HeatpumpIdm.ChannelId.REG_1393));
	}

	@Test
	public void priceBlocksHeatingAboveThreshold() throws Exception {
		var heatpump = new DummyHeatpumpIdm(HEATPUMP_ID);
		var price = new DummyPriceService(PRICE_ID).withCurrentPrice(0.30f);

		priceTest(new ControllerHeatpumpIdmImpl(), heatpump, price) //
				.activate(priceConfig().build()) //
				.next(new TestCase());

		assertEquals(0, heatpump.getWriteValue(HeatpumpIdm.ChannelId.REG_1710));
	}

	@Test
	public void priceHysteresisHoldsReleaseBetweenThresholds() throws Exception {
		var heatpump = new DummyHeatpumpIdm(HEATPUMP_ID);
		var price = new DummyPriceService(PRICE_ID).withCurrentPrice(0.10f);
		var clock = clock();

		var test = priceTest(new ControllerHeatpumpIdmImpl(), heatpump, price, clock) //
				.activate(priceConfig().build()) //
				.next(new TestCase());
		assertEquals(1, heatpump.getWriteValue(HeatpumpIdm.ChannelId.REG_1710));

		// 0.21 is above the 0.20 start threshold but below the 0.22 release
		// threshold, so the heating release must be held
		price.withCurrentPrice(0.21f);
		test.next(new TestCase().timeleap(clock, 20, ChronoUnit.MINUTES));
		assertEquals(1, heatpump.getWriteValue(HeatpumpIdm.ChannelId.REG_1710));

		// 0.23 is above the release threshold
		price.withCurrentPrice(0.23f);
		test.next(new TestCase().timeleap(clock, 20, ChronoUnit.MINUTES));
		assertEquals(0, heatpump.getWriteValue(HeatpumpIdm.ChannelId.REG_1710));
	}

	@Test
	public void priceHoldTimeBlocksAnImmediateRestart() throws Exception {
		var heatpump = new DummyHeatpumpIdm(HEATPUMP_ID);
		var price = new DummyPriceService(PRICE_ID).withCurrentPrice(0.30f);
		var clock = clock();

		var test = priceTest(new ControllerHeatpumpIdmImpl(), heatpump, price, clock) //
				.activate(priceConfig().build()) //
				.next(new TestCase());
		assertEquals(0, heatpump.getWriteValue(HeatpumpIdm.ChannelId.REG_1710));

		// The price is well below the threshold again, but the blocked state is
		// younger than the hold time
		price.withCurrentPrice(0.05f);
		test.next(new TestCase().timeleap(clock, 2, ChronoUnit.MINUTES));
		assertEquals(0, heatpump.getWriteValue(HeatpumpIdm.ChannelId.REG_1710));

		test.next(new TestCase().timeleap(clock, 14, ChronoUnit.MINUTES));
		assertEquals(1, heatpump.getWriteValue(HeatpumpIdm.ChannelId.REG_1710));
	}

	@Test
	public void priceHoldTimeZeroSwitchesImmediately() throws Exception {
		var heatpump = new DummyHeatpumpIdm(HEATPUMP_ID);
		var price = new DummyPriceService(PRICE_ID).withCurrentPrice(0.10f);

		var test = priceTest(new ControllerHeatpumpIdmImpl(), heatpump, price) //
				.activate(priceConfig().setMinPriceHoldTime(0).build()) //
				.next(new TestCase());
		assertEquals(1, heatpump.getWriteValue(HeatpumpIdm.ChannelId.REG_1710));

		price.withCurrentPrice(0.30f);
		test.next(new TestCase());
		assertEquals(0, heatpump.getWriteValue(HeatpumpIdm.ChannelId.REG_1710));
	}

	@Test
	public void priceWithoutDataFallsBackToHeatingAllowedAndWarns() throws Exception {
		var heatpump = new DummyHeatpumpIdm(HEATPUMP_ID);
		var controller = new ControllerHeatpumpIdmImpl();
		var price = new DummyPriceService(PRICE_ID);

		priceTest(controller, heatpump, price) //
				.activate(priceConfig().build()) //
				.next(new TestCase());

		assertEquals(1, heatpump.getWriteValue(HeatpumpIdm.ChannelId.REG_1710));
		assertNull(controller.getCurrentPriceChannel().getNextValue().get());
		assertTrue(controller.getPriceNotAvailableChannel().getNextValue().get());
	}

	@Test
	public void priceWithoutComponentFallsBackToHeatingAllowed() throws Exception {
		var heatpump = new DummyHeatpumpIdm(HEATPUMP_ID);

		new ControllerTest(new ControllerHeatpumpIdmImpl()) //
				.addReference("componentManager", new DummyComponentManager(clock())) //
				.addReference("heatpump", heatpump) //
				.addReference("electrical", new DummyFronius(ELECTRICAL_ID)) //
				.activate(priceConfig().build()) //
				.next(new TestCase());

		assertEquals(1, heatpump.getWriteValue(HeatpumpIdm.ChannelId.REG_1710));
	}

	/**
	 * A negative hysteresis would put the release threshold below the block
	 * threshold, so every cycle would flip the request and the plant would
	 * short-cycle. The hold time is switched off here so nothing else could mask
	 * the flapping.
	 */
	@Test
	public void priceNegativeHysteresisDoesNotInvertTheSwitchingPoints() throws Exception {
		var heatpump = new DummyHeatpumpIdm(HEATPUMP_ID);
		var price = new DummyPriceService(PRICE_ID).withCurrentPrice(0.125f);

		var test = priceTest(new ControllerHeatpumpIdmImpl(), heatpump, price) //
				.activate(priceConfig() //
						.setPriceThreshold(0.13f) //
						.setPriceHysteresis(-0.5f) //
						.setMinPriceHoldTime(0) //
						.build()) //
				.next(new TestCase());

		for (var cycle = 0; cycle < 4; cycle++) {
			assertEquals(1, heatpump.getWriteValue(HeatpumpIdm.ChannelId.REG_1710));
			test.next(new TestCase());
		}
	}

	@Test
	public void pvNegativeHysteresisDoesNotInvertTheSwitchingPoints() throws Exception {
		var heatpump = new DummyHeatpumpIdm(HEATPUMP_ID);
		var electrical = new DummyFronius(ELECTRICAL_ID).withGridPower(-2500);

		var test = test(new ControllerHeatpumpIdmImpl(), heatpump, electrical) //
				.activate(pvConfig() //
						.setSurplusHysteresis(-5000) //
						.setMinBoostHoldTime(0) //
						.build()) //
				.next(new TestCase());

		for (var cycle = 0; cycle < 4; cycle++) {
			assertEquals(24f, heatpump.getFloatWriteValue(HeatpumpIdm.ChannelId.REG_1401));
			test.next(new TestCase());
		}
	}

	@Test
	public void heatingCurveMismatchIsReportedButNotOverwritten() throws Exception {
		var controller = new ControllerHeatpumpIdmImpl();
		var heatpump = new DummyHeatpumpIdm(HEATPUMP_ID) //
				.withRegister(HeatpumpIdm.ChannelId.REG_1393, CircuitMode.NORMAL.getValue()) //
				.withRegister(HeatpumpIdm.ChannelId.REG_1429, 1.2f) // configured: 0.6
				.withRegister(HeatpumpIdm.ChannelId.REG_1442, 15);

		var test = priceTest(controller, heatpump, new DummyPriceService(PRICE_ID).withCurrentPrice(0.10f)) //
				.activate(priceConfig().build()) //
				.next(new TestCase());

		// first cycle writes the parameters; the deviation is only judged afterwards
		heatpump.clearWriteValue(HeatpumpIdm.ChannelId.REG_1429);
		test.next(new TestCase());

		assertTrue(controller.getHeatingCurveMismatchChannel().getNextValue().get());
		assertNull(heatpump.getFloatWriteValue(HeatpumpIdm.ChannelId.REG_1429));
	}

	@Test
	public void matchingHeatingCurveRaisesNoMismatch() throws Exception {
		var controller = new ControllerHeatpumpIdmImpl();
		var heatpump = new DummyHeatpumpIdm(HEATPUMP_ID) //
				.withRegister(HeatpumpIdm.ChannelId.REG_1393, CircuitMode.NORMAL.getValue()) //
				.withRegister(HeatpumpIdm.ChannelId.REG_1429, 0.6f) //
				.withRegister(HeatpumpIdm.ChannelId.REG_1442, 15);

		var test = priceTest(controller, heatpump, new DummyPriceService(PRICE_ID).withCurrentPrice(0.10f)) //
				.activate(priceConfig().build()) //
				.next(new TestCase());
		test.next(new TestCase());

		assertFalse(controller.getHeatingCurveMismatchChannel().getNextValue().get());
	}

	/**
	 * After a plant restart the readback is empty for a while; reporting that as a
	 * mismatch would only produce false alarms.
	 */
	@Test
	public void anUnreadCurveRegisterIsNoMismatch() throws Exception {
		var controller = new ControllerHeatpumpIdmImpl();
		var heatpump = new DummyHeatpumpIdm(HEATPUMP_ID);

		var test = priceTest(controller, heatpump, new DummyPriceService(PRICE_ID).withCurrentPrice(0.10f)) //
				.activate(priceConfig().build()) //
				.next(new TestCase());
		test.next(new TestCase());

		assertFalse(controller.getHeatingCurveMismatchChannel().getNextValue().get());
	}

	private static MyConfig.Builder pvConfig() {
		return MyConfig.create() //
				.setId(CTRL_ID) //
				.setMode(Mode.PV_OPTIMIZED) //
				.setSurplusThreshold(2000) //
				.setSurplusHysteresis(500) //
				.setRoomSetpoint(22f) //
				.setBoostOffset(2f);
	}

	private static MyConfig.Builder priceConfig() {
		return MyConfig.create() //
				.setId(CTRL_ID) //
				.setMode(Mode.PRICE_OPTIMIZED) //
				.setPriceId(PRICE_ID) //
				.setPriceThreshold(0.20f) //
				.setPriceHysteresis(0.02f) //
				.setRoomSetpoint(22f);
	}
}
