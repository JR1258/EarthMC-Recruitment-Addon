package net.recruitmentaddon.mixin;

import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.ProfilelessChatMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.ChatMessageS2CPacket;
import net.recruitmentaddon.RecruitmentAddon;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.UUID;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {

    @Shadow @Final
    private Map<UUID, PlayerListEntry> playerListEntries;

    @Inject(method = "onPlayerList", at = @At("HEAD"))
    private void recruitmentAddon$onPlayerList(PlayerListS2CPacket packet, CallbackInfo ci) {
        if (!packet.getActions().contains(PlayerListS2CPacket.Action.ADD_PLAYER)) return;
        for (PlayerListS2CPacket.Entry entry : packet.getEntries()) {
            if (entry.profile() == null || entry.profile().name() == null) continue;
            if (playerListEntries.containsKey(entry.profileId())) continue;
            RecruitmentAddon.onPlayerListAddPacket(entry.profile().name());
        }
    }

    @Inject(method = "onGameMessage", at = @At("HEAD"))
    private void recruitmentAddon$onGameMessage(GameMessageS2CPacket packet, CallbackInfo ci) {
        if (!packet.overlay()) {
            RecruitmentAddon.onIncomingMessage(packet.content().getString());
        }
    }

    @Inject(method = "onProfilelessChatMessage", at = @At("HEAD"))
    private void recruitmentAddon$onProfilelessChatMessage(ProfilelessChatMessageS2CPacket packet, CallbackInfo ci) {
        RecruitmentAddon.onIncomingMessage(packet.message().getString());
    }

    @Inject(method = "onChatMessage", at = @At("HEAD"))
    private void recruitmentAddon$onChatMessage(ChatMessageS2CPacket packet, CallbackInfo ci) {
        if (packet.unsignedContent() != null) {
            RecruitmentAddon.onIncomingMessage(packet.unsignedContent().getString());
        }
    }
}
