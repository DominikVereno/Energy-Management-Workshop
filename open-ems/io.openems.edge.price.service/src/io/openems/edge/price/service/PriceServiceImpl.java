package io.openems.edge.price.service;

import static io.openems.common.utils.JsonUtils.getAsDouble;
import static io.openems.common.utils.JsonUtils.getAsJsonArray;
import static io.openems.common.utils.JsonUtils.getAsString;
import static io.openems.common.utils.JsonUtils.parseToJsonObject;

import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.utils.ThreadPoolUtils;
import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.OpenemsComponent;
import okhttp3.OkHttpClient;
import okhttp3.Request;

@Designate(ocd = Config.class, factory = true)
@Component(//
		name = "Price.Service", //
		immediate = true, //
		configurationPolicy = ConfigurationPolicy.REQUIRE //
)
public class PriceServiceImpl extends AbstractOpenemsComponent implements PriceService, OpenemsComponent {

	/** How far ahead of "now" the query window reaches. */
	private static final long LOOKAHEAD_HOURS = 2;

	/**
	 * How often the current slot is re-picked from the already fetched points.
	 * Prices change on a 15-minute grid that the poll interval is not aligned to,
	 * so without this the published price would keep belonging to the slot that was
	 * current at the last fetch.
	 */
	private static final long REFRESH_INTERVAL_SECONDS = 60;

	private final Logger log = LoggerFactory.getLogger(PriceServiceImpl.class);
	private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
	private final OkHttpClient client = new OkHttpClient();

	private Config config = null;
	private volatile List<PricePoint> points = List.of();

	public PriceServiceImpl() {
		super(//
				OpenemsComponent.ChannelId.values(), //
				PriceService.ChannelId.values() //
		);
	}

	@Activate
	private void activate(ComponentContext context, Config config) {
		super.activate(context, config.id(), config.alias(), config.enabled());

		if (!this.isEnabled()) {
			return;
		}

		this.config = config;
		this.executor.schedule(this.fetchTask, 0, TimeUnit.SECONDS);
		this.executor.schedule(this.refreshTask, REFRESH_INTERVAL_SECONDS, TimeUnit.SECONDS);
	}

	@Override
	@Deactivate
	protected void deactivate() {
		super.deactivate();
		ThreadPoolUtils.shutdownAndAwaitTermination(this.executor, 0);
	}

	/**
	 * Fetches a fresh window of price points. A failed fetch keeps the points
	 * already held: they carry their own validity and expire on their own, so a
	 * single failed request must not drop a price that is still valid.
	 */
	private final Runnable fetchTask = () -> {
		try {
			this.points = this.fetchPoints();
			this._setCommunicationFailed(false);
		} catch (IOException | OpenemsNamedException | RuntimeException e) {
			this.logWarn(this.log, "Failed to fetch price: " + e.getMessage());
			this._setCommunicationFailed(true);
		}

		this.refreshCurrentPrice();
		this.scheduleNext(this.fetchTask, TimeUnit.MINUTES.toSeconds(Math.max(1, this.config.pollIntervalMinutes())));
	};

	private final Runnable refreshTask = () -> {
		this.refreshCurrentPrice();
		this.scheduleNext(this.refreshTask, REFRESH_INTERVAL_SECONDS);
	};

	private List<PricePoint> fetchPoints() throws IOException, OpenemsNamedException {
		var url = buildUrl(this.config.baseUrl(), this.config.provider(), this.config.zone(), Instant.now());
		var request = new Request.Builder().url(url).build();

		try (var response = this.client.newCall(request).execute()) {
			if (!response.isSuccessful()) {
				throw new IOException("Unexpected code " + response);
			}
			return parsePoints(response.body().string());
		}
	}

	/**
	 * Publishes the price of the slot covering "now". Holding points but having
	 * none that covers "now" is a data gap, not a communication problem, and is
	 * reported separately so the two are not confused during diagnosis.
	 */
	private void refreshCurrentPrice() {
		var price = pickPrice(this.points, Instant.now());
		this._setCurrentPrice(price == null ? null : price.floatValue());
		this._setNoPriceForCurrentTime(price == null && !this.points.isEmpty());
	}

	/**
	 * Re-schedules a task, tolerating a shutdown that happened in the meantime:
	 * deactivate() does not wait for a running task, so the task can outlive the
	 * executor it wants to schedule itself on.
	 */
	private void scheduleNext(Runnable task, long delaySeconds) {
		try {
			this.executor.schedule(task, delaySeconds, TimeUnit.SECONDS);
		} catch (RejectedExecutionException e) {
			// component is shutting down
		}
	}

	/**
	 * Builds the price-service request URL for the window
	 * [top of the current hour, now + {@value #LOOKAHEAD_HOURS}h).
	 *
	 * <p>
	 * The start is floored to the current hour because the price-service only
	 * includes a full hour when {@code start} is at or before that hour's top;
	 * a start in the middle of the current hour would drop the slot covering
	 * "now" and yield no current price.
	 *
	 * @param baseUrl  base URL of the price-service
	 * @param provider the provider name
	 * @param zone     the market zone
	 * @param now      the current instant
	 * @return the request URL
	 */
	public static String buildUrl(String baseUrl, String provider, String zone, Instant now) {
		var start = format(now.truncatedTo(ChronoUnit.HOURS));
		var end = format(now.plusSeconds(LOOKAHEAD_HOURS * 3600));
		return removeTrailingSlash(baseUrl) //
				+ "/prices?provider=" + provider //
				+ "&zone=" + zone //
				+ "&start=" + start //
				+ "&end=" + end //
				+ "&timeframe=15min";
	}

	private static String format(Instant instant) {
		return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
	}

	/**
	 * Parses the price-service JSON response into its price points.
	 *
	 * @param jsonData the price-service JSON response
	 * @return the price points, in the order delivered
	 * @throws OpenemsNamedException on invalid JSON
	 */
	public static List<PricePoint> parsePoints(String jsonData) throws OpenemsNamedException {
		var elements = getAsJsonArray(parseToJsonObject(jsonData), "points");
		var points = new ArrayList<PricePoint>();
		for (var element : elements) {
			points.add(new PricePoint(//
					OffsetDateTime.parse(getAsString(element, "start")).toInstant(), //
					OffsetDateTime.parse(getAsString(element, "end")).toInstant(), //
					getAsDouble(element, "price_eur_per_kwh")));
		}
		return points;
	}

	/**
	 * Picks the price of the point covering {@code now}.
	 *
	 * @param points the price points to pick from
	 * @param now    the instant to find the price for
	 * @return the price in EUR/kWh, or {@code null} if no point covers {@code now}
	 */
	public static Double pickPrice(List<PricePoint> points, Instant now) {
		return points.stream() //
				.filter(point -> point.covers(now)) //
				.map(PricePoint::price) //
				.findFirst() //
				.orElse(null);
	}

	private static String removeTrailingSlash(String value) {
		return value.endsWith("/") //
				? value.substring(0, value.length() - 1) //
				: value;
	}

	@Override
	public String debugLog() {
		return "Preis:" + this.getCurrentPrice().asString();
	}

}
