package com.optimistopti.clanchat.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.optimistopti.clanchat.ClanChatMod;
import com.optimistopti.clanchat.clan.Clan;
import com.optimistopti.clanchat.clan.ClanActionException;
import com.optimistopti.clanchat.clan.ClanManager;
import com.optimistopti.clanchat.clan.ClanMember;
import com.optimistopti.clanchat.clan.ClanRole;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Fallback-команды на случай, если игрок не хочет открывать GUI (или для админ-отладки).
 * Основной способ взаимодействия — экран {@code ClanChatScreen} на клиенте.
 */
public final class ClanCommands {

	private ClanCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(Commands.literal("clan")
					.then(Commands.literal("create")
							.then(Commands.argument("name", StringArgumentType.word())
									.then(Commands.argument("tag", StringArgumentType.word())
											.executes(ClanCommands::createClan))))
					.then(Commands.literal("invite")
							.then(Commands.argument("target", EntityArgument.player())
									.executes(ClanCommands::invite)))
					.then(Commands.literal("accept").executes(ClanCommands::accept))
					.then(Commands.literal("decline").executes(ClanCommands::decline))
					.then(Commands.literal("leave").executes(ClanCommands::leave))
					.then(Commands.literal("disband").executes(ClanCommands::disband))
					.then(Commands.literal("kick")
							.then(Commands.argument("target", EntityArgument.player())
									.executes(ClanCommands::kick)))
					.then(Commands.literal("promote")
							.then(Commands.argument("target", EntityArgument.player())
									.executes(ClanCommands::promote)))
					.then(Commands.literal("demote")
							.then(Commands.argument("target", EntityArgument.player())
									.executes(ClanCommands::demote)))
					.then(Commands.literal("info").executes(ClanCommands::info))
			);
		});
	}

	private static ClanManager manager() {
		return ClanChatMod.getClanManager();
	}

	private static int createClan(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		String name = StringArgumentType.getString(ctx, "name");
		String tag = StringArgumentType.getString(ctx, "tag");
		try {
			Clan clan = manager().createClan(player, name, tag, 0xFFFFFF);
			ctx.getSource().sendSuccess(() -> Component.literal("Клан '" + clan.getName() + "' [" + clan.getTag() + "] создан!"), false);
			return 1;
		} catch (ClanActionException e) {
			ctx.getSource().sendFailure(Component.literal(e.getMessage()));
			return 0;
		}
	}

	private static int invite(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
		try {
			manager().invite(player, target);
			ctx.getSource().sendSuccess(() -> Component.literal("Приглашение отправлено."), false);
			return 1;
		} catch (ClanActionException e) {
			ctx.getSource().sendFailure(Component.literal(e.getMessage()));
			return 0;
		}
	}

	private static int accept(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		try {
			Clan clan = manager().acceptInvite(player);
			ctx.getSource().sendSuccess(() -> Component.literal("Добро пожаловать в '" + clan.getName() + "'!"), false);
			return 1;
		} catch (ClanActionException e) {
			ctx.getSource().sendFailure(Component.literal(e.getMessage()));
			return 0;
		}
	}

	private static int decline(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		manager().declineInvite(ctx.getSource().getPlayerOrException());
		ctx.getSource().sendSuccess(() -> Component.literal("Приглашение отклонено."), false);
		return 1;
	}

	private static int leave(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		try {
			manager().leave(ctx.getSource().getPlayerOrException());
			ctx.getSource().sendSuccess(() -> Component.literal("Ты покинул клан."), false);
			return 1;
		} catch (ClanActionException e) {
			ctx.getSource().sendFailure(Component.literal(e.getMessage()));
			return 0;
		}
	}

	private static int disband(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		try {
			manager().disbandClan(ctx.getSource().getPlayerOrException());
			ctx.getSource().sendSuccess(() -> Component.literal("Клан распущен."), false);
			return 1;
		} catch (ClanActionException e) {
			ctx.getSource().sendFailure(Component.literal(e.getMessage()));
			return 0;
		}
	}

	private static int kick(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
		try {
			manager().kick(player, target.getUUID());
			ctx.getSource().sendSuccess(() -> Component.literal("Игрок " + target.getGameProfile().getName() + " исключён из клана."), false);
			return 1;
		} catch (ClanActionException e) {
			ctx.getSource().sendFailure(Component.literal(e.getMessage()));
			return 0;
		}
	}

	private static int promote(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		return setRole(ctx, ClanRole.OFFICER);
	}

	private static int demote(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		return setRole(ctx, ClanRole.MEMBER);
	}

	private static int setRole(CommandContext<CommandSourceStack> ctx, ClanRole role) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
		try {
			manager().setRole(player, target.getUUID(), role);
			ctx.getSource().sendSuccess(() -> Component.literal("Роль " + target.getGameProfile().getName() + " изменена на " + role.name() + "."), false);
			return 1;
		} catch (ClanActionException e) {
			ctx.getSource().sendFailure(Component.literal(e.getMessage()));
			return 0;
		}
	}

	private static int info(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		Clan clan = manager().getClanOf(player.getUUID());
		if (clan == null) {
			ctx.getSource().sendFailure(Component.literal("Ты не состоишь в клане."));
			return 0;
		}
		ctx.getSource().sendSuccess(() -> Component.literal(
				"Клан: " + clan.getName() + " [" + clan.getTag() + "], участников: " + clan.getMembers().size()), false);
		for (ClanMember member : clan.getMembers().values()) {
			ctx.getSource().sendSuccess(() -> Component.literal(" - " + member.getLastKnownName() + " (" + member.getRole().name() + ")"), false);
		}
		return 1;
	}
}
