package io.openems.edge.controller.heatpump.idm;

import io.openems.common.types.OptionsEnum;

/**
 * Operating mode of heating circuit A (Reg1393).
 */
public enum CircuitMode implements OptionsEnum {

	OFF(0, "Aus"), //
	TIME_PROGRAM(1, "Zeitprogramm"), //
	NORMAL(2, "Normal"), //
	ECO(3, "Eco"), //
	MANUAL_HEATING(4, "Manuell Heizen"), //
	MANUAL_COOLING(5, "Manuell Kuehlen");

	private final int value;
	private final String name;

	private CircuitMode(int value, String name) {
		this.value = value;
		this.name = name;
	}

	@Override
	public int getValue() {
		return this.value;
	}

	@Override
	public String getName() {
		return this.name;
	}

	@Override
	public OptionsEnum getUndefined() {
		return NORMAL;
	}
}
