package com.optimistopti.clanchat.network.dto;

/** {@link com.optimistopti.clanchat.network.ClanAction#SET_ROLE} */
public class SetRoleC2S {
	public String targetUuid;
	public String role; // ClanRole.name()
}
