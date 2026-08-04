package com.optimistopti.clanchat.network;

import com.optimistopti.clanchat.ClanChatMod;
import com.optimistopti.clanchat.client.config.ClanChatConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Простой WebSocket-клиент поверх встроенного в JDK {@link java.net.http.WebSocket}
 * (Java 25, никаких доп. зависимостей не нужно). Держит одно соединение с бэкендом
 * ClanChat (см. /backend в репозитории) — независимо от того, на какой Minecraft-сервер
 * игрок сейчас зашёл поиграть.
 * <p>
 * Протокол — тот же JSON-конверт {@code {"action": "...", "data": {...}}}
 * (см. {@link Envelope}), что раньше гонялся через Fabric Custom Payloads, просто теперь
 * как текстовые WebSocket-фреймы.
 * <p>
 * При обрыве связи (сервер перезапустился, сеть моргнула и т.д.) автоматически
 * переподключается с экспоненциальной задержкой (1с, 2с, 4с, 8с, 16с, дальше — раз в 30с),
 * пока не выключат в настройках или не позовут {@link #disconnect()} явно.
 */
public final class BackendConnection {

	private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.build();

	private static final ScheduledExecutorService RECONNECT_EXECUTOR = Executors.newSingleThreadScheduledExecutor(r -> {
		Thread t = new Thread(r, "ClanChat-Reconnect");
		t.setDaemon(true);
		return t;
	});

	private static volatile WebSocket webSocket;
	private static volatile boolean connected = false;
	private static volatile String currentUri;
	private static volatile boolean autoReconnectEnabled = false;
	private static volatile int reconnectAttempt = 0;
	private static volatile ScheduledFuture<?> pendingReconnect;

	private static Consumer<String> onMessage = json -> {};
	private static Runnable onOpenCallback = () -> {};
	private static Consumer<String> onStatusChange = status -> {};

	private BackendConnection() {
	}

	public static void setOnMessage(Consumer<String> handler) {
		onMessage = handler;
	}

	public static void setOnOpen(Runnable handler) {
		onOpenCallback = handler;
	}

	public static void setOnStatusChange(Consumer<String> handler) {
		onStatusChange = handler;
	}

	public static boolean isConnected() {
		return connected;
	}

	/** Подключиться и включить автопереподключение при обрыве. Явный вызов — сбрасывает бэкофф. */
	public static void connect(String uri) {
		currentUri = uri;
		autoReconnectEnabled = true;
		reconnectAttempt = 0;
		cancelPendingReconnect();
		closeCurrentSocket();
		doConnect(uri);
	}

	/** Отключиться насовсем: закрывает сокет и выключает автопереподключение. */
	public static void disconnect() {
		autoReconnectEnabled = false;
		cancelPendingReconnect();
		closeCurrentSocket();
	}

	public static void send(ClanAction action, Object dataDto) {
		WebSocket ws = webSocket;
		if (ws == null || !connected) {
			ClanChatMod.LOGGER.warn("ClanChat backend: попытка отправить {} без активного соединения", action);
			return;
		}
		Envelope envelope = new Envelope(action, Envelope.toDataObject(dataDto));
		String json = envelope.toJson();
		ClanChatMod.LOGGER.info("ClanChat backend: -> {}", json);
		ws.sendText(json, true);
	}

	// ---------------------------------------------------------------- internals

	private static void doConnect(String uri) {
		onStatusChange.accept("Подключение...");

		StringBuilder buffer = new StringBuilder();

		CompletableFuture<WebSocket> future = HTTP_CLIENT.newWebSocketBuilder()
				.connectTimeout(Duration.ofSeconds(10))
				.buildAsync(URI.create(uri), new WebSocket.Listener() {
					@Override
					public void onOpen(WebSocket ws) {
						// Важно: присваиваем поле здесь же, синхронно, а не только в future.whenComplete
						// ниже — иначе identify() (вызываемый из onOpenCallback) мог попытаться
						// отправить IDENTIFY раньше, чем webSocket вообще будет сохранён, и тихо
						// проигнорировать отправку (см. проверку в send()).
						webSocket = ws;
						connected = true;
						reconnectAttempt = 0;
						ClanChatMod.LOGGER.info("ClanChat backend: соединение установлено ({})", uri);
						onStatusChange.accept("Подключено");
						onOpenCallback.run();
						WebSocket.Listener.super.onOpen(ws);
					}

					@Override
					public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
						buffer.append(data);
						if (last) {
							String message = buffer.toString();
							buffer.setLength(0);
							ClanChatMod.LOGGER.info("ClanChat backend: <- {}", message);
							try {
								onMessage.accept(message);
							} catch (Exception e) {
								ClanChatMod.LOGGER.error("Ошибка обработки сообщения от ClanChat backend", e);
							}
						}
						ws.request(1);
						return null;
					}

					@Override
					public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
						connected = false;
						ClanChatMod.LOGGER.info("ClanChat backend: соединение закрыто ({}: {})", statusCode, reason);
						onStatusChange.accept("Отключено");
						scheduleReconnectIfNeeded();
						return null;
					}

					@Override
					public void onError(WebSocket ws, Throwable error) {
						connected = false;
						ClanChatMod.LOGGER.error("ClanChat backend: ошибка соединения", error);
						onStatusChange.accept("Ошибка: " + error.getMessage());
						scheduleReconnectIfNeeded();
					}
				});

		future.whenComplete((ws, error) -> {
			if (error != null) {
				connected = false;
				ClanChatMod.LOGGER.error("ClanChat backend: не удалось подключиться к {}", uri, error);
				onStatusChange.accept("Не удалось подключиться: " + error.getMessage());
				scheduleReconnectIfNeeded();
				return;
			}
			webSocket = ws;
		});
	}

	private static void scheduleReconnectIfNeeded() {
		if (!autoReconnectEnabled || !ClanChatConfig.INSTANCE.autoConnect || currentUri == null) {
			return;
		}
		if (pendingReconnect != null && !pendingReconnect.isDone()) {
			return; // уже что-то запланировано
		}
		int attempt = reconnectAttempt++;
		long delaySeconds = Math.min(30, 1L << Math.min(attempt, 4)); // 1, 2, 4, 8, 16, дальше 30
		onStatusChange.accept("Переподключение через " + delaySeconds + "с...");
		String uri = currentUri;
		pendingReconnect = RECONNECT_EXECUTOR.schedule(() -> {
			if (autoReconnectEnabled && ClanChatConfig.INSTANCE.autoConnect) {
				doConnect(uri);
			}
		}, delaySeconds, TimeUnit.SECONDS);
	}

	private static void cancelPendingReconnect() {
		ScheduledFuture<?> f = pendingReconnect;
		if (f != null) {
			f.cancel(false);
		}
		pendingReconnect = null;
	}

	private static void closeCurrentSocket() {
		WebSocket ws = webSocket;
		if (ws != null) {
			try {
				ws.sendClose(WebSocket.NORMAL_CLOSURE, "client disconnect");
			} catch (Exception ignored) {
				// соединение уже могло быть мертво — не страшно
			}
		}
		webSocket = null;
		connected = false;
	}
}
