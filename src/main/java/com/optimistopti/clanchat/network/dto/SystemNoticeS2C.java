package com.optimistopti.clanchat.network.dto;

public class SystemNoticeS2C {
	public String text;
	/** "info" | "warn" | "error" — влияет только на цвет в клиентском GUI. */
	public String level;

	public SystemNoticeS2C(String text, String level) {
		this.text = text;
		this.level = level;
	}
}
