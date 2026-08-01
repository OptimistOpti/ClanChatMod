package com.optimistopti.clanchat.chat;

/**
 * Лёгкий "снимок" предмета только для отображения в чате (иконка + название + количество).
 * Специально НЕ храним полный NBT/компоненты — вложение это превью, а не способ передать предмет.
 */
public record ItemSnapshot(String itemId, int count, String displayName) {

	public static final ItemSnapshot EMPTY = new ItemSnapshot("minecraft:air", 0, "");

	public boolean isEmpty() {
		return count <= 0 || "minecraft:air".equals(itemId);
	}
}
