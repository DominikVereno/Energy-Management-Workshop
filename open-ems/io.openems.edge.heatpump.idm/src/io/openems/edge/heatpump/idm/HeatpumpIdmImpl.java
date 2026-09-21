package io.openems.edge.heatpump.idm;

import static io.openems.edge.bridge.modbus.api.element.WordOrder.LSWMSW;

import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.osgi.service.metatype.annotations.Designate;

import io.openems.common.exceptions.OpenemsException;
import io.openems.edge.bridge.modbus.api.AbstractOpenemsModbusComponent;
import io.openems.edge.bridge.modbus.api.BridgeModbus;
import io.openems.edge.bridge.modbus.api.ModbusComponent;
import io.openems.edge.bridge.modbus.api.ModbusProtocol;
import io.openems.edge.bridge.modbus.api.element.FloatDoublewordElement;
import io.openems.edge.bridge.modbus.api.element.UnsignedWordElement;
import io.openems.edge.bridge.modbus.api.task.FC16WriteRegistersTask;
import io.openems.edge.bridge.modbus.api.task.FC3ReadRegistersTask;
import io.openems.edge.bridge.modbus.api.task.FC4ReadInputRegistersTask;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.taskmanager.Priority;

@Designate(ocd = Config.class, factory = true)
@Component(//
		name = "HeatPump.Idm", //
		immediate = true, //
		configurationPolicy = ConfigurationPolicy.REQUIRE)
public class HeatpumpIdmImpl extends AbstractOpenemsModbusComponent
		implements HeatpumpIdm, ModbusComponent, OpenemsComponent {

	@Reference
	private ConfigurationAdmin cm;

	@Reference(policy = ReferencePolicy.STATIC, policyOption = ReferencePolicyOption.GREEDY, //
			cardinality = ReferenceCardinality.MANDATORY)
	protected void setModbus(BridgeModbus modbus) {
		super.setModbus(modbus);
	}

	public HeatpumpIdmImpl() {
		super(//
				OpenemsComponent.ChannelId.values(), //
				ModbusComponent.ChannelId.values(), //
				HeatpumpIdm.ChannelId.values() //
		);
	}

	@Activate
	private void activate(ComponentContext context, Config config) throws OpenemsException {
		if (super.activate(context, config.id(), config.alias(), config.enabled(), //
				config.modbusUnitId(), this.cm, "Modbus", config.modbus_id())) {
			return;
		}
	}

	@Override
	@Deactivate
	protected void deactivate() {
		super.deactivate();
	}

	@Override
	protected ModbusProtocol defineModbusProtocol() {
		return new ModbusProtocol(this,

				// Read-only Input Registers
				new FC4ReadInputRegistersTask(1000, Priority.HIGH,
						m(HeatpumpIdm.ChannelId.OUTDOOR_TEMPERATURE,
								new FloatDoublewordElement(1000).wordOrder(LSWMSW))),

				new FC4ReadInputRegistersTask(1050, Priority.HIGH,
						m(HeatpumpIdm.ChannelId.FLOW_TEMPERATURE,
								new FloatDoublewordElement(1050).wordOrder(LSWMSW))),

				new FC4ReadInputRegistersTask(1052, Priority.HIGH,
						m(HeatpumpIdm.ChannelId.RETURN_TEMPERATURE,
								new FloatDoublewordElement(1052).wordOrder(LSWMSW))),

				new FC4ReadInputRegistersTask(1364, Priority.HIGH,
						m(HeatpumpIdm.ChannelId.ROOM_TEMPERATURE,
								new FloatDoublewordElement(1364).wordOrder(LSWMSW))),

				new FC4ReadInputRegistersTask(1378, Priority.HIGH,
						m(HeatpumpIdm.ChannelId.HEATING_CURVE_FLOW_TEMPERATURE_SETPOINT,
								new FloatDoublewordElement(1378).wordOrder(LSWMSW))),

				new FC4ReadInputRegistersTask(4122, Priority.HIGH,
						m(HeatpumpIdm.ChannelId.ELECTRICAL_POWER_CONSUMPTION,
								new FloatDoublewordElement(4122).wordOrder(LSWMSW))),

				new FC4ReadInputRegistersTask(4126, Priority.HIGH,
						m(HeatpumpIdm.ChannelId.THERMAL_POWER,
								new FloatDoublewordElement(4126).wordOrder(LSWMSW))),

				// Read/write Holding Registers
				new FC3ReadRegistersTask(1393, Priority.HIGH,
						m(HeatpumpIdm.ChannelId.OPERATION_MODE,
								new UnsignedWordElement(1393))),

				new FC3ReadRegistersTask(1710, Priority.HIGH,
						m(HeatpumpIdm.ChannelId.HEAT_DEMAND,
								new UnsignedWordElement(1710))),

				new FC3ReadRegistersTask(1449, Priority.HIGH,
						m(HeatpumpIdm.ChannelId.FLOW_TEMPERATURE_SETPOINT,
								new UnsignedWordElement(1449))),

				// Write Holding Registers
				new FC16WriteRegistersTask(1393,
						m(HeatpumpIdm.ChannelId.OPERATION_MODE,
								new UnsignedWordElement(1393))),

			
				new FC16WriteRegistersTask(1710,
						m(HeatpumpIdm.ChannelId.HEAT_DEMAND,
								new UnsignedWordElement(1710))));
	}
}