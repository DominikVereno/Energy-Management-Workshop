package io.openems.edge.heatpump.idm;

import java.util.function.Consumer;

import io.openems.common.exceptions.OpenemsException;
import io.openems.edge.common.channel.Channel;
import io.openems.edge.common.channel.WriteChannel;

/**
 * Range check for writable IDM registers.
 *
 * <p>
 * The parameter list states a minimum and a maximum for most writable
 * registers. Without a check a value outside that range reaches the Modbus
 * element, where it is silently truncated to 16 bits, and the write task then
 * fails against the device once per cycle for as long as the value is queued.
 * Rejecting it here keeps it out of the write pipeline altogether and reports
 * the reason through the API worker log.
 */
public final class RegisterRange {

	private RegisterRange() {
	}

	/**
	 * Rejects write values outside the range the register is specified for.
	 *
	 * @param <T>      the channel type
	 * @param register the register label used in the error message
	 * @param min      the lowest accepted value
	 * @param max      the highest accepted value
	 * @return a callback for {@link io.openems.edge.common.channel.Doc#onInit}
	 */
	public static <T> Consumer<Channel<T>> limit(String register, double min, double max) {
		return check(register, min, max);
	}

	/**
	 * Rejects write values that are not a finite number, for registers the
	 * parameter list gives no range for.
	 *
	 * @param <T>      the channel type
	 * @param register the register label used in the error message
	 * @return a callback for {@link io.openems.edge.common.channel.Doc#onInit}
	 */
	public static <T> Consumer<Channel<T>> finite(String register) {
		return check(register, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
	}

	@SuppressWarnings("unchecked")
	private static <T> Consumer<Channel<T>> check(String register, double min, double max) {
		return channel -> ((WriteChannel<T>) channel).onSetNextWrite(value -> {
			if (!(value instanceof Number number)) {
				return;
			}
			var candidate = number.doubleValue();
			if (!Double.isFinite(candidate)) {
				throw new OpenemsException("Value [" + value + "] for [" + register + "] is not a finite number");
			}
			if (candidate < min || candidate > max) {
				throw new OpenemsException(
						"Value [" + value + "] for [" + register + "] is outside [" + min + " .. " + max + "]");
			}
		});
	}
}
