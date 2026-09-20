package io.openems.edge.electrical.fronius;

import io.openems.common.test.AbstractComponentConfig;

@SuppressWarnings("all")
public class MyConfig extends AbstractComponentConfig implements Config {

	protected static class Builder {
		private String id;
		private String baseUrl;
		private int cycles = 1;

		private Builder() {
		}

		public Builder setId(String id) {
			this.id = id;
			return this;
		}

		public Builder setBaseUrl(String baseUrl) {
			this.baseUrl = baseUrl;
			return this;
		}

		public Builder setCycles(int cycles) {
			this.cycles = cycles;
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
	public String baseUrl() {
		return this.builder.baseUrl;
	}

	@Override
	public int cycles() {
		return this.builder.cycles;
	}

}
