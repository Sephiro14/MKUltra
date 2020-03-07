package com.chaosbuffalo.mkultra.core.sync;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

public class DirtyResourceLocation extends DirtyObject<ResourceLocation> {

    static void serialize(NBTTagCompound tag, DirtyObject<ResourceLocation> instance) {
        tag.setString(instance.name, instance.get().toString());
    }

    static void deserialize(NBTTagCompound tag, DirtyObject<ResourceLocation> instance) {
        instance.set(new ResourceLocation(tag.getString(instance.name)));
    }

    public DirtyResourceLocation(String name, ResourceLocation value) {
        super(name, value, DirtyResourceLocation::serialize, DirtyResourceLocation::deserialize);
    }
}
