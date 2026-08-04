package com.optimistopti.clanchat.chat;

/**
 * Вложение к сообщению чата. {@code dataJson} — сериализованные через {@link com.optimistopti.clanchat.clan.ClanGson}
 * данные, конкретная форма зависит от {@link #type}:
 * <ul>
 *   <li>{@code COORDINATES} -> {@link CoordinatesData}</li>
 *   <li>{@code HELD_ITEM} -> {@link ItemSnapshot}</li>
 *   <li>{@code INVENTORY} / {@code ENDER_CHEST} -> {@code ItemSnapshot[]}</li>
 *   <li>{@code HEALTH_STATUS} -> {@link HealthStatusData}</li>
 *   <li>{@code SCREENSHOT} -> {@link ScreenshotData}</li>
 * </ul>
 */
public record Attachment(AttachmentType type, String dataJson) {

	public record CoordinatesData(String label, String dimensionId, double x, double y, double z) {
	}

	public record HealthStatusData(float health, float maxHealth, int armor, int foodLevel, boolean needsHelp) {
	}

	/** {@code imageBase64} — PNG, уже уменьшенный клиентом перед отправкой (см. ScreenshotCapture). */
	public record ScreenshotData(String imageBase64, int width, int height) {
	}
}
