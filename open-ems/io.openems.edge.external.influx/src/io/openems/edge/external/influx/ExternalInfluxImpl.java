package io.openems.edge.external.influx;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.controller.api.Controller;

@Designate(ocd = Config.class, factory = true)
@Component(//
		name = "External.Influx", //
		immediate = true, //
		configurationPolicy = ConfigurationPolicy.REQUIRE)
public class ExternalInfluxImpl extends AbstractOpenemsComponent
		implements ExternalInflux, OpenemsComponent, Controller {

	private final Logger log = LoggerFactory.getLogger(ExternalInfluxImpl.class);
	private final HttpClient httpClient = HttpClient.newHttpClient();

	private Config config;

	public ExternalInfluxImpl() {
		super(//
				OpenemsComponent.ChannelId.values(), //
				Controller.ChannelId.values(), //
				ExternalInflux.ChannelId.values() //
		);
	}

	@Activate
	private void activate(ComponentContext context, Config config) {
		super.activate(context, config.id(), config.alias(), config.enabled());
		this.config = config;

		this.log.info("External Influx activated: id={}, url={}, org={}",
				config.id(), config.url(), config.org());
	}

	@Override
	@Deactivate
	protected void deactivate() {
		this.log.info("External Influx deactivated");
		super.deactivate();
	}

	@Override
	public void run() throws OpenemsNamedException {
		this.readInflux();
	}

	private void readInflux() {
		String flux = """
				from(bucket: "openems")
				  |> range(start: -30s)
				  |> filter(fn: (r) => r._measurement == "data")
				  |> filter(fn: (r) =>
				      r._field == "externalapi0/PvPower" or
				      r._field == "externalapi0/GridPower")
				  |> last()
				""";

		try {
			String url = this.config.url()
					+ "/api/v2/query?org="
					+ this.config.org();

			this.log.info("Querying external InfluxDB: {}", url);

			var request = HttpRequest.newBuilder()
					.uri(URI.create(url))
					.header("Authorization", "Token " + this.config.token())
					.header("Content-Type", "application/vnd.flux")
					.header("Accept", "application/csv")
					.POST(HttpRequest.BodyPublishers.ofString(flux))
					.build();

			var response = this.httpClient.send(
					request,
					HttpResponse.BodyHandlers.ofString());

			this.log.info("InfluxDB response status: {}", response.statusCode());

			// Temporarily log the complete response for debugging
			this.log.info("InfluxDB response:\n{}", response.body());

			if (response.statusCode() != 200) {
				this.log.error("InfluxDB query failed: HTTP {} - {}",
						response.statusCode(), response.body());
				return;
			}

			this.parseResponse(response.body());

		} catch (Exception e) {
			this.log.error("Error while querying external InfluxDB", e);
		}
	}

	private void parseResponse(String csv) {
		for (String line : csv.split("\\R")) {

			if (line.isBlank() || line.startsWith("#")) {
				continue;
			}

			if (line.contains("_result,table,_start,_stop,_time,_value,_field")) {
				continue;
			}

			String[] columns = line.split(",");

			if (columns.length < 8) {
				this.log.warn("Unexpected InfluxDB CSV line: {}", line);
				continue;
			}

			try {
				String value = columns[6];

				if (line.contains("externalapi0/PvPower")) {
					int pvPower = (int) Math.round(
							Double.parseDouble(value) * 10.0);

					this._setPvPower(pvPower);
					this.log.info("PV_POWER updated: {} W", pvPower);
				}

				if (line.contains("externalapi0/GridPower")) {
					int gridPower = (int) Math.round(
							Double.parseDouble(value));

					this._setGridPower(gridPower);
					this.log.info("GRID_POWER updated: {} W", gridPower);
				}

			} catch (NumberFormatException e) {
				this.log.warn("Could not parse InfluxDB value from line: {}", line);
			}
		}
	}

	@Override
	public String debugLog() {
		return "PvPower:"
				+ this.channel(ExternalInflux.ChannelId.PV_POWER).value().asString()
				+ "|GridPower:"
				+ this.channel(ExternalInflux.ChannelId.GRID_POWER).value().asString();
	}
}