package io.openems.edge.controller.heatpump.idm;

import io.openems.common.types.OptionsEnum;

public enum Mode implements OptionsEnum {

	NORMAL(0, "Normal"), //
	MANUAL(1, "Manual");

	private final int value;
	private final String name;

	private Mode(int value, String name) {
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