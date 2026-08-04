package com.optimistopti.clanchat.client.gui;

import com.optimistopti.clanchat.chat.Attachment;
import com.optimistopti.clanchat.chat.ItemSnapshot;
import com.optimistopti.clanchat.clan.ClanGson;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Собирает JSON-полезную нагрузку вложений из клиентского состояния игрока.
 * Сервер сохранённое здесь НЕ разбирает и НЕ валидирует по существу (кроме длины) —
 * это осознанно: вложение это превью для отображения в чате, а не способ передать
 * предметы другому игроку.
 */
public final class AttachmentBuilder {

	private AttachmentBuilder() {
	}

	public static Attachment coordinates(String label) {
		Player player = Minecraft.getInstance().player;
		if (player == null) {
			return null;
		}
		String dimensionId = player.level().dimension().identifier().toString();
		Attachment.CoordinatesData data = new Attachment.CoordinatesData(
				label, dimensionId, player.getX(), player.getY(), player.getZ());
		return new Attachment(com.optimistopti.clanchat.chat.AttachmentType.COORDINATES, ClanGson.INSTANCE.toJson(data));
	}

	public static Attachment heldItem() {
		Player player = Minecraft.getInstance().player;
		if (player == null) {
			return null;
		}
		ItemSnapshot snapshot = toSnapshot(player.getMainHandItem());
		return new Attachment(com.optimistopti.clanchat.chat.AttachmentType.HELD_ITEM, ClanGson.INSTANCE.toJson(snapshot));
	}

	public static Attachment inventory() {
		Player player = Minecraft.getInstance().player;
		if (player == null) {
			return null;
		}
		var inv = player.getInventory();
		ItemSnapshot[] snapshots = new ItemSnapshot[inv.getContainerSize()];
		for (int i = 0; i < snapshots.length; i++) {
			snapshots[i] = toSnapshot(inv.getItem(i));
		}
		return new Attachment(com.optimistopti.clanchat.chat.AttachmentType.INVENTORY, ClanGson.INSTANCE.toJson(snapshots));
	}

	public static Attachment enderChest() {
		Player player = Minecraft.getInstance().player;
		if (player == null) {
			return null;
		}
		var enderChest = player.getEnderChestInventory();
		ItemSnapshot[] snapshots = new ItemSnapshot[enderChest.getContainerSize()];
		for (int i = 0; i < snapshots.length; i++) {
			snapshots[i] = toSnapshot(enderChest.getItem(i));
		}
		return new Attachment(com.optimistopti.clanchat.chat.AttachmentType.ENDER_CHEST, ClanGson.INSTANCE.toJson(snapshots));
	}

	public static Attachment healthStatus() {
		Player player = Minecraft.getInstance().player;
		if (player == null) {
			return null;
		}
		float health = player.getHealth();
		float maxHealth = player.getMaxHealth();
		int armor = player.getArmorValue();
		int food = player.getFoodData().getFoodLevel();
		boolean needsHelp = health <= maxHealth * 0.3f;
		Attachment.HealthStatusData data = new Attachment.HealthStatusData(health, maxHealth, armor, food, needsHelp);
		return new Attachment(com.optimistopti.clanchat.chat.AttachmentType.HEALTH_STATUS, ClanGson.INSTANCE.toJson(data));
	}

	/**
	 * Использует уже снятый заранее скриншот (см. {@link com.optimistopti.clanchat.client.ScreenshotCapture}) —
	 * специально не снимает новый прямо сейчас, потому что к моменту нажатия кнопки в GUI
	 * игровой мир уже закрыт самим окном чата.
	 */
	public static Attachment screenshot() {
		var pending = com.optimistopti.clanchat.client.ScreenshotCapture.getPending();
		if (pending == null) {
			return null;
		}
		Attachment.ScreenshotData data = new Attachment.ScreenshotData(pending.base64Png(), pending.width(), pending.height());
		return new Attachment(com.optimistopti.clanchat.chat.AttachmentType.SCREENSHOT, ClanGson.INSTANCE.toJson(data));
	}

	private static ItemSnapshot toSnapshot(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return ItemSnapshot.EMPTY;
		}
		String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
		return new ItemSnapshot(id, stack.getCount(), stack.getHoverName().getString());
	}
}
