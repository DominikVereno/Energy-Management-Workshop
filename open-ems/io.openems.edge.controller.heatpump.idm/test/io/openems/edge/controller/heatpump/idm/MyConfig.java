package io.openems.edge.controller.heatpump.idm;

import io.openems.common.test.AbstractComponentConfig;

@SuppressWarnings("all")
public class MyConfig extends AbstractComponentConfig implements Config {

	protected static class Builder {
		private String id;
		private Mode mode = Mode.MANUAL;
		private String heatpumpId = "heatpump0";
		private boolean manualHeating = false;
		private boolean manualCooling = false;
		private int manualHeatingSetpoint = 45;
		private int manualCoolingSetpoint = 18;
		private String electricalId = "fronius0";
		private int surplusThreshold = 2000;
		private int surplusHysteresis = 500;
		private int minBoostHoldTime = 15;
		private float roomSetpoint = 22f;
		private float boostOffset = 2f;
		private CircuitMode circuitMode = CircuitMode.NORMAL;
		private float curveSlope = 0.6f;
		private int heatingLimit = 15;
		private String priceId = "price0";
		private float priceThreshold = 0.20f;
		private float priceHysteresis = 0.02f;
		private int minPriceHoldTime = 15;

		private Builder() {
		}

		public Builder setId(String id) {
			this.id = id;
			return this;
		}

		public Builder setMode(Mode mode) {
			this.mode = mode;
			return this;
		}

		public Builder setHeatpumpId(String heatpumpId) {
			this.heatpumpId = heatpumpId;
			return this;
		}

		public Builder setManualHeating(boolean manualHeating) {
			this.manualHeating = manualHeating;
			return this;
		}

		public Builder setManualCooling(boolean manualCooling) {
			this.manualCooling = manualCooling;
			return this;
		}

		public Builder setManualHeatingSetpoint(int manualHeatingSetpoint) {
			this.manualHeatingSetpoint = manualHeatingSetpoint;
			return this;
		}

		public Builder setManualCoolingSetpoint(int manualCoolingSetpoint) {
			this.manualCoolingSetpoint = manualCoolingSetpoint;
			return this;
		}

		public Builder setElectricalId(String electricalId) {
			this.electricalId = electricalId;
			return this;
		}

		public Builder setSurplusThreshold(int surplusThreshold) {
			this.surplusThreshold = surplusThreshold;
			return this;
		}

		public Builder setSurplusHysteresis(int surplusHysteresis) {
			this.surplusHysteresis = surplusHysteresis;
			return this;
		}

		public Builder setMinBoostHoldTime(int minBoostHoldTime) {
			this.minBoostHoldTime = minBoostHoldTime;
			return this;
		}

		public Builder setRoomSetpoint(float roomSetpoint) {
			this.roomSetpoint = roomSetpoint;
			return this;
		}

		public Builder setBoostOffset(float boostOffset) {
			this.boostOffset = boostOffset;
			return this;
		}

		public Builder setCircuitMode(CircuitMode circuitMode) {
			this.circuitMode = circuitMode;
			return this;
		}

		public Builder setCurveSlope(float curveSlope) {
			this.curveSlope = curveSlope;
			return this;
		}

		public Builder setHeatingLimit(int heatingLimit) {
			this.heatingLimit = heatingLimit;
			return this;
		}

		public Builder setPriceId(String priceId) {
			this.priceId = priceId;
			return this;
		}

		public Builder setPriceThreshold(float priceThreshold) {
			this.priceThreshold = priceThreshold;
			return this;
		}

		public Builder setPriceHysteresis(float priceHysteresis) {
			this.priceHysteresis = priceHysteresis;
			return this;
		}

		public Builder setMinPriceHoldTime(int minPriceHoldTime) {
			this.minPriceHoldTime = minPriceHoldTime;
			return this;
		}

		public MyConfig build() {
			return new MyConfig(this);
		}
	}

	/**
	 * Create a Config builder.
	 *
	 * @return a {@link Builder}
	 */
	public static Builder create() {
		return new Builder();
	}

	private final Builder builder;

	private MyConfig(Builder builder) {
		super(Config.class, builder.id);
		this.builder = builder;
	}

	@Override
	public Mode mode() {
		return this.builder.mode;
	}

	@Override
	public String heatpump_id() {
		return this.builder.heatpumpId;
	}

	@Override
	public boolean manualHeating() {
		return this.builder.manualHeating;
	}

	@Override
	public boolean manualCooling() {
		return this.builder.manualCooling;
	}

	@Override
	public int manualHeatingSetpoint() {
		return this.builder.manualHeatingSetpoint;
	}

	@Override
	public int manualCoolingSetpoint() {
		return this.builder.manualCoolingSetpoint;
	}

	@Override
	public String electrical_id() {
		return this.builder.electricalId;
	}

	@Override
	public int surplusThreshold() {
		return this.builder.surplusThreshold;
	}

	@Override
	public int surplusHysteresis() {
		return this.builder.surplusHysteresis;
	}

	@Override
	public int minBoostHoldTime() {
		return this.builder.minBoostHoldTime;
	}

	@Override
	public float roomSetpoint() {
		return this.builder.roomSetpoint;
	}

	@Override
	public float boostOffset() {
		return this.builder.boostOffset;
	}

	@Override
	public CircuitMode circuitMode() {
		return this.builder.circuitMode;
	}

	@Override
	public float curveSlope() {
		return this.builder.curveSlope;
	}

	@Override
	public int heatingLimit() {
		return this.builder.heatingLimit;
	}

	@Override
	public String price_id() {
		return this.builder.priceId;
	}

	@Override
	public float priceThreshold() {
		return this.builder.priceThreshold;
	}

	@Override
	public float priceHysteresis() {
		return this.builder.priceHysteresis;
	}

	@Override
	public int minPriceHoldTime() {
		return this.builder.minPriceHoldTime;
	}
}
