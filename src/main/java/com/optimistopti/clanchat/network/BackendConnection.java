package com.optimistopti.clanchat.network;

import com.optimistopti.clanchat.ClanChatMod;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
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
 */
public final class BackendConnection {

	private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.build();

	private static volatile WebSocket webSocket;
	private static volatile boolean connected = false;
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

	public static void connect(String uri) {
		disconnect();
		onStatusChange.accept("Подключение...");

		StringBuilder buffer = new StringBuilder();

		CompletableFuture<WebSocket> future = HTTP_CLIENT.newWebSocketBuilder()
				.connectTimeout(Duration.ofSeconds(10))
				.buildAsync(URI.create(uri), new WebSocket.Listener() {
					@Override
					public void onOpen(WebSocket ws) {
						connected = true;
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
						return null;
					}

					@Override
					public void onError(WebSocket ws, Throwable error) {
						connected = false;
						ClanChatMod.LOGGER.error("ClanChat backend: ошибка соединения", error);
						onStatusChange.accept("Ошибка: " + error.getMessage());
					}
				});

		future.whenComplete((ws, error) -> {
			if (error != null) {
				connected = false;
				ClanChatMod.LOGGER.error("ClanChat backend: не удалось подключиться к {}", uri, error);
				onStatusChange.accept("Не удалось подключиться: " + error.getMessage());
				return;
			}
			webSocket = ws;
		});
	}

	public static void disconnect() {
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

	public static void send(ClanAction action, Object dataDto) {
		WebSocket ws = webSocket;
		if (ws == null || !connected) {
			ClanChatMod.LOGGER.warn("ClanChat backend: попытка отправить {} без активного соединения", action);
			return;
		}
		Envelope envelope = new Envelope(action, Envelope.toDataObject(dataDto));
		ws.sendText(envelope.toJson(), true);
	}
}
