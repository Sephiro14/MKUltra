package com.chaosbuffalo.mkultra.client.gui;

import com.chaosbuffalo.mkultra.MKUltra;
import com.chaosbuffalo.mkultra.client.gui.lib.*;
import com.chaosbuffalo.mkultra.core.MKUPlayerData;
import com.chaosbuffalo.mkultra.core.MKURegistry;
import com.chaosbuffalo.mkultra.core.PlayerData;
import com.chaosbuffalo.mkultra.core.events.PlayerClassEvent;
import com.chaosbuffalo.mkultra.network.packets.AddTalentRequestPacket;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;

public class TalentTreeSelectionScreen extends MKScreen implements IPlayerDataScreen {

    private EntityPlayer player;
    private boolean canEdit;
    private int PANEL_WIDTH = 320;
    private int PANEL_HEIGHT = 256;

    public TalentTreeSelectionScreen(EntityPlayer player, boolean canEdit) {
        super();
        this.player = player;
        this.canEdit = canEdit;
    }

    @Override
    public void handlePlayerDataUpdate(PlayerClassEvent.Updated event) {
        if (!event.isCurrentClass())
            return;
        this.flagNeedSetup();
    }

    @Override
    public void setupScreen() {
        super.setupScreen();
        int panelWidth = PANEL_WIDTH;
        int panelHeight = PANEL_HEIGHT;
        int xPos = width / 2 - panelWidth / 2;
        int yPos = height / 2 - panelHeight / 2;
        ScaledResolution scaledRes = new ScaledResolution(mc);
        MKWidget selectRoot = new MKWidget(xPos, yPos, panelWidth, panelHeight);
        addState("select", selectRoot);

        String titleText;
        if (canEdit) {
            titleText = "Select A Talent Tree to Edit:";
        } else {
            titleText = "Current Talents";
        }
        MKWidget title = new MKText(mc.fontRenderer, titleText)
                .setIsCentered(true)
                .setColor(8129636)
                .setWidth(panelWidth)
                .setX(xPos)
                .setY(yPos + 4);
        selectRoot.addWidget(title);

        MKWidget textLayout = new MKStackLayoutVertical(xPos, title.getY() + title.getHeight(), panelWidth)
                .doSetWidth(true)
                .setMarginTop(8)
                .setMarginBot(8)
                .setPaddingTop(4)
                .setMarginLeft(25)
                .setMarginRight(25)
                .setPaddingBot(4);

        PlayerData data = (PlayerData) MKUPlayerData.get(player);
        if (data != null) {
            String unspentText = String.format("Unspent Points: %d", data.getUnspentTalentPoints());
            MKWidget unspentPoints = new MKText(mc.fontRenderer, unspentText).setColor(8129636);
            textLayout.addWidget(unspentPoints);
            String totalText = String.format("Total Points: %d", data.getTotalTalentPoints());
            MKWidget totalPoints = new MKText(mc.fontRenderer, totalText).setColor(8129636);
            textLayout.addWidget(totalPoints);
            String nextPointText = String.format("Next Point Will Cost: %d", data.getTotalTalentPoints());
            MKWidget nextPoint = new MKText(mc.fontRenderer, nextPointText).setColor(8129636);
            textLayout.addWidget(nextPoint);
            if (canEdit) {
                MKWidget buyButton = new MKButton("Buy Talent Point")
                        .setPressedCallback((button, mouseButton) -> {
                            MKUltra.packetHandler.sendToServer(new AddTalentRequestPacket());
                            return true;
                        })
                        .setEnabled(data.canGainTalentPoint())
                        .setSizeHintWidth(0.6f)
                        .setPosHintX(0.2f)
                        .setEnabled(canEdit);
                textLayout.addWidget(buyButton);
            }
        }

        selectRoot.addWidget(textLayout);

        int ltop = textLayout.getY() + textLayout.getHeight();
        int lwidth = 160;
        int layoutX = xPos + (panelWidth - lwidth) / 2;
        MKScrollView svTreeList = new MKScrollView(layoutX, ltop,
                lwidth, 160, scaledRes.getScaleFactor(), true);
        svTreeList.setScrollMarginX(20).setDoScrollX(false);
        MKLayout layout = new MKStackLayoutVertical(0, 0, lwidth - 40)
                .doSetWidth(true)
                .setPaddingBot(2)
                .setPaddingTop(2)
                .setMarginTop(4);

        ArrayList<ResourceLocation> treeLocs = new ArrayList<>(MKURegistry.REGISTRY_TALENT_TREES.getKeys());
        treeLocs.sort(ResourceLocation::compareTo);
        for (ResourceLocation treeId : treeLocs) {
            MKButton locButton = new MKButton(I18n.format(String.format("%s.%s.name",
                    treeId.getNamespace(), treeId.getPath())));
            locButton.setPressedCallback((button, mouseButton) -> {
                mc.displayGuiScreen(new TalentTreeView(this, player, treeId, canEdit));
                return true;
            });
            layout.addWidget(locButton);
        }
        svTreeList.addWidget(layout);
        svTreeList.centerContentX();
        svTreeList.setToTop();
        selectRoot.addWidget(svTreeList);
        setState("select");
    }


    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        int panelWidth = PANEL_WIDTH;
        int panelHeight = PANEL_HEIGHT;
        int xPos = width / 2 - panelWidth / 2;
        int yPos = height / 2 - panelHeight / 2;

        ResourceLocation loc = new ResourceLocation(MKUltra.MODID, "textures/gui/background_320.png");
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        this.mc.renderEngine.bindTexture(loc);
        GlStateManager.disableLighting();
        drawModalRectWithCustomSizedTexture(xPos, yPos,
                0, 0,
                panelWidth, panelHeight,
                512, 512);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}
