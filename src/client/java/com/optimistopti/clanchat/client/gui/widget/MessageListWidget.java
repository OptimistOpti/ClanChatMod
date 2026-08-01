package com.optimistopti.clanchat.client.gui.widget;

import com.optimistopti.clanchat.chat.ChatMessage;
import com.optimistopti.clanchat.client.config.ClanChatConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.function.Supplier;

/**
 * Простой скроллируемый список сообщений в стиле мессенджера: имя отправителя (цветное
 * по роли) + время + текст, вложение показывается отдельной строкой-плашкой под текстом.
 * <p>
 * NOTE: для простоты здесь нет автопереноса длинных строк по словам (Font#split) —
 * длинные сообщения обрезаются с "...". Это несложно доработать напильником в IDE,
 * если понадобится (см. README, раздел "Известные упрощения").
 */
public class MessageListWidget extends AbstractWidget {

	private static final int LINE_HEIGHT = 12;
	private static final int PADDING = 6;
	private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm");

	private final Supplier<List<ChatMessage>> messagesSupplier;
	private final net.minecraft.client.gui.Font font;
	private double scrollOffset = 0;

	public MessageListWidget(int x, int y, int width, int height, net.minecraft.client.gui.Font font,
							  Supplier<List<ChatMessage>> messagesSupplier) {
		super(x, y, width, height, Component.empty());
		this.font = font;
		this.messagesSupplier = messagesSupplier;
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		graphics.fill(getX(), getY(), getX() + width, getY() + height, 0x88101014);

		List<ChatMessage> messages = messagesSupplier.get();
		int contentHeight = messages.size() * (LINE_HEIGHT * 2 + 2);
		double maxScroll = Math.max(0, contentHeight - height);
		if (scrollOffset > maxScroll) {
			scrollOffset = maxScroll;
		}
		if (scrollOffset < 0) {
			scrollOffset = 0;
		}

		int textX = getX() + PADDING;
		int cursorY = getY() + height - PADDING - (int) scrollOffset;

		// Рисуем снизу вверх (последнее сообщение внизу, как в любом мессенджере).
		for (int i = messages.size() - 1; i >= 0; i--) {
			ChatMessage message = messages.get(i);
			int blockHeight = LINE_HEIGHT * 2 + (message.attachment() != null ? LINE_HEIGHT + 2 : 0);
			cursorY -= blockHeight;

			if (cursorY + blockHeight < getY() || cursorY > getY() + height) {
				continue; // за пределами видимой области — не рисуем
			}

			String header = message.senderName();
			if (ClanChatConfig.INSTANCE.showTimestamps) {
				header += " §7" + TIME_FORMAT.format(new Date(message.timestampEpochMillis()));
			}
			graphics.text(this.font, header, textX, cursorY, message.senderColor() | 0xFF000000, false);
			graphics.text(this.font, truncate(message.content(), width - PADDING * 2),
					textX, cursorY + LINE_HEIGHT, 0xFFE0E0E0, false);

			if (message.attachment() != null) {
				String label = "\u2B1B " + attachmentLabel(message.attachment().type());
				graphics.text(this.font, label, textX, cursorY + LINE_HEIGHT * 2, 0xFF6FA8DC, false);
			}
		}
	}

	private String attachmentLabel(com.optimistopti.clanchat.chat.AttachmentType type) {
		return switch (type) {
			case COORDINATES -> "Координаты";
			case HELD_ITEM -> "Предмет в руке";
			case INVENTORY -> "Инвентарь";
			case ENDER_CHEST -> "Эндер-сундук";
			case HEALTH_STATUS -> "Статус здоровья";
		};
	}

	private String truncate(String text, int maxWidth) {
		if (this.font.width(text) <= maxWidth) {
			return text;
		}
		String suffix = "...";
		StringBuilder builder = new StringBuilder();
		for (char c : text.toCharArray()) {
			if (this.font.width(builder.toString() + c + suffix) > maxWidth) {
				break;
			}
			builder.append(c);
		}
		return builder + suffix;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		scrollOffset -= scrollY * (LINE_HEIGHT * 2);
		if (scrollOffset < 0) {
			scrollOffset = 0;
		}
		return true;
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput builder) {
		builder.add(net.minecraft.client.gui.narration.NarratedElementType.TITLE, Component.literal("Список сообщений чата клана"));
	}
}
