package com.optimistopti.clanchat.client.gui;

import com.optimistopti.clanchat.client.config.ClanChatConfig;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Уведомление о новых сообщениях, когда {@link ClanChatScreen} не открыт: сначала
 * несколько секунд показывается полная плашка с последним сообщением, затем она
 * схлопывается в маленькую иконку со счётчиком непрочитанных — и остаётся, пока игрок
 * не откроет чат (тогда счётчик сбрасывается, сообщения же считаются просмотренными).
 * <p>
 * Регистрируется как HUD-слой перед ванильным чатом, см. {@code HudElementRegistry}
 * в {@link com.optimistopti.clanchat.client.ClanChatModClient}.
 */
public final class ChatToastRenderer {

	private static final long FULL_DISPLAY_MS = 4000;
	private static final int BOX_WIDTH = 200;
	private static final int BOX_HEIGHT = 28;
	private static final int BADGE_SIZE = 20;
	private static final int MARGIN = 8;

	private static int unreadCount = 0;
	private static String lastSender = "";
	private static String lastPreview = "";
	private static long fullDisplayUntil = 0;

	private ChatToastRenderer() {
	}

	public static void push(String sender, String content) {
		unreadCount++;
		lastSender = sender;
		lastPreview = content.length() > 40 ? content.substring(0, 40) + "..." : content;
		fullDisplayUntil = System.currentTimeMillis() + FULL_DISPLAY_MS;
	}

	/** Вызывается явно из {@link ClanChatScreen#init()} — гарантированно срабатывает при
	 * открытии чата, независимо от того, продолжает ли вообще рендериться HUD-слой,
	 * пока открыт GUI-экран (это по факту не гарантировано ни в одну, ни в другую сторону). */
	public static void onChatOpened() {
		unreadCount = 0;
		fullDisplayUntil = 0;
	}

	public static void render(GuiGraphicsExtractor graphics, DeltaTracker tickCounter) {
		// Чат открыт — все сообщения и так на виду, считаем прочитанными и ничего не рисуем.
		if (Minecraft.getInstance().screen instanceof ClanChatScreen) {
			if (unreadCount > 0) {
				onChatOpened();
			}
			return;
		}
		if (!ClanChatConfig.INSTANCE.showPopupNotifications || unreadCount == 0) {
			return;
		}

		float scale = Math.max(0.5f, ClanChatConfig.INSTANCE.popupScale);
		int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
		int screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
		boolean top = ClanChatConfig.INSTANCE.popupPosition.startsWith("TOP");
		boolean left = ClanChatConfig.INSTANCE.popupPosition.endsWith("LEFT");

		boolean showFull = System.currentTimeMillis() < fullDisplayUntil;

		if (showFull) {
			int boxW = Math.round(BOX_WIDTH * scale);
			int boxH = Math.round(BOX_HEIGHT * scale);
			int x = left ? MARGIN : screenWidth - boxW - MARGIN;
			int y = top ? MARGIN : screenHeight - boxH - MARGIN;

			graphics.fill(x, y, x + boxW, y + boxH, 0xCC101418);
			String header = unreadCount > 1 ? ("\u2709 " + lastSender + " (+" + (unreadCount - 1) + ")") : "\u2709 " + lastSender;
			drawScaled(graphics, header, x + Math.round(6 * scale), y + Math.round(5 * scale), 0xFF6FA8DC, scale);
			drawScaled(graphics, lastPreview, x + Math.round(6 * scale), y + Math.round(16 * scale), 0xFFE0E0E0, scale);
		} else {
			int badgeSize = Math.round(BADGE_SIZE * scale);
			int x = left ? MARGIN : screenWidth - badgeSize - MARGIN;
			int y = top ? MARGIN : screenHeight - badgeSize - MARGIN;

			graphics.fill(x, y, x + badgeSize, y + badgeSize, 0xCC101418);
			String count = unreadCount > 99 ? "99+" : String.valueOf(unreadCount);
			String label = "\u2709" + count;
			int textWidth = Minecraft.getInstance().font.width(label);
			int textX = x + (badgeSize - Math.round(textWidth * scale)) / 2;
			int textY = y + Math.round((badgeSize / scale - 8) / 2 * scale);
			drawScaled(graphics, label, textX, textY, 0xFFFF5555, scale);
		}
	}

	private static void drawScaled(GuiGraphicsExtractor graphics, String text, int x, int y, int color, float scale) {
		var font = Minecraft.getInstance().font;
		if (scale == 1.0f) {
			graphics.text(font, text, x, y, color, true);
			return;
		}
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(scale, scale);
		graphics.text(font, text, 0, 0, color, true);
		graphics.pose().popMatrix();
	}
}
