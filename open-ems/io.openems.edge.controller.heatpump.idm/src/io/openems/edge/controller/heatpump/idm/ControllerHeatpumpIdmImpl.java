package io.openems.edge.controller.heatpump.idm;

import static org.osgi.service.component.annotations.ReferenceCardinality.MANDATORY;
import static org.osgi.service.component.annotations.ReferencePolicyOption.GREEDY;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.Designate;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.referencetarget.GenerateTargetsFromReferences;
import io.openems.edge.common.channel.FloatReadChannel;
import io.openems.edge.common.channel.IntegerReadChannel;
import io.openems.edge.common.channel.IntegerWriteChannel;
import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.controller.api.Controller;
import io.openems.edge.external.influx.ExternalInflux;
import io.openems.edge.heatpump.idm.HeatpumpIdm;

@Designate(ocd = Config.class, factory = true)
@Component(//
		name = "Controller.Heatpump.Idm", //
		immediate = true, //
		configurationPolicy = ConfigurationPolicy.REQUIRE //
)
@GenerateTargetsFromReferences({ "heatpump", "externalInflux" })
public class ControllerHeatpumpIdmImpl extends AbstractOpenemsComponent
		implements ControllerHeatpumpIdm, Controller, OpenemsComponent {

	private final HttpClient httpClient = HttpClient.newHttpClient();

	@Reference(cardinality = MANDATORY, policyOption = GREEDY, //
			target = "(&(id=${config.heatpump_id})(enabled=true))")
	private HeatpumpIdm heatpump;

	@Reference(cardinality = MANDATORY, policyOption = GREEDY, //
			target = "(&(id=externalInflux0)(enabled=true))")
	private ExternalInflux externalInflux;

	private Config config;
	private Mode previousMode = null;

	public ControllerHeatpumpIdmImpl() {
		super(//
				OpenemsComponent.ChannelId.values(), //
				Controller.ChannelId.values(), //
				ControllerHeatpumpIdm.ChannelId.values() //
		);
	}

	@Activate
	private void activate(ComponentContext context, Config config) {
		super.activate(context, config.id(), config.alias(), config.enabled());
		this.config = config;
	}

	@Modified
	private void modified(ComponentContext context, Config config) {
		super.modified(context, config.id(), config.alias(), config.enabled());
		this.config = config;
		this.previousMode = null;
	}

	@Override
	@Deactivate
	protected void deactivate() {
		super.deactivate();
	}

	@Override
	public void run() throws OpenemsNamedException {
		this.writeRegister(HeatpumpIdm.ChannelId.HEAT_DEMAND, 1);

		var mode = this.config.mode();

		this._setActiveMode(mode);

		if (mode != this.previousMode) {
			this.applyOperationMode(mode);
			this.previousMode = mode;
		}

		switch (mode) {
		case NORMAL -> this._setAppliedSetpoint(null);
		case MANUAL -> this.applyManualSetpoint();
		}
	}

	private void applyOperationMode(Mode mode) throws OpenemsNamedException {
		var operationMode = switch (mode) {
		case NORMAL -> CircuitMode.NORMAL.getValue();
		case MANUAL -> CircuitMode.MANUAL_HEATING.getValue();
		};

		this.writeRegister(HeatpumpIdm.ChannelId.OPERATION_MODE, operationMode);
	}

	private void applyManualSetpoint() throws OpenemsNamedException {
		FloatReadChannel roomTemperatureChannel = this.heatpump
				.channel(HeatpumpIdm.ChannelId.ROOM_TEMPERATURE);
		FloatReadChannel outdoorTemperatureChannel = this.heatpump
				.channel(HeatpumpIdm.ChannelId.OUTDOOR_TEMPERATURE);
		FloatReadChannel flowTemperatureChannel = this.heatpump
				.channel(HeatpumpIdm.ChannelId.FLOW_TEMPERATURE);
		FloatReadChannel returnTemperatureChannel = this.heatpump
				.channel(HeatpumpIdm.ChannelId.RETURN_TEMPERATURE);
		FloatReadChannel electricalPowerChannel = this.heatpump
				.channel(HeatpumpIdm.ChannelId.ELECTRICAL_POWER_CONSUMPTION);
		FloatReadChannel thermalPowerChannel = this.heatpump
				.channel(HeatpumpIdm.ChannelId.THERMAL_POWER);

		IntegerReadChannel pvPowerChannel = this.externalInflux
				.channel(ExternalInflux.ChannelId.PV_POWER);
		IntegerReadChannel gridPowerChannel = this.externalInflux
				.channel(ExternalInflux.ChannelId.GRID_POWER);

		double roomTemperature = roomTemperatureChannel.value().get();
		double outdoorTemperature = outdoorTemperatureChannel.value().get();
		double flowTemperature = flowTemperatureChannel.value().get();
		double returnTemperature = returnTemperatureChannel.value().get();

		// iDM liefert die Leistungen bereits in kW
		double electricalPower = electricalPowerChannel.value().get();
		double thermalPower = thermalPowerChannel.value().get();

		// ExternalInflux liefert W -> Umrechnung auf kW
		double pvPower = pvPowerChannel.value().get();
		double gridPower = gridPowerChannel.value().get();

		int setpoint = this.requestFlowTemperatureSetpoint(
				roomTemperature,
				outdoorTemperature,
				flowTemperature,
				returnTemperature,
				electricalPower,
				thermalPower,
				pvPower,
				gridPower);

		this.writeRegister(
				HeatpumpIdm.ChannelId.FLOW_TEMPERATURE_SETPOINT,
				setpoint);

		this._setAppliedSetpoint(setpoint);
	}

	private int requestFlowTemperatureSetpoint(
			double roomTemperature,
			double outdoorTemperature,
			double flowTemperature,
			double returnTemperature,
			double electricalPower,
			double thermalPower,
			double pvPower,
			double gridPower) {

		String json = """
				{
					"room_temperature": %f,
					"outdoor_temperature": %f,
					"flow_temperature": %f,
					"return_temperature": %f,
					"electrical_power": %f,
					"thermal_power": %f,
					"pv_power": %f,
					"grid_power": %f
				}
				""".formatted(
				roomTemperature,
				outdoorTemperature,
				flowTemperature,
				returnTemperature,
				electricalPower,
				thermalPower,
				pvPower,
				gridPower);

		try {
			var request = HttpRequest.newBuilder()
					.uri(URI.create("http://control-service:8000/control"))
					.header("Content-Type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString(json))
					.build();

			var response = this.httpClient.send(
					request,
					HttpResponse.BodyHandlers.ofString());

			String body = response.body();

			String value = body
					.replaceAll(".*\"flow_temperature_setpoint\"\\s*:\\s*", "")
					.replaceAll("[^0-9-].*", "");

			return Integer.parseInt(value);

		} catch (Exception e) {
			throw new RuntimeException("Control-Service nicht erreichbar", e);
		}
	}

	private void writeRegister(HeatpumpIdm.ChannelId channelId, int value)
			throws OpenemsNamedException {
		IntegerWriteChannel channel = this.heatpump.channel(channelId);
		channel.setNextWriteValue(value);
	}

	@Override
	public String debugLog() {
		return switch (this.config.mode()) {
		case NORMAL -> "Mode:Normal";
		case MANUAL -> "Mode:Manual|Setpoint:" + this.getAppliedSetpoint().asString();
		};
	}
}