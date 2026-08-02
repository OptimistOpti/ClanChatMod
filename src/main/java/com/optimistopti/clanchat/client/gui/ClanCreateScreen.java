package com.optimistopti.clanchat.client.gui;

import com.optimistopti.clanchat.client.ClanChatModClient;
import com.optimistopti.clanchat.network.ClanAction;
import com.optimistopti.clanchat.network.dto.CreateClanC2S;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ClanCreateScreen extends Screen {

	private final Screen parent;
	private EditBox nameBox;
	private EditBox tagBox;

	public ClanCreateScreen(Screen parent) {
		super(Component.literal("Создать клан"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int centerX = this.width / 2;
		int y = this.height / 2 - 40;

		nameBox = new EditBox(this.font, centerX - 100, y, 200, 20, Component.literal("Название клана"));
		nameBox.setMaxLength(24);
		this.addRenderableWidget(nameBox);
		this.setInitialFocus(nameBox);
		y += 26;

		tagBox = new EditBox(this.font, centerX - 100, y, 200, 20, Component.literal("Тег (2-4 символа)"));
		tagBox.setMaxLength(4);
		this.addRenderableWidget(tagBox);
		y += 30;

		this.addRenderableWidget(Button.builder(Component.literal("Создать"), b -> createClan())
				.bounds(centerX - 100, y, 96, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Отмена"), b -> this.onClose())
				.bounds(centerX + 4, y, 96, 20).build());
	}

	private void createClan() {
		CreateClanC2S dto = new CreateClanC2S();
		dto.name = nameBox.getValue().trim();
		dto.tag = tagBox.getValue().trim();
		dto.color = 0xFFFFFF;
		ClanChatModClient.sendToServer(ClanAction.CREATE_CLAN, dto);
		this.minecraft.setScreen(new ClanChatScreen(parent));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		this.minecraft.setScreen(parent);
	}
}
