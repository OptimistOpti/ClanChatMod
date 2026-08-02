package com.optimistopti.clanchat.client.gui.widget;

import com.optimistopti.clanchat.chat.Attachment;
import com.optimistopti.clanchat.chat.AttachmentType;
import com.optimistopti.clanchat.chat.ChatMessage;
import com.optimistopti.clanchat.chat.ItemSnapshot;
import com.optimistopti.clanchat.clan.ClanGson;
import com.optimistopti.clanchat.client.config.ClanChatConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.function.Supplier;

/**
 * Скроллируемый список сообщений в стиле мессенджера: имя отправителя (цветное по роли),
 * время, текст, и полноценный рендер вложения под сообщением — иконки предметов (не
 * просто подпись), цифры координат, полоска здоровья.
 * <p>
 * Масштаб ({@link ClanChatConfig#fontScale}) применяется точечно к каждому вызову
 * отрисовки текста/иконки через локальный push/scale/pop матрицы, а не ко всему виджету
 * целиком — это позволяет держать основную раскладку (позиции строк) в обычных пикселях
 * и не переписывать всю геометрию под масштабированную систему координат.
 * <p>
 * NOTE: автопереноса длинных строк по словам всё ещё нет (грубое обрезание с "..."),
 * см. README, раздел "Известные упрощения".
 */
public class MessageListWidget extends AbstractWidget {

	private static final int BASE_LINE_HEIGHT = 12;
	private static final int BASE_ITEM_SIZE = 18;
	private static final int PADDING = 6;
	private static final int GRID_COLUMNS = 9;
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

	private float scale() {
		return Math.max(0.5f, ClanChatConfig.INSTANCE.fontScale);
	}

	private int lineH() {
		return Math.round(BASE_LINE_HEIGHT * scale());
	}

