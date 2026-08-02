package com.optimistopti.clanchat.client.gui;

import com.optimistopti.clanchat.client.config.ClanChatConfig;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

/**
 * Небольшие всплывающие плашки "новое сообщение" в углу экрана — показываются только
 * когда {@link ClanChatScreen} не открыт (иначе сообщение и так видно в самом чате).
 * Регистрируется как HUD-слой перед ванильным чатом, см. {@code HudElementRegistry}
 * в {@link com.optimistopti.clanchat.client.ClanChatModClient}.
 */
public final class ChatToastRenderer {

	private static final long DURATION_MS = 5000;
	private static final int MAX_VISIBLE = 3;
	private static final int BOX_WIDTH = 200;
	private static final int BOX_HEIGHT = 28;
	private static final int GAP = 4;
	private static final int MARGIN = 8;

	private record Toast(String sender, String preview, long expiresAt) {
	}

	private static final Deque<Toast> ACTIVE = new ArrayDeque<>();

	private ChatToastRenderer() {
	}

	public static void push(String sender, String content) {
		String preview = content.length() > 40 ? content.substring(0, 40) + "..." : content;
		ACTIVE.addFirst(new Toast(sender, preview, System.currentTimeMillis() + DURATION_MS));
		while (ACTIVE.size() > MAX_VISIBLE) {
			ACTIVE.removeLast();
		}
	}

	public static void render(GuiGraphicsExtractor graphics, DeltaTracker tickCounter) {
		if (!ClanChatConfig.INSTANCE.showPopupNotifications || ACTIVE.isEmpty()) {
			return;
		}
		// Пока открыт сам чат клана — уведомления ни к чему, сообщение и так на экране.
		if (Minecraft.getInstance().screen instanceof ClanChatScreen) {
			return;
		}

		long now = System.currentTimeMillis();
		Iterator<Toast> it = ACTIVE.iterator();
		while (it.hasNext()) {
			if (it.next().expiresAt() <= now) {
				it.remove();
			}
		}
		if (ACTIVE.isEmpty()) {
			return;
		}

		float scale = Math.max(0.5f, ClanChatConfig.INSTANCE.popupScale);
		int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
		int screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
		boolean top = ClanChatConfig.INSTANCE.popupPosition.startsWith("TOP");
		boolean left = ClanChatConfig.INSTANCE.popupPosition.endsWith("LEFT");

		int boxW = Math.round(BOX_WIDTH * scale);
		int boxH = Math.round(BOX_HEIGHT * scale);
		int gap = Math.round(GAP * scale);

		int x = left ? MARGIN : screenWidth - boxW - MARGIN;
		int y = top ? MARGIN : screenHeight - boxH - MARGIN;
		int step = top ? (boxH + gap) : -(boxH + gap);

		int i = 0;
		for (Toast toast : ACTIVE) {
			int boxY = y + step * i;
			graphics.fill(x, boxY, x + boxW, boxY + boxH, 0xCC101418);
			drawScaled(graphics, "\u2709 " + toast.sender(), x + Math.round(6 * scale), boxY + Math.round(5 * scale), 0xFF6FA8DC, scale);
			drawScaled(graphics, toast.preview(), x + Math.round(6 * scale), boxY + Math.round(16 * scale), 0xFFE0E0E0, scale);
			i++;
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
