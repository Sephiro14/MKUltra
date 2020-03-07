package com.chaosbuffalo.mkultra.core.sync;

import net.minecraft.nbt.NBTTagCompound;

import java.util.ArrayList;
import java.util.List;

public class CompositeUpdater implements ISupportsPartialSync {
    List<ISupportsPartialSync> components = new ArrayList<>();

    public CompositeUpdater(ISupportsPartialSync... syncs) {
        for (ISupportsPartialSync sync : syncs) {
            add(sync);
        }
    }

    public void add(ISupportsPartialSync sync) {
        components.add(sync);
    }

    @Override
    public boolean isDirty() {
        return components.stream().anyMatch(ISupportsPartialSync::isDirty);
    }

    @Override
    public void deserializeUpdate(NBTTagCompound tag) {
        components.forEach(c -> c.deserializeUpdate(tag));
    }

    @Override
    public void serializeUpdate(NBTTagCompound tag) {
        components.stream().filter(ISupportsPartialSync::isDirty).forEach(c -> c.serializeUpdate(tag));
    }
}