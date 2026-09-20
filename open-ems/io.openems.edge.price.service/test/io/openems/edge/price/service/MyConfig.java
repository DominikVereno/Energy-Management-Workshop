package io.openems.edge.price.service;

import io.openems.common.test.AbstractComponentConfig;

@SuppressWarnings("all")
public class MyConfig extends AbstractComponentConfig implements Config {

	protected static class Builder {
		private String id;
		private String baseUrl = "http://127.0.0.1:8200";
		private String provider = "mock";
		private String zone = "AT";
		private int pollIntervalMinutes = 15;

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

		public Builder setProvider(String provider) {
			this.provider = provider;
			return this;
		}

		public Builder setZone(String zone) {
			this.zone = zone;
			return this;
		}

		public Builder setPollIntervalMinutes(int pollIntervalMinutes) {
			this.pollIntervalMinutes = pollIntervalMinutes;
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
	public String provider() {
		return this.builder.provider;
	}

	@Override
	public String zone() {
		return this.builder.zone;
	}

	@Override
	public int pollIntervalMinutes() {
		return this.builder.pollIntervalMinutes;
	}

}
