package com.optimistopti.clanchat.network.dto;

/** {@link com.optimistopti.clanchat.network.ClanAction#SEND_MESSAGE} */
public class SendMessageC2S {
	public String channel; // ChatChannelType.name()
	public String content;
	public String whisperTargetUuid; // nullable
	public String attachmentType; // AttachmentType.name(), nullable
	public String attachmentDataJson; // nullable
}
