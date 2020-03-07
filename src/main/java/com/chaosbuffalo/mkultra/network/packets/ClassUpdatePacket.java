package com.chaosbuffalo.mkultra.network.packets;

import com.chaosbuffalo.mkultra.core.*;
import com.chaosbuffalo.mkultra.network.MessageHandler;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

import java.io.IOException;
import java.util.*;

public class ClassUpdatePacket implements IMessage {

    public enum UpdateType {
        ADD,
        UPDATE,
        REMOVE
    }

    private Map<ResourceLocation, NBTTagCompound> classes;

    public ClassUpdatePacket() {
        classes = new HashMap<>();
    }

    public ClassUpdatePacket(Collection<PlayerClassInfo> knownClasses) {
        this();
        knownClasses.forEach(this::newClass);
    }

    public ClassUpdatePacket(PlayerClassInfo info, UpdateType action) {
        this();
        if (action == UpdateType.ADD) {
            newClass(info);
        } else if (action == UpdateType.UPDATE) {
            updateClass(info);
        } else if (action == UpdateType.REMOVE) {
            removeClass(info.getClassId());
        }
    }

    void newClass(PlayerClassInfo classInfo) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("sync", UpdateType.ADD.toString());
        classInfo.serialize(tag);
        classes.put(classInfo.getClassId(), tag);
    }

    void updateClass(PlayerClassInfo classInfo) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("sync", UpdateType.UPDATE.toString());
        classInfo.serializeUpdate(tag);
        classes.put(classInfo.getClassId(), tag);
    }

    void removeClass(ResourceLocation classId) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("sync", UpdateType.REMOVE.toString());
        classes.put(classId, tag);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        PacketBuffer pb = new PacketBuffer(buf);
        int count = pb.readVarInt();

        try {
            for (int i = 0; i < count; i++) {
                ResourceLocation id = pb.readResourceLocation();
                NBTTagCompound tag = pb.readCompoundTag();
                classes.put(id, tag);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        PacketBuffer pb = new PacketBuffer(buf);
        pb.writeVarInt(classes.size());

        NBTTagCompound list = new NBTTagCompound();
        classes.forEach((id, tag) -> {
            pb.writeResourceLocation(id);
            pb.writeCompoundTag(tag);
        });
        pb.writeCompoundTag(list);
    }

    public static class Handler extends MessageHandler.Client<ClassUpdatePacket> {

        // Client reads the serialized data from the server
        @Override
        public void handleClientMessage(final EntityPlayer player, final ClassUpdatePacket msg) {
            if (player == null)
                return;
            PlayerData data = (PlayerData) MKUPlayerData.get(player);
            if (data == null)
                return;

            data.clientBulkKnownClassUpdate(msg.classes);
        }
    }
}
