package com.chaosbuffalo.mkultra.network.packets;

import com.chaosbuffalo.mkultra.network.MessageHandler;
import com.chaosbuffalo.mkultra.party.PartyManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

import java.util.UUID;


public class PartyInviteResponsePacket implements IMessage {
    private UUID invitingUUID;
    private boolean doesAccept;

    public PartyInviteResponsePacket() {
    }

    public PartyInviteResponsePacket(UUID invitingUUID, boolean doesAccept) {
        this.invitingUUID = invitingUUID;
        this.doesAccept = doesAccept;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.invitingUUID = new UUID(buf.readLong(), buf.readLong());
        this.doesAccept = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {

        buf.writeLong(this.invitingUUID.getMostSignificantBits());
        buf.writeLong(this.invitingUUID.getLeastSignificantBits());
        buf.writeBoolean(this.doesAccept);
    }

    // ========================================================================

    public static class Handler extends MessageHandler.Server<PartyInviteResponsePacket> {


        @Override
        public void handleServerMessage(final EntityPlayer player,
                                        final PartyInviteResponsePacket msg) {
            World theWorld = player.getEntityWorld();
            EntityPlayer invitingPlayer = theWorld.getPlayerEntityByUUID(msg.invitingUUID);

            if (player == null || invitingPlayer == null) {
                return;
            }

            if (msg.doesAccept) {
                PartyManager.handleInviteAccept(invitingPlayer, player);

                String message = String.format("%s accepted your invite", player.getName());
                invitingPlayer.sendMessage(new TextComponentString(message));

                message = String.format("You have accepted %s's invite", invitingPlayer.getName());
                player.sendMessage(new TextComponentString(message));
            } else {
                String rejectionString = String.format("%s declined your invite", player.getName());
                invitingPlayer.sendMessage(new TextComponentString(rejectionString));

                rejectionString = String.format("You declined %s's invite", invitingPlayer.getName());
                invitingPlayer.sendMessage(new TextComponentString(rejectionString));
            }
        }

    }
}
