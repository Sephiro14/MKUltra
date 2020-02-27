package com.chaosbuffalo.mkultra.client.gui;

import com.chaosbuffalo.mkultra.core.events.PlayerClassEvent;

public interface IPlayerDataScreen {
    void handlePlayerDataUpdate(PlayerClassEvent.Updated event);
}
