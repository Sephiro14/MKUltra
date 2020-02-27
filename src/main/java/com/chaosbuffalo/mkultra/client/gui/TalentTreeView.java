package com.chaosbuffalo.mkultra.client.gui;

import com.chaosbuffalo.mkultra.MKUltra;
import com.chaosbuffalo.mkultra.client.gui.lib.*;
import com.chaosbuffalo.mkultra.core.MKUPlayerData;
import com.chaosbuffalo.mkultra.core.PlayerData;
import com.chaosbuffalo.mkultra.core.events.PlayerClassEvent;
import com.chaosbuffalo.mkultra.core.talents.TalentRecord;
import com.chaosbuffalo.mkultra.core.talents.TalentTreeRecord;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.Arrays;

public class TalentTreeView extends MKScreen implements IPlayerDataScreen {

    private GuiScreen parentScreen;
    private boolean canEdit;
    private EntityPlayer player;
    private PlayerData playerData;
    private ResourceLocation treeId;
    private MKScrollView treeView;
    private int PANEL_WIDTH = 320;
    private int PANEL_HEIGHT = 256;

    public TalentTreeView(GuiScreen parentScreen, EntityPlayer player, ResourceLocation treeId, boolean canEdit) {
        super();
        this.parentScreen = parentScreen;
        this.player = player;
        this.playerData = (PlayerData) MKUPlayerData.get(player);
        this.treeId = treeId;
        this.canEdit = canEdit;
    }

    @Override
    public void handlePlayerDataUpdate(PlayerClassEvent.Updated event) {
        if (!event.isCurrentClass())
            return;
        this.flagNeedSetup();
    }

    @Override
    public void addRestoreStateCallbacks() {
        super.addRestoreStateCallbacks();
        if (treeView != null) {
            int offsetX = treeView.getOffsetX();
            int offsetY = treeView.getOffsetY();
            addPostSetupCallback(() -> {
                treeView.setOffsetX(offsetX);
                treeView.setOffsetY(offsetY);
            });
        }
    }

    @Override
    public void onResize(Minecraft minecraft, int width, int height) {
        super.onResize(minecraft, width, height);
        addPostSetupCallback(() -> {
            if (treeView != null) {
                treeView.centerContentX();
                treeView.setToTop();
            }
        });
    }

    @Override
    public void setupScreen() {
        super.setupScreen();
        int panelWidth = PANEL_WIDTH;
        int panelHeight = PANEL_HEIGHT;
        int xPos = width / 2 - panelWidth / 2;
        int yPos = height / 2 - panelHeight / 2;
        ScaledResolution scaledRes = new ScaledResolution(mc);
        MKWidget treeRoot = new MKWidget(xPos, yPos, panelWidth, panelHeight);
        addState("tree", treeRoot);

        int scrollViewSpace = 30;
        treeView = new MKScrollView(xPos + 5, yPos + 5 + scrollViewSpace,
                panelWidth - 10, panelHeight - 10 - scrollViewSpace, scaledRes.getScaleFactor(), true)
                .setScrollMarginY(10)
                .setDoScrollX(false);
        treeRoot.addWidget(treeView);

        MKButton backButton = new MKButton(xPos + 10, yPos + 10, 30, 20, "Back")
                .setPressedCallback((MKButton button, Integer mouseButton) -> {
                    mc.displayGuiScreen(parentScreen);
                    return true;
                });
        treeRoot.addWidget(backButton);

        treeView.clearWidgets();
        treeView.addWidget(setupTalentTree(0, 0));
        treeView.centerContentX();
        treeView.setToTop();

        String unspentText = String.format("Unspent Points: %d", playerData.getUnspentTalentPoints());
        MKWidget unspentPointsTree = new MKText(mc.fontRenderer, unspentText)
                .setColor(8129636)
                .setX(backButton.getRight() + 10)
                .setY(backButton.getTop() + backButton.getHeight() / 2);
        treeRoot.addWidget(unspentPointsTree);

        setState("tree");
    }


    private boolean pressTalentButton(MKButton button, Integer mouseButton) {
        TalentButton talentButton = (TalentButton) button;
        PlayerData data = (PlayerData) MKUPlayerData.get(player);
        if (data != null) {
            if (mouseButton == UIConstants.MOUSE_BUTTON_RIGHT) {
                data.refundTalentPoint(treeId, talentButton.line, talentButton.index);
            } else if (mouseButton == UIConstants.MOUSE_BUTTON_LEFT) {
                data.spendTalentPoint(treeId, talentButton.line, talentButton.index);
            }
        }
        return true;
    }

    private MKWidget setupTalentTree(int xPos, int yPos) {
        TalentTreeRecord record = playerData.getTalentTree(treeId);
        int treeRenderingMarginX = 10;
        int treeRenderingPaddingX = 10;
        int talentButtonHeight = TalentButton.HEIGHT;
        int talentButtonWidth = TalentButton.WIDTH;
        int talentButtonYMargin = 6;
        MKWidget widget = new MKWidget(xPos, yPos);
        if (record != null) {

            int count = record.getRecords().size();
            int talentWidth = talentButtonWidth * count + treeRenderingMarginX * 2 + (count - 1) * treeRenderingPaddingX;
            int spacePerColumn = talentWidth / count;
            int columnOffset = (spacePerColumn - talentButtonWidth) / 2;
            int i = 0;
            String[] keys = record.getRecords().keySet().toArray(new String[0]);
            Arrays.sort(keys);
            int largestIndex = 0;
            int columnOffsetTotal = 0;
            for (String name : keys) {
                ArrayList<TalentRecord> talents = record.getRecords().get(name);
                int talentIndex = 0;
                for (TalentRecord talent : talents) {
                    TalentButton button = new TalentButton(talentIndex, name, talent,
                            xPos + spacePerColumn * i + columnOffsetTotal,
                            yPos + talentIndex * talentButtonHeight + talentButtonYMargin
                    );
                    button.setPressedCallback(this::pressTalentButton);
                    button.setEnabled(this.canEdit);
                    widget.addWidget(button);
                    if (talentIndex > largestIndex) {
                        largestIndex = talentIndex;
                    }
                    talentIndex++;
                }
                i++;
                columnOffsetTotal += columnOffset;
            }
            widget.setWidth(talentWidth);
            widget.setHeight((largestIndex + 1) * talentButtonHeight + talentButtonYMargin);
        }
        return widget;
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