	private int itemSize() {
		return Math.round(BASE_ITEM_SIZE * scale());
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		graphics.fill(getX(), getY(), getX() + width, getY() + height, 0x88101014);

		List<ChatMessage> messages = messagesSupplier.get();
		int contentHeight = 0;
		for (ChatMessage m : messages) {
			contentHeight += blockHeight(m);
		}
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
			int blockHeight = blockHeight(message);
			cursorY -= blockHeight;

			if (cursorY + blockHeight < getY() || cursorY > getY() + height) {
				continue; // за пределами видимой области — не рисуем
			}

			int rowWidth = width - PADDING * 2;
			int y = cursorY;

			String header = message.senderName();
			if (ClanChatConfig.INSTANCE.showTimestamps) {
				header += " §7" + TIME_FORMAT.format(new Date(message.timestampEpochMillis()));
			}
			scaledText(graphics, header, textX, y, message.senderColor() | 0xFF000000, false);
			y += lineH();

			scaledText(graphics, truncate(message.content(), (int) (rowWidth / scale())), textX, y, 0xFFE0E0E0, false);
			y += lineH();

			if (message.attachment() != null) {
				renderAttachment(graphics, message.attachment(), textX, y, rowWidth);
			}
		}
	}

	// ---------------------------------------------------------------- layout

	private int blockHeight(ChatMessage message) {
		int h = lineH() * 2; // заголовок + текст
		Attachment attachment = message.attachment();
		if (attachment == null) {
			return h + 2;
		}
		return h + attachmentHeight(attachment) + 2;
	}

	private int attachmentHeight(Attachment attachment) {
		return switch (attachment.type()) {
			case COORDINATES, HEALTH_STATUS -> lineH();
			case HELD_ITEM -> itemSize();
			case INVENTORY -> lineH() + rowsFor(36) * itemSize();
			case ENDER_CHEST -> lineH() + rowsFor(27) * itemSize();
		};
	}

	private int rowsFor(int slotCount) {
		return (slotCount + GRID_COLUMNS - 1) / GRID_COLUMNS;
	}

	// ---------------------------------------------------------------- attachment rendering

	private void renderAttachment(GuiGraphicsExtractor graphics, Attachment attachment, int x, int y, int maxWidth) {
		try {
			switch (attachment.type()) {
				case COORDINATES -> renderCoordinates(graphics, attachment, x, y);
				case HEALTH_STATUS -> renderHealth(graphics, attachment, x, y);
				case HELD_ITEM -> renderHeldItem(graphics, attachment, x, y);
				case INVENTORY -> renderItemGrid(graphics, attachment, x, y, maxWidth, "Инвентарь");
				case ENDER_CHEST -> renderItemGrid(graphics, attachment, x, y, maxWidth, "Эндер-сундук");
			}
		} catch (Exception e) {
			scaledText(graphics, "\u26A0 не удалось показать вложение", x, y, 0xFFFF5555, false);
		}
	}

	private void renderCoordinates(GuiGraphicsExtractor graphics, Attachment attachment, int x, int y) {
		Attachment.CoordinatesData data = ClanGson.INSTANCE.fromJson(attachment.dataJson(), Attachment.CoordinatesData.class);
		String dim = shortDimensionName(data.dimensionId());
		String text = String.format("\uD83D\uDCCD %s: %.0f, %.0f, %.0f (%s)", data.label(), data.x(), data.y(), data.z(), dim);
		scaledText(graphics, text, x, y, 0xFF6FA8DC, false);
	}

	private String shortDimensionName(String dimensionId) {
		if (dimensionId == null) return "?";
		int colon = dimensionId.indexOf(':');
		String path = colon >= 0 ? dimensionId.substring(colon + 1) : dimensionId;
		return switch (path) {
			case "overworld" -> "верхний мир";
			case "the_nether" -> "нижний мир";
			case "the_end" -> "энд";
			default -> path;
		};
	}

	private void renderHealth(GuiGraphicsExtractor graphics, Attachment attachment, int x, int y) {
		Attachment.HealthStatusData data = ClanGson.INSTANCE.fromJson(attachment.dataJson(), Attachment.HealthStatusData.class);
		int color = data.needsHelp() ? 0xFFFF5555 : 0xFF7CFC00;
		String prefix = data.needsHelp() ? "\u2757 " : "\u2764 ";
		String text = String.format("%sHP: %.0f/%.0f  Броня: %d  Голод: %d", prefix, data.health(), data.maxHealth(), data.armor(), data.foodLevel());
		scaledText(graphics, text, x, y, color, false);
	}

	private void renderHeldItem(GuiGraphicsExtractor graphics, Attachment attachment, int x, int y) {
		ItemSnapshot snapshot = ClanGson.INSTANCE.fromJson(attachment.dataJson(), ItemSnapshot.class);
		if (snapshot.isEmpty()) {
			scaledText(graphics, "(пустая рука)", x, y, 0xFF888888, false);
			return;
		}
		ItemStack stack = toItemStack(snapshot);
		scaledItem(graphics, stack, x, y);
		scaledText(graphics, snapshot.displayName() + (snapshot.count() > 1 ? " x" + snapshot.count() : ""),
				x + itemSize() + 4, y + (itemSize() - lineH()) / 2, 0xFFE0E0E0, false);
	}

	private void renderItemGrid(GuiGraphicsExtractor graphics, Attachment attachment, int x, int y, int maxWidth, String label) {
		ItemSnapshot[] snapshots = ClanGson.INSTANCE.fromJson(attachment.dataJson(), ItemSnapshot[].class);
		scaledText(graphics, label + ":", x, y, 0xFF6FA8DC, false);
		int gridY = y + lineH();
		int col = 0;
		int row = 0;
		int size = itemSize();
		int columns = Math.max(1, Math.min(GRID_COLUMNS, maxWidth / size));
		for (ItemSnapshot snapshot : snapshots) {
			if (snapshot == null || snapshot.isEmpty()) {
				col++;
				if (col >= columns) {
					col = 0;
					row++;
				}
				continue;
			}
			int slotX = x + col * size;
			int slotY = gridY + row * size;
			scaledItem(graphics, toItemStack(snapshot), slotX, slotY);
			col++;
			if (col >= columns) {
				col = 0;
				row++;
			}
		}
	}

	private ItemStack toItemStack(ItemSnapshot snapshot) {
		Identifier id = Identifier.tryParse(snapshot.itemId());
		Item item = id != null ? BuiltInRegistries.ITEM.getValue(id) : null;
		if (item == null) {
			return ItemStack.EMPTY;
		}
		return new ItemStack(item, Math.max(1, snapshot.count()));
	}

	// ---------------------------------------------------------------- scaled draw helpers

	private void scaledText(GuiGraphicsExtractor graphics, String text, int x, int y, int color, boolean shadow) {
		float s = scale();
		if (s == 1.0f) {
			graphics.text(this.font, text, x, y, color, shadow);
			return;
		}
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(s, s);
		graphics.text(this.font, text, 0, 0, color, shadow);
		graphics.pose().popMatrix();
	}

	private void scaledItem(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y) {
		if (stack.isEmpty()) {
			return;
		}
		float s = scale();
		if (s == 1.0f) {
			graphics.fakeItem(stack, x, y);
			graphics.itemDecorations(this.font, stack, x, y);
			return;
		}
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(s, s);
		graphics.fakeItem(stack, 0, 0);
		graphics.itemDecorations(this.font, stack, 0, 0);
		graphics.pose().popMatrix();
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
		scrollOffset -= scrollY * (lineH() * 2);
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
