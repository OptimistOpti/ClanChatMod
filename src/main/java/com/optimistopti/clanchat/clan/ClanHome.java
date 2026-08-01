package com.optimistopti.clanchat.clan;

/**
 * Общая точка клана (база, точка сбора, ферма и т.д.), которую можно быстро
 * прикрепить к сообщению в чате через вложение "Координаты".
 */
public record ClanHome(String name, String dimensionId, double x, double y, double z) {
}
