package com.chaosbuffalo.mkultra.core.sync;

import net.minecraft.nbt.NBTTagCompound;

public class DirtyInt implements ISupportsPartialSync {
    String name;
    private int value;
    private boolean dirty;

    public DirtyInt(String name, int value) {
        this.name = name;
        set(value);
    }

    public void set(int value) {
        this.value = value;
        this.dirty = true;
    }

    public void add(int value) {
        set(get() + value);
    }

    public int get() {
        return value;
    }

    @Override
    public boolean isDirty() {
        return dirty;
    }

    @Override
    public void deserializeUpdate(NBTTagCompound tag) {
        if (tag.hasKey(name)) {
            this.value = tag.getInteger(name);
        }
    }

    @Override
    public void serializeUpdate(NBTTagCompound tag) {
        if (dirty) {
            tag.setInteger(name, value);
            dirty = false;
        }
    }
}
