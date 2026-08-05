package com.optimistopti.clanchat.client.gui.widget;

import com.optimistopti.clanchat.chat.Attachment;
import com.optimistopti.clanchat.chat.AttachmentType;
import com.optimistopti.clanchat.chat.ChatMessage;
import com.optimistopti.clanchat.chat.ItemSnapshot;
import com.optimistopti.clanchat.clan.ClanGson;
import com.optimistopti.clanchat.client.config.ClanChatConfig;
import net.minecraft.client.gui.Font;
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
 * Скроллируемый список сообщений в стиле мессенджера. Вложения рендерятся по-настоящему
 * (иконки предметов через {@code graphics.fakeItem(...)}, а не просто текстовой подписью),
 * длинные сообщения переносятся по словам ({@link #wrapText}).
 */
public class MessageListWidget extends AbstractWidget {

	private static final int LINE_HEIGHT = 12;
	private static final int PADDING = 6;
	private static final int ITEM_SIZE = 18;
	private static final int GRID_COLUMNS = 9;
	private static final int SCREENSHOT_BOX_WIDTH = 100;
	private static final int SCREENSHOT_BOX_HEIGHT = 56;
	private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm");

	private record ScreenshotHit(int x0, int y0, int x1, int y1, String dataJson) {
	}

	private final List<ScreenshotHit> screenshotHits = new java.util.ArrayList<>();

	private final Supplier<List<ChatMessage>> messagesSupplier;
	private final Font font;
	private double scrollOffset = 0;

	public MessageListWidget(int x, int y, int width, int height, Font font,
							  Supplier<List<ChatMessage>> messagesSupplier) {
		super(x, y, width, height, Component.empty());
		this.font = font;
		this.messagesSupplier = messagesSupplier;
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		graphics.fill(getX(), getY(), getX() + width, getY() + height, 0x88101014);
		screenshotHits.clear();

		float scale = ClanChatConfig.INSTANCE.fontScale;
		int localWidth = (int) (width / scale);
		int localHeight = (int) (height / scale);

		List<ChatMessage> messages = messagesSupplier.get();
		int contentHeight = 0;
		for (ChatMessage m : messages) {
			contentHeight += blockHeight(m, localWidth - PADDING * 2);
		}
		double maxScroll = Math.max(0, contentHeight - localHeight);
		if (scrollOffset > maxScroll) {
			scrollOffset = maxScroll;
		}
		if (scrollOffset < 0) {
			scrollOffset = 0;
		}

		graphics.pose().pushMatrix();
		graphics.pose().translate(getX(), getY());
		graphics.pose().scale(scale, scale);

		int textX = PADDING;
		int cursorY = localHeight - PADDING + (int) scrollOffset;

		// Рисуем снизу вверх (последнее сообщение внизу, как в любом мессенджере).
		for (int i = messages.size() - 1; i >= 0; i--) {
			ChatMessage message = messages.get(i);
			int height = blockHeight(message, localWidth - PADDING * 2);
			cursorY -= height;

			if (cursorY + height < 0 || cursorY > localHeight) {
				continue; // за пределами видимой области — не рисуем
			}

			String header = message.senderName();
			if (ClanChatConfig.INSTANCE.showTimestamps) {
				header += " §7" + TIME_FORMAT.format(new Date(message.timestampEpochMillis()));
			}
			graphics.text(this.font, header, textX, cursorY, message.senderColor() | 0xFF000000, false);

			List<String> lines = wrapText(message.content(), localWidth - PADDING * 2);
			int lineY = cursorY + LINE_HEIGHT;
			for (String line : lines) {
				graphics.text(this.font, line, textX, lineY, 0xFFE0E0E0, false);
				lineY += LINE_HEIGHT;
			}

			if (message.attachment() != null) {
				renderAttachment(graphics, message.attachment(), textX, lineY, localWidth, scale);
			}
		}

		graphics.pose().popMatrix();
	}

	/** Полная высота блока сообщения (в "локальных", ещё не отмасштабленных, пикселях). */
	private int blockHeight(ChatMessage message, int contentWidth) {
		int lineCount = Math.max(1, wrapText(message.content(), contentWidth).size());
		int base = LINE_HEIGHT + lineCount * LINE_HEIGHT + 2;
		Attachment attachment = message.attachment();
		if (attachment == null) {
			return base;
		}
		return switch (attachment.type()) {
			case COORDINATES, HEALTH_STATUS -> base + LINE_HEIGHT + 2;
			case HELD_ITEM -> base + ITEM_SIZE + 2;
			case INVENTORY -> base + rows(36) * ITEM_SIZE + LINE_HEIGHT + 4;
			case ENDER_CHEST -> base + rows(27) * ITEM_SIZE + LINE_HEIGHT + 4;
			case SCREENSHOT -> base + SCREENSHOT_BOX_HEIGHT + 2;
		};
	}

	/**
	 * Простой жадный перенос по словам (без учёта форматирования Component/§-кодов внутри
	 * слова — для чата этого достаточно). Слишком длинные "слова" без пробелов режутся
	 * посимвольно, чтобы не вылезать за границу виджета.
	 */
	private List<String> wrapText(String text, int maxWidth) {
		List<String> lines = new java.util.ArrayList<>();
		if (text == null || text.isEmpty()) {
			return lines;
		}
		maxWidth = Math.max(20, maxWidth);
		for (String paragraph : text.split("\n", -1)) {
			StringBuilder currentLine = new StringBuilder();
			for (String word : paragraph.split(" ", -1)) {
				String candidate = currentLine.isEmpty() ? word : currentLine + " " + word;
				if (this.font.width(candidate) <= maxWidth) {
					currentLine = new StringBuilder(candidate);
					continue;
				}
				if (!currentLine.isEmpty()) {
					lines.add(currentLine.toString());
					currentLine = new StringBuilder();
				}
				// Само слово шире доступной ширины — режем посимвольно.
				while (this.font.width(word) > maxWidth) {
					int cut = 1;
					while (cut < word.length() && this.font.width(word.substring(0, cut + 1)) <= maxWidth) {
						cut++;
					}
					lines.add(word.substring(0, cut));
					word = word.substring(cut);
				}
				currentLine = new StringBuilder(word);
			}
			lines.add(currentLine.toString());
		}
		return lines;
	}

	private int rows(int slotCount) {
		return (int) Math.ceil(slotCount / (double) GRID_COLUMNS);
	}

	private void renderAttachment(GuiGraphicsExtractor graphics, Attachment attachment, int x, int y, int maxWidth, float scale) {
		try {
			switch (attachment.type()) {
				case COORDINATES -> renderCoordinates(graphics, attachment, x, y);
				case HEALTH_STATUS -> renderHealth(graphics, attachment, x, y);
				case HELD_ITEM -> renderHeldItem(graphics, attachment, x, y);
				case INVENTORY -> renderItemGrid(graphics, attachment, x, y, "Инвентарь");
				case ENDER_CHEST -> renderItemGrid(graphics, attachment, x, y, "Эндер-сундук");
				case SCREENSHOT -> renderScreenshotPlaceholder(graphics, attachment, x, y, scale);
			}
		} catch (Exception e) {
			// Повреждённое/несовместимое вложение — не роняем весь рендер списка сообщений.
			graphics.text(this.font, "[не удалось показать вложение]", x, y, 0xFFFF5555, false);
		}
	}

	private void renderScreenshotPlaceholder(GuiGraphicsExtractor graphics, Attachment attachment, int x, int y, float scale) {
		graphics.fill(x, y, x + SCREENSHOT_BOX_WIDTH, y + SCREENSHOT_BOX_HEIGHT, 0xFF2A2A30);
		graphics.text(this.font, "\uD83D\uDCF7 Скриншот", x + 6, y + SCREENSHOT_BOX_HEIGHT / 2 - 10, 0xFF6FA8DC, false);
		graphics.text(this.font, "(нажми)", x + 6, y + SCREENSHOT_BOX_HEIGHT / 2 + 2, 0xFFAAAAAA, false);

		// Координаты клика запоминаем уже в экранном (немасштабированном) пространстве,
		// т.к. mouseClicked получает абсолютные координаты мыши, а не "локальные".
		int screenX0 = getX() + Math.round(x * scale);
		int screenY0 = getY() + Math.round(y * scale);
		int screenX1 = getX() + Math.round((x + SCREENSHOT_BOX_WIDTH) * scale);
		int screenY1 = getY() + Math.round((y + SCREENSHOT_BOX_HEIGHT) * scale);
		screenshotHits.add(new ScreenshotHit(screenX0, screenY0, screenX1, screenY1, attachment.dataJson()));
	}

	private void renderCoordinates(GuiGraphicsExtractor graphics, Attachment attachment, int x, int y) {
		Attachment.CoordinatesData data = ClanGson.INSTANCE.fromJson(attachment.dataJson(), Attachment.CoordinatesData.class);
		String dim = shortDimensionName(data.dimensionId());
		String text = String.format("\uD83D\uDCCD %.0f, %.0f, %.0f (%s)", data.x(), data.y(), data.z(), dim);
		graphics.text(this.font, text, x, y, 0xFF6FA8DC, false);
	}

	private void renderHealth(GuiGraphicsExtractor graphics, Attachment attachment, int x, int y) {
		Attachment.HealthStatusData data = ClanGson.INSTANCE.fromJson(attachment.dataJson(), Attachment.HealthStatusData.class);
		String text = String.format("\u2764 %.0f/%.0f  \u26E8 %d  \uD83C\uDF56 %d", data.health(), data.maxHealth(), data.armor(), data.foodLevel());
		int color = data.needsHelp() ? 0xFFFF5555 : 0xFF6FA8DC;
		if (data.needsHelp()) {
			text += "  \u26A0 нужна помощь!";
		}
		graphics.text(this.font, text, x, y, color, false);
	}

	private void renderHeldItem(GuiGraphicsExtractor graphics, Attachment attachment, int x, int y) {
		ItemSnapshot snapshot = ClanGson.INSTANCE.fromJson(attachment.dataJson(), ItemSnapshot.class);
		ItemStack stack = toItemStack(snapshot);
		graphics.fakeItem(stack, x, y);
		graphics.itemDecorations(this.font, stack, x, y);
		graphics.text(this.font, snapshot.isEmpty() ? "Пусто" : snapshot.displayName(),
				x + ITEM_SIZE + 4, y + ITEM_SIZE / 2 - 4, 0xFFE0E0E0, false);
	}

	private void renderItemGrid(GuiGraphicsExtractor graphics, Attachment attachment, int x, int y, String label) {
		ItemSnapshot[] snapshots = ClanGson.INSTANCE.fromJson(attachment.dataJson(), ItemSnapshot[].class);
		graphics.text(this.font, label + " (" + nonEmptyCount(snapshots) + "/" + snapshots.length + "):", x, y, 0xFF6FA8DC, false);
		int gridY = y + LINE_HEIGHT + 2;
		for (int i = 0; i < snapshots.length; i++) {
			ItemSnapshot snap = snapshots[i];
			if (snap == null || snap.isEmpty()) {
				continue;
			}
			int col = i % GRID_COLUMNS;
			int row = i / GRID_COLUMNS;
			int itemX = x + col * ITEM_SIZE;
			int itemY = gridY + row * ITEM_SIZE;
			ItemStack stack = toItemStack(snap);
			graphics.fakeItem(stack, itemX, itemY);
			graphics.itemDecorations(this.font, stack, itemX, itemY);
		}
	}

	private int nonEmptyCount(ItemSnapshot[] snapshots) {
		int count = 0;
		for (ItemSnapshot s : snapshots) {
			if (s != null && !s.isEmpty()) count++;
		}
		return count;
	}

	private ItemStack toItemStack(ItemSnapshot snapshot) {
		if (snapshot == null || snapshot.isEmpty()) {
			return ItemStack.EMPTY;
		}
		try {
			Identifier id = Identifier.parse(snapshot.itemId());
			Item item = BuiltInRegistries.ITEM.getValue(id);
			if (item == null) {
				return ItemStack.EMPTY;
			}
			return new ItemStack(item, Math.max(1, snapshot.count()));
		} catch (Exception e) {
			return ItemStack.EMPTY;
		}
	}

	private String shortDimensionName(String dimensionId) {
		if (dimensionId == null) return "?";
		return switch (dimensionId) {
			case "minecraft:overworld" -> "Верхний мир";
			case "minecraft:the_nether" -> "Незер";
			case "minecraft:the_end" -> "Энд";
			default -> dimensionId;
		};
	}

	@Override
	public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
		double mouseX = event.x();
		double mouseY = event.y();
		for (ScreenshotHit hit : screenshotHits) {
			if (mouseX >= hit.x0() && mouseX <= hit.x1() && mouseY >= hit.y0() && mouseY <= hit.y1()) {
				try {
					Attachment.ScreenshotData data = ClanGson.INSTANCE.fromJson(hit.dataJson(), Attachment.ScreenshotData.class);
					net.minecraft.client.Minecraft.getInstance().setScreen(
							new com.optimistopti.clanchat.client.gui.ScreenshotViewScreen(
									net.minecraft.client.Minecraft.getInstance().screen, data));
				} catch (Exception e) {
					com.optimistopti.clanchat.ClanChatMod.LOGGER.error("Не удалось открыть скриншот", e);
				}
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		// Колёсико "вверх" (scrollY > 0) уводит в историю — раскрывает более старые сообщения.
		scrollOffset += scrollY * (LINE_HEIGHT * 2);
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
