package com.chaosbuffalo.mkultra.core;

import net.minecraft.nbt.NBTTagCompound;

public interface ISupportsPartialSync {
    boolean isDirty();
    void deserializeUpdate(NBTTagCompound tag);
    void serializeUpdate(NBTTagCompound tag);
}
