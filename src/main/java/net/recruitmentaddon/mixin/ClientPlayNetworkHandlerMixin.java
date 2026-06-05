package net.recruitmentaddon.mixin;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.recruitmentaddon.RecruitmentAddon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPlayNetworkHandlerMixin {

    @Inject(method = "handlePlayerInfoUpdate", at = @At("HEAD"))
    private void recruitmentAddon$onPlayerInfoUpdate(ClientboundPlayerInfoUpdatePacket packet, CallbackInfo ci) {
        if (!packet.actions().contains(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER)) return;
        for (ClientboundPlayerInfoUpdatePacket.Entry entry : packet.newEntries()) {
            if (entry.profile() == null || entry.profile().name() == null) continue;
            RecruitmentAddon.onPlayerListAddPacket(entry.profile().name());
        }
    }

    @Inject(method = "handleSystemChat", at = @At("HEAD"))
    private void recruitmentAddon$onSystemChat(ClientboundSystemChatPacket packet, CallbackInfo ci) {
        if (!packet.overlay()) {
            RecruitmentAddon.onIncomingMessage(packet.content().getString(), true);
        }
    }

    @Inject(method = "handleDisguisedChat", at = @At("HEAD"))
    private void recruitmentAddon$onDisguisedChat(ClientboundDisguisedChatPacket packet, CallbackInfo ci) {
        RecruitmentAddon.onIncomingMessage(packet.message().getString(), true);
    }

    @Inject(method = "handlePlayerChat", at = @At("HEAD"))
    private void recruitmentAddon$onPlayerChat(ClientboundPlayerChatPacket packet, CallbackInfo ci) {
        if (packet.unsignedContent() != null) {
            RecruitmentAddon.onIncomingMessage(packet.unsignedContent().getString(), false);
        }
    }
}
