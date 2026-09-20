package io.openems.edge.electrical.fronius;

import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.openems.common.bridge.http.api.BridgeHttp;
import io.openems.common.bridge.http.api.BridgeHttpFactory;
import io.openems.common.bridge.http.api.HttpError;
import io.openems.common.bridge.http.api.HttpResponse;
import io.openems.edge.bridge.http.cycle.HttpBridgeCycleServiceDefinition;
import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.OpenemsComponent;

@Designate(ocd = Config.class, factory = true)
@Component(//
		name = "Electrical.Fronius", //
		immediate = true, //
		configurationPolicy = ConfigurationPolicy.REQUIRE //
)
public class FroniusImpl extends AbstractOpenemsComponent implements Fronius, OpenemsComponent {

	private static final String POWER_FLOW_PATH = "/solar_api/v1/GetPowerFlowRealtimeData.fcgi";

	private static final String INVERTER_ID = "1";

	private final Logger log = LoggerFactory.getLogger(FroniusImpl.class);

	private BridgeHttp httpBridge = null;

	@Reference
	private BridgeHttpFactory httpBridgeFactory;

	@Reference
	private HttpBridgeCycleServiceDefinition httpBridgeCycleServiceDefinition;

	public FroniusImpl() {
		super(//
				OpenemsComponent.ChannelId.values(), //
				Fronius.ChannelId.values() //
		);
	}

	@Activate
	private void activate(ComponentContext context, Config config) {
		super.activate(context, config.id(), config.alias(), config.enabled());

		if (!this.isEnabled()) {
			return;
		}

		this.httpBridge = this.httpBridgeFactory.get();
		this.httpBridge.createService(this.httpBridgeCycleServiceDefinition) //
				.subscribeJsonCycle(config.cycles(), //
						removeTrailingSlash(config.baseUrl()) + POWER_FLOW_PATH, //
						this::handlePowerFlowResponse);
	}

	@Override
	@Deactivate
	protected void deactivate() {
		super.deactivate();

		if (this.httpBridge != null) {
			this.httpBridgeFactory.unget(this.httpBridge);
			this.httpBridge = null;
		}
	}

	private void handlePowerFlowResponse(HttpResponse<JsonElement> response, HttpError error) {
		if (error != null) {
			this.onCommunicationFailed("PowerFlow GET failed: " + error);
			return;
		}
		if (response == null || response.data() == null) {
			this.onCommunicationFailed("PowerFlow response is empty");
			return;
		}

		try {
			var root = response.data().getAsJsonObject();

			var statusCode = root.getAsJsonObject("Head").getAsJsonObject("Status").get("Code").getAsInt();
			if (statusCode != 0) {
				this.onCommunicationFailed("PowerFlow API status code is " + statusCode);
				return;
			}

			this.applyPowerFlow(root.getAsJsonObject("Body").getAsJsonObject("Data"));
			this._setCommunicationFailed(false);

		} catch (RuntimeException e) {
			this.onCommunicationFailed("Failed to parse PowerFlow response: " + e.getMessage());
		}
	}

	private void applyPowerFlow(JsonObject data) {
		var site = data.getAsJsonObject("Site");
		var inverter = data.getAsJsonObject("Inverters").getAsJsonObject(INVERTER_ID);

		this._setBatteryPower(Math.round(site.get("P_Akku").getAsFloat()));
		this._setGridPower(Math.round(site.get("P_Grid").getAsFloat()));
		this._setLoadPower(Math.round(site.get("P_Load").getAsFloat()));
		this._setPvPower(Math.round(site.get("P_PV").getAsFloat()));
		this._setSoc(Math.round(inverter.get("SOC").getAsFloat()));
		this._setBatteryMode(inverter.get("Battery_Mode").getAsString());
	}

	private void onCommunicationFailed(String reason) {
		this.logWarn(this.log, reason);
		this.clearMeasuredValues();
		this._setCommunicationFailed(true);
	}

	private void clearMeasuredValues() {
		this._setBatteryPower(null);
		this._setGridPower(null);
		this._setLoadPower(null);
		this._setPvPower(null);
		this._setSoc(null);
		this._setBatteryMode(null);
	}

	private static String removeTrailingSlash(String value) {
		return value.endsWith("/") //
				? value.substring(0, value.length() - 1) //
				: value;
	}

	@Override
	public String debugLog() {
		return "Grid:" + this.getGridPower().asString() //
				+ "|Battery:" + this.getBatteryPower().asString() //
				+ "|SOC:" + this.getSoc().asString();
	}

}
