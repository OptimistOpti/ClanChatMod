package com.optimistopti.clanchat.network.dto;

public class InviteReceivedS2C {
	public String clanName;
	public String inviterName;

	public InviteReceivedS2C(String clanName, String inviterName) {
		this.clanName = clanName;
		this.inviterName = inviterName;
	}
}
