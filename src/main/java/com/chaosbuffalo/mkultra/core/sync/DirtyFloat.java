package com.chaosbuffalo.mkultra.core.sync;

import net.minecraft.nbt.NBTTagCompound;

public class DirtyFloat implements ISupportsPartialSync {
    String name;
    private float value;
    private boolean dirty;

    public DirtyFloat(String name, float value) {
        this.name = name;
        set(value);
    }

    public void set(float value) {
        this.value = value;
        this.dirty = true;
    }

    public void add(float value) {
        set(get() + value);
    }

    public float get() {
        return value;
    }

    @Override
    public boolean isDirty() {
        return dirty;
    }

    @Override
    public void deserializeUpdate(NBTTagCompound tag) {
        if (tag.hasKey(name)) {
            this.value = tag.getFloat(name);
        }
    }

    @Override
    public void serializeUpdate(NBTTagCompound tag) {
        if (dirty) {
            tag.setFloat(name, value);
            dirty = false;
        }
    }
}
