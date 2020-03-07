package com.chaosbuffalo.mkultra.core;

import com.chaosbuffalo.mkultra.GameConstants;
import com.chaosbuffalo.mkultra.MKConfig;
import com.chaosbuffalo.mkultra.core.sync.CompositeUpdater;
import com.chaosbuffalo.mkultra.core.sync.DirtyInt;
import com.chaosbuffalo.mkultra.core.sync.ISupportsPartialSync;
import com.chaosbuffalo.mkultra.core.talents.BaseTalent;
import com.chaosbuffalo.mkultra.core.talents.RangedAttributeTalent;
import com.chaosbuffalo.mkultra.core.talents.TalentTree;
import com.chaosbuffalo.mkultra.core.talents.TalentTreeRecord;
import com.chaosbuffalo.mkultra.log.Log;
import com.chaosbuffalo.mkultra.network.packets.ClassUpdatePacket;
import com.google.common.collect.Maps;
import net.minecraft.entity.ai.attributes.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

import javax.annotation.Nullable;
import java.util.*;

public class PlayerClassInfo implements ISupportsPartialSync {
    private PlayerClass playerClass;
    private ResourceLocation classId;
    private DirtyInt level = new DirtyInt("level", 1);
    private DirtyInt unspentAbilityPoints = new DirtyInt("unspentPoints", 1);
    private DirtyInt totalTalentPoints = new DirtyInt("totalTalentPoints", 0);
    private DirtyInt unspentTalentPoints = new DirtyInt("unspentTalentPoints", 0);
    private HashMap<ResourceLocation, TalentTreeRecord> talentTrees;
    private List<ResourceLocation> hotbar;
    private List<ResourceLocation> abilitySpendOrder;
    private List<ResourceLocation> loadedPassives;
    private List<ResourceLocation> loadedUltimates;
    private Map<ResourceLocation, PlayerAbilityInfo> abilityInfoMap = new HashMap<>(GameConstants.ACTION_BAR_SIZE);
    private KnownAbilityUpdater dirtyAbilities = new KnownAbilityUpdater();
    private ActiveAbilityUpdater dirtyActiveAbilities = new ActiveAbilityUpdater();
    private CompositeUpdater dirtyUpdater = new CompositeUpdater(level, unspentAbilityPoints, totalTalentPoints, unspentTalentPoints, dirtyAbilities, dirtyActiveAbilities);

    public PlayerClassInfo(PlayerClass playerClass) {
        this.playerClass = playerClass;
        this.classId = playerClass.getClassId();
        loadedPassives = NonNullList.withSize(GameConstants.MAX_PASSIVES, MKURegistry.INVALID_ABILITY);
        loadedUltimates = NonNullList.withSize(GameConstants.MAX_ULTIMATES, MKURegistry.INVALID_ABILITY);
        hotbar = NonNullList.withSize(GameConstants.ACTION_BAR_SIZE, MKURegistry.INVALID_ABILITY);
        abilitySpendOrder = NonNullList.withSize(GameConstants.MAX_CLASS_LEVEL, MKURegistry.INVALID_ABILITY);
        talentTrees = new HashMap<>();
        for (TalentTree tree : MKURegistry.REGISTRY_TALENT_TREES.getValuesCollection()) {
            TalentTreeRecord record = new TalentTreeRecord(tree);
            talentTrees.put(tree.getRegistryName(), record);
            dirtyUpdater.add(record);
        }
    }

    public PlayerClass getClassDefinition() {
        return playerClass;
    }

    IMessage getUpdateMessage() {
        if (isDirty()) {
            IMessage message = new ClassUpdatePacket(this, ClassUpdatePacket.UpdateType.UPDATE);
            return message;
        }
        return null;
    }

    public ResourceLocation getClassId() {
        return classId;
    }

    public int getLevel() {
        return level.get();
    }

    void setLevel(int level) {
        this.level.set(level);
    }

    public int getUnspentPoints() {
        return unspentAbilityPoints.get();
    }

    void setUnspentPoints(int unspentPoints) {
        // You shouldn't have more unspent points than your levels
        if (unspentPoints > getLevel())
            return;
        unspentAbilityPoints.set(unspentPoints);
    }

    public int getTotalTalentPoints() {
        return totalTalentPoints.get();
    }

    private void setTotalTalentPoints(int points) {
        totalTalentPoints.set(points);
    }

    public int getUnspentTalentPoints() {
        return unspentTalentPoints.get();
    }

    private void setUnspentTalentPoints(int points) {
        unspentTalentPoints.set(points);
    }

    boolean isAtTalentPointLimit() {
        int max = getTalentPointLimit();
        return max != -1 && getTotalTalentPoints() >= max;
    }

    private int getTalentPointLimit() {
        return MKConfig.gameplay.MAX_TALENT_POINTS_PER_CLASS;
    }

    Collection<PlayerAbilityInfo> getAbilities() {
        return Collections.unmodifiableCollection(abilityInfoMap.values());
    }

    List<ResourceLocation> getActivePassives() {
        return Collections.unmodifiableList(loadedPassives);
    }

    List<ResourceLocation> getActiveUltimates() {
        return Collections.unmodifiableList(loadedUltimates);
    }

    ResourceLocation getAbilityInSlot(int index) {
        if (index < hotbar.size()) {
            return hotbar.get(index);
        }
        return MKURegistry.INVALID_ABILITY;
    }

    private int getSlotForAbility(ResourceLocation abilityId) {
        int slot = hotbar.indexOf(abilityId);
        if (slot == -1)
            return GameConstants.ACTION_BAR_INVALID_SLOT;
        return slot;
    }

    private void setAbilityInSlot(int index, ResourceLocation abilityId) {
        if (index < hotbar.size()) {
            hotbar.set(index, abilityId);
            dirtyActiveAbilities.markAbilityListDirty("hotbar", index, abilityId);
        }
    }

    boolean checkTalentTotals() {
        int maxPoints = getTalentPointLimit();
        if (maxPoints >= 0 && getTotalTalentPoints() > maxPoints) {
            Log.info("player has too many talents! %d max %d", getTotalTalentPoints(), maxPoints);
            return false;
        }
        int spent = getTotalSpentPoints();
        if (getTotalTalentPoints() - spent != getUnspentTalentPoints()) {
            return false;
        }
        return true;
    }

    void resetClassAbilities() {
        playerClass.getAbilities().forEach(ability -> unlearnAbility(ability.getAbilityId(), false, true));

        clearAbilitySpendOrder();
        setUnspentPoints(getLevel());
    }

    void abilityUpdate(ResourceLocation abilityId, PlayerAbilityInfo info) {
        abilityInfoMap.put(abilityId, info);
        dirtyAbilities.markDirty(info);
        checkHotBar(abilityId);
    }

    boolean hasUltimate() {
        return getUltimateAbilitiesFromTalents().size() > 0;
    }

    private int getFirstFreeAbilitySlot() {
        return getSlotForAbility(MKURegistry.INVALID_ABILITY);
    }

    private int tryPlaceOnBar(ResourceLocation abilityId) {
        int slot = getSlotForAbility(abilityId);
        if (slot == GameConstants.ACTION_BAR_INVALID_SLOT) {
            // Skill was just learned so let's try to put it on the bar
            slot = getFirstFreeAbilitySlot();
            if (slot != GameConstants.ACTION_BAR_INVALID_SLOT) {
                setAbilityInSlot(slot, abilityId);
            }
        }

        return slot;
    }

    int getActionBarSize() {
        ResourceLocation loc = getAbilityInSlot(GameConstants.ACTION_BAR_SIZE - 1);
        return (hasUltimate() || !loc.equals(MKURegistry.INVALID_ABILITY)) ?
                GameConstants.ACTION_BAR_SIZE :
                GameConstants.CLASS_ACTION_BAR_SIZE;
    }

    boolean addPassiveToSlot(ResourceLocation abilityId, int slotIndex) {
        if (canAddPassiveToSlot(abilityId, slotIndex)) {
            for (int i = 0; i < loadedPassives.size(); i++) {
                if (!abilityId.equals(MKURegistry.INVALID_ABILITY) && i != slotIndex && abilityId.equals(loadedPassives.get(i))) {
                    setPassiveSlot(i, loadedPassives.get(slotIndex));
                }
            }
            setPassiveSlot(slotIndex, abilityId);
            return true;
        }
        return false;
    }

    boolean addUltimateToSlot(ResourceLocation abilityId, int slotIndex) {
        if (canAddUltimateToSlot(abilityId, slotIndex)) {
            ResourceLocation currentAbility = loadedUltimates.get(slotIndex);
            if (abilityId.equals(MKURegistry.INVALID_ABILITY) && !currentAbility.equals(MKURegistry.INVALID_ABILITY)) {
                clearUltimateSlot(slotIndex);
            } else {
                if (!currentAbility.equals(MKURegistry.INVALID_ABILITY)) {
                    clearUltimateSlot(slotIndex);
                }
                setUltimateSlot(slotIndex, abilityId);
                tryPlaceOnBar(abilityId);
            }
            return true;
        }
        return false;
    }

    private int getUltimateSlot(ResourceLocation abilityId) {
        int index = loadedUltimates.indexOf(abilityId);
        if (index == -1)
            return GameConstants.ULTIMATE_INVALID_SLOT;
        return index;
    }

    private int getPassiveSlot(ResourceLocation abilityId) {
        int index = loadedPassives.indexOf(abilityId);
        if (index == -1)
            return GameConstants.PASSIVE_INVALID_SLOT;
        return index;
    }

    private void setUltimateSlot(int slotIndex, ResourceLocation abilityId) {
        loadedUltimates.set(slotIndex, abilityId);
        dirtyActiveAbilities.markAbilityListDirty("ultimates", slotIndex, abilityId);
    }

    private void setPassiveSlot(int slotIndex, ResourceLocation abilityId) {
        loadedPassives.set(slotIndex, abilityId);
        dirtyActiveAbilities.markAbilityListDirty("passives", slotIndex, abilityId);
    }

    void clearUltimate(ResourceLocation abilityId) {
        int slot = getUltimateSlot(abilityId);
        if (slot != GameConstants.ULTIMATE_INVALID_SLOT) {
            clearUltimateSlot(slot);
        }
    }

    void clearPassive(ResourceLocation abilityId) {
        int slot = getPassiveSlot(abilityId);
        if (slot != GameConstants.PASSIVE_INVALID_SLOT) {
            clearPassiveSlot(slot);
        }
    }

    private void clearUltimateSlot(int slotIndex) {
        if (slotIndex >= loadedUltimates.size())
            return;
        ResourceLocation currentAbility = loadedUltimates.get(slotIndex);
        setUltimateSlot(slotIndex, MKURegistry.INVALID_ABILITY);
        if (!currentAbility.equals(MKURegistry.INVALID_ABILITY)) {
            removeFromHotBar(currentAbility);
        }
    }

    private void clearPassiveSlot(int slotIndex) {
        setPassiveSlot(slotIndex, MKURegistry.INVALID_ABILITY);
    }

    private void removeFromHotBar(ResourceLocation abilityId) {
        int slot = getSlotForAbility(abilityId);
        if (slot != GameConstants.ACTION_BAR_INVALID_SLOT) {
            setAbilityInSlot(slot, MKURegistry.INVALID_ABILITY);
        }
    }

    boolean canAddUltimateToSlot(ResourceLocation abilityId, int slotIndex) {
        return slotIndex < GameConstants.MAX_ULTIMATES && hasTrainedUltimate(abilityId);
    }

    boolean canAddPassiveToSlot(ResourceLocation abilityId, int slotIndex) {
        return slotIndex < GameConstants.MAX_PASSIVES && hasTrainedPassive(abilityId);
    }

    Set<PlayerAbility> getUltimateAbilitiesFromTalents() {
        HashSet<PlayerAbility> abilities = new HashSet<>();
        for (TalentTreeRecord rec : talentTrees.values()) {
            if (rec.hasPointsInTree()) {
                rec.getUltimatesWithPoints().forEach(talent -> abilities.add(talent.getAbility()));
            }
        }
        return abilities;
    }

    Set<PlayerPassiveAbility> getPassiveAbilitiesFromTalents() {
        HashSet<PlayerPassiveAbility> abilities = new HashSet<>();
        for (TalentTreeRecord rec : talentTrees.values()) {
            if (rec.hasPointsInTree()) {
                rec.getPassivesWithPoints().forEach(talent -> abilities.add(talent.getAbility()));
            }
        }
        return abilities;
    }

    private boolean hasTrainedUltimate(ResourceLocation abilityId) {
        if (abilityId.equals(MKURegistry.INVALID_ABILITY)) {
            return true;
        }
        Set<PlayerAbility> abilities = getUltimateAbilitiesFromTalents();
        PlayerAbility ability = MKURegistry.getAbility(abilityId);
        return ability != null && abilities.contains(ability);
    }

    private boolean hasTrainedPassive(ResourceLocation abilityId) {
        if (abilityId.equals(MKURegistry.INVALID_ABILITY)) {
            return true;
        }
        Set<PlayerPassiveAbility> abilities = getPassiveAbilitiesFromTalents();
        PlayerAbility ability = MKURegistry.getAbility(abilityId);
        return ability instanceof PlayerPassiveAbility && abilities.contains(ability);
    }

    private Set<RangedAttributeTalent> getAttributeTalentSet() {
        HashSet<RangedAttributeTalent> attributeTalents = new HashSet<>();
        for (TalentTreeRecord rec : talentTrees.values()) {
            if (rec.hasPointsInTree()) {
                attributeTalents.addAll(rec.getAttributeTalentsWithPoints());
            }
        }
        return attributeTalents;
    }

    private Map<IAttribute, AttributeModifier> getAttributeModifiers() {
        Set<RangedAttributeTalent> presentTalents = getAttributeTalentSet();
        Map<IAttribute, AttributeModifier> attributeModifierMap = Maps.newHashMap();
        for (RangedAttributeTalent talent : presentTalents) {
            double value = 0.0;
            for (TalentTreeRecord rec : talentTrees.values()) {
                if (rec.hasPointsInTree()) {
                    value += rec.getTotalForAttributeTalent(talent);
                }
            }
//            Log.info("Total for attribute talent: %s, %f", talent.getRegistryName().toString(), value);
            attributeModifierMap.put(talent.getAttribute(), talent.createModifier(value));
        }
        return attributeModifierMap;
    }

    public void applyAttributesModifiersToPlayer(EntityPlayer player) {
        AbstractAttributeMap attributeMap = player.getAttributeMap();

        for (Map.Entry<IAttribute, AttributeModifier> entry : getAttributeModifiers().entrySet()) {
            IAttributeInstance instance = attributeMap.getAttributeInstance(entry.getKey());
            if (instance != null) {
                AttributeModifier attributemodifier = entry.getValue();
                instance.removeModifier(attributemodifier);
                instance.applyModifier(attributemodifier);
            }
        }
    }

    public void removeAttributesModifiersFromPlayer(EntityPlayer player) {
        AbstractAttributeMap attributeMap = player.getAttributeMap();

        for (RangedAttributeTalent entry : getAttributeTalentSet()) {
            IAttributeInstance instance = attributeMap.getAttributeInstance(entry.getAttribute());
            if (instance != null) {
                instance.removeModifier(entry.getUUID());
            }
        }
    }

    public void refreshAttributeModifiers(EntityPlayer player, RangedAttribute attribute) {
        removeAttributesModifiersFromPlayer(player);
        applyAttributesModifiersToPlayer(player);
    }

    private void checkHotBar(ResourceLocation abilityId) {
        if (abilityId.equals(MKURegistry.INVALID_ABILITY))
            return;
        PlayerAbilityInfo info = getAbilityInfo(abilityId);
        if (info == null)
            return;
        if (!info.isCurrentlyKnown()) {
            removeFromHotBar(info.getId());
        }
    }

    private void checkSlottedTalents() {
        Set<PlayerPassiveAbility> knownPassives = getPassiveAbilitiesFromTalents();
        for (ResourceLocation abilityId : loadedPassives) {
            PlayerAbility ability = MKURegistry.getAbility(abilityId);
            if (ability == null || (ability instanceof PlayerPassiveAbility && !knownPassives.contains(ability))) {
                clearPassive(abilityId);
            }
        }

        Set<PlayerAbility> knownUltimates = getUltimateAbilitiesFromTalents();
        for (ResourceLocation abilityId : loadedUltimates) {
            PlayerAbility ability = MKURegistry.getAbility(abilityId);
            if (ability == null || !knownUltimates.contains(ability)) {
                clearUltimate(abilityId);
            }
        }
    }

    void resetTalents() {
        loadedUltimates.forEach(id -> unlearnAbility(id, false, true));
        talentTrees.values().forEach(TalentTreeRecord::reset);
        clearPassiveAbilities();
        clearUltimateAbilities();

        int maxPoints = Math.min(getTotalTalentPoints(), getTalentPointLimit());
        maxPoints = Math.max(maxPoints, 0);
        setTotalTalentPoints(maxPoints);
        setUnspentTalentPoints(maxPoints);
    }

    private void clearSpentAbilities() {
        setUnspentPoints(getLevel());
        clearAbilitySpendOrder();
        clearActiveAbilities();
        abilityInfoMap.clear();
    }

    void addTalentPoints(int pointCount) {
        totalTalentPoints.add(pointCount);
        unspentTalentPoints.add(pointCount);
    }

    private int getTotalSpentPoints() {
        int tot = 0;
        for (TalentTreeRecord talentTree : talentTrees.values()) {
            tot += talentTree.getPointsInTree();
        }
        return tot;
    }

    boolean canSpendTalentPoint(ResourceLocation tree, String line, int index) {
        if (getUnspentTalentPoints() == 0)
            return false;
        TalentTreeRecord talentTree = talentTrees.get(tree);
        return talentTree != null && talentTree.canIncrementPoint(line, index);
    }

    boolean canRefundTalentPoint(ResourceLocation tree, String line, int index) {
        TalentTreeRecord talentTree = talentTrees.get(tree);
        return talentTree != null && talentTree.canDecrementPoint(line, index);
    }

    boolean spendTalentPoint(EntityPlayer player, ResourceLocation tree, String line, int index) {
        if (!canSpendTalentPoint(tree, line, index))
            return false;

        TalentTreeRecord talentTree = talentTrees.get(tree);
        BaseTalent talentDef = talentTree.getTalentDefinition(line, index);
        if (talentDef.onAdd(player, this)) {
            talentTree.incrementPoint(line, index);
            unspentTalentPoints.add(-1);
            return true;
        }
        return false;
    }

    boolean refundTalentPoint(EntityPlayer player, ResourceLocation tree, String line, int index) {
        if (!canRefundTalentPoint(tree, line, index))
            return false;

        TalentTreeRecord talentTree = talentTrees.get(tree);
        BaseTalent talentDef = talentTree.getTalentDefinition(line, index);
        if (talentDef.onRemove(player, this)) {
            talentTree.decrementPoint(line, index);
            unspentTalentPoints.add(1);
            return true;
        }
        return false;
    }

    private void setAbilitySpendOrder(ResourceLocation abilityId, int level) {
        if (level > 0) {
            abilitySpendOrder.set(level - 1, abilityId);
        }
    }

    @Nullable
    PlayerAbilityInfo getAbilityInfo(ResourceLocation abilityId) {
        return abilityInfoMap.get(abilityId);
    }

    private void clearUltimateAbilities() {
        for (int i = 0; i < loadedUltimates.size(); i++) {
            clearUltimateSlot(i);
        }
    }

    private void clearPassiveAbilities() {
        for (int i = 0; i < loadedPassives.size(); i++) {
            clearPassiveSlot(i);
        }
    }

    private void clearActiveAbilities() {
        for (int i = 0; i < hotbar.size(); i++) {
            setAbilityInSlot(i, MKURegistry.INVALID_ABILITY);
        }
    }

    private void clearAbilitySpendOrder() {
        abilitySpendOrder.clear();
    }

    private ResourceLocation getAbilitySpendOrder(int index) {
        ResourceLocation id = MKURegistry.INVALID_ABILITY;
        if (index > 0) {
            id = abilitySpendOrder.get(index - 1);
            abilitySpendOrder.set(index - 1, MKURegistry.INVALID_ABILITY);
        }
        return id;
    }

    TalentTreeRecord getTalentTree(ResourceLocation treeId) {
        return talentTrees.get(treeId);
    }

    public boolean learnAbility(PlayerAbility ability, boolean consumePoint, boolean placeOnBar) {
        ResourceLocation abilityId = ability.getAbilityId();

        PlayerAbilityInfo info = getAbilityInfo(abilityId);
        if (info == null) {
            info = ability.createAbilityInfo();
        }

        if (consumePoint && getUnspentPoints() == 0)
            return false;

        if (!info.upgrade())
            return false;

        if (consumePoint) {
            int curUnspent = getUnspentPoints();
            if (curUnspent > 0) {
                setUnspentPoints(curUnspent - 1);
            } else {
                return false;
            }
            setAbilitySpendOrder(abilityId, getAbilityLearnIndex());
        }

        if (placeOnBar) {
            tryPlaceOnBar(abilityId);
        }

        abilityUpdate(abilityId, info);
        return true;
    }

    public boolean unlearnAbility(ResourceLocation abilityId, boolean refundPoint, boolean allRanks) {
        PlayerAbilityInfo info = getAbilityInfo(abilityId);
        if (info == null || !info.isCurrentlyKnown()) {
            // We never knew it or it exists but is currently unlearned
            return false;
        }

        int ranks = 0;
        if (allRanks) {
            while (info.isCurrentlyKnown())
                if (info.downgrade())
                    ranks += 1;
        } else {
            if (info.downgrade())
                ranks += 1;
        }

        if (refundPoint) {
            int curUnspent = getUnspentPoints();
            setUnspentPoints(curUnspent + ranks);
        }

        abilityUpdate(abilityId, info);
        return true;
    }

    private int getAbilityLearnIndex() {
        return getLevel() - getUnspentPoints();
    }

    ResourceLocation getLastLeveledAbility() {
        return getAbilitySpendOrder(getAbilityLearnIndex());
    }

    @Override
    public boolean isDirty() {
        return dirtyUpdater.isDirty();
    }

    @Override
    public void serializeUpdate(NBTTagCompound tag) {
        dirtyUpdater.serializeUpdate(tag);

        Log.info(tag.toString());
    }

    @Override
    public void deserializeUpdate(NBTTagCompound tag) {
        dirtyUpdater.deserializeUpdate(tag);
    }

    public void serialize(NBTTagCompound tag) {
        tag.setString("id", classId.toString());
        tag.setInteger("level", level.get());
        tag.setString("classAbilityHash", playerClass.hashAbilities());
        tag.setInteger("unspentPoints", getUnspentPoints());
        serializeAbilities(tag);
        writeNBTAbilityArray(tag, "abilitySpendOrder", abilitySpendOrder, GameConstants.MAX_CLASS_LEVEL);
        writeNBTAbilityArray(tag, "hotbar", hotbar, GameConstants.ACTION_BAR_SIZE);
        serializeTalentInfo(tag);
    }

    public void deserialize(NBTTagCompound tag) {
        classId = new ResourceLocation(tag.getString("id"));
        level.set(tag.getInteger("level"));
        unspentAbilityPoints.set(tag.getInteger("unspentPoints"));
        abilitySpendOrder = parseNBTAbilityList(tag, "abilitySpendOrder", GameConstants.MAX_CLASS_LEVEL);
        hotbar = parseNBTAbilityList(tag, "hotbar", GameConstants.ACTION_BAR_SIZE);
        deserializeAbilities(tag);
        if (tag.hasKey("classAbilityHash")) {
            String abilityHash = tag.getString("classAbilityHash");
            if (!abilityHash.equals(playerClass.hashAbilities())) {
                resetClassAbilities();
            }
        } else {
            resetClassAbilities();
        }

        deserializeTalentInfo(tag);
        hotbar.forEach(this::checkHotBar);
    }

    private void serializeTalentInfo(NBTTagCompound tag) {
        tag.setInteger("unspentTalentPoints", getUnspentTalentPoints());
        tag.setInteger("totalTalentPoints", getTotalTalentPoints());
        writeNBTAbilityArray(tag, "loadedPassives", loadedPassives, GameConstants.MAX_PASSIVES);
        writeNBTAbilityArray(tag, "loadedUltimates", loadedUltimates, GameConstants.MAX_ULTIMATES);
        writeTalentTrees(tag);
    }

    private void deserializeTalentInfo(NBTTagCompound tag) {
        unspentTalentPoints.set(tag.getInteger("unspentTalentPoints"));
        totalTalentPoints.set(tag.getInteger("totalTalentPoints"));
        if (tag.hasKey("loadedPassives")) {
            loadedPassives = parseNBTAbilityList(tag, "loadedPassives", GameConstants.MAX_PASSIVES);
        }
        if (tag.hasKey("loadedUltimates")) {
            loadedUltimates = parseNBTAbilityList(tag, "loadedUltimates", GameConstants.MAX_ULTIMATES);
        }
        parseTalentTrees(tag);
        checkSlottedTalents();
    }

    private void serializeAbilities(NBTTagCompound tag) {
        NBTTagList tagList = new NBTTagList();
        for (PlayerAbilityInfo info : abilityInfoMap.values()) {
            NBTTagCompound sk = new NBTTagCompound();
            info.serialize(sk);
            tagList.appendTag(sk);
        }

        tag.setTag("abilities", tagList);
    }

    private void deserializeAbilities(NBTTagCompound tag) {
        if (tag.hasKey("abilities")) {
            NBTTagList tagList = tag.getTagList("abilities", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < tagList.tagCount(); i++) {
                NBTTagCompound abilityTag = tagList.getCompoundTagAt(i);
                ResourceLocation abilityId = new ResourceLocation(abilityTag.getString("id"));
                PlayerAbility ability = MKURegistry.getAbility(abilityId);
                if (ability == null) {
                    continue;
                }

                PlayerAbilityInfo info = ability.createAbilityInfo();
                if (info.deserialize(abilityTag))
                    abilityInfoMap.put(abilityId, info);
            }
        } else {
            clearSpentAbilities();
        }
    }

    private void writeNBTAbilityArray(NBTTagCompound tag, String name, Collection<ResourceLocation> array, int size) {
        NBTTagList list = new NBTTagList();
        if (array != null) {
            array.stream().limit(size).forEach(r -> list.appendTag(new NBTTagString(r.toString())));
        }
        tag.setTag(name, list);
    }


    private List<ResourceLocation> parseNBTAbilityList(NBTTagCompound tag, String name, int size) {
        NBTTagList list = tag.getTagList(name, Constants.NBT.TAG_STRING);
        List<ResourceLocation> ids = NonNullList.withSize(size, MKURegistry.INVALID_ABILITY);
        for (int i = 0; i < size && i < list.tagCount(); i++) {
            ids.set(i, new ResourceLocation(list.getStringTagAt(i)));
        }
        return ids;
    }

    private void writeTalentTrees(NBTTagCompound tag) {
        NBTTagCompound trees = new NBTTagCompound();
        boolean hadTalents = false;
        for (ResourceLocation loc : talentTrees.keySet()) {
            TalentTreeRecord record = talentTrees.get(loc);
            if (record.hasPointsInTree()) {
                trees.setTag(loc.toString(), record.toTag());
                hadTalents = true;
            }
        }
        if (hadTalents) {
            tag.setTag("trees", trees);
        }
    }

    private void parseTalentTrees(NBTTagCompound tag) {
        boolean doReset = false;
        if (tag.hasKey("trees")) {
            NBTTagCompound trees = tag.getCompoundTag("trees");
            for (String key : trees.getKeySet()) {
                ResourceLocation loc = new ResourceLocation(key);
                if (talentTrees.containsKey(loc)) {
                    boolean needsReset = talentTrees.get(loc).fromTag(trees.getCompoundTag(key));
                    if (needsReset) {
                        doReset = true;
                    }
                }
            }
        }
        if (doReset) {
            resetTalents();
        }
    }

    class KnownAbilityUpdater implements ISupportsPartialSync {
        private List<PlayerAbilityInfo> list;

        public KnownAbilityUpdater() {
            list = new ArrayList<>();
        }

        public void markDirty(PlayerAbilityInfo info) {
            list.add(info);
        }

        @Override
        public boolean isDirty() {
            return list.size() > 0;
        }

        @Override
        public void deserializeUpdate(NBTTagCompound tag) {
            if (tag.hasKey("abilityUpdates")) {
                NBTTagCompound abilities = tag.getCompoundTag("abilityUpdates");

                for (String id : abilities.getKeySet()) {
                    ResourceLocation abilityId = new ResourceLocation(id);
                    PlayerAbilityInfo current = abilityInfoMap.computeIfAbsent(abilityId, newAbilityId -> {
                        PlayerAbility ability = MKURegistry.getAbility(newAbilityId);
                        if (ability == null)
                            return null;

                        return ability.createAbilityInfo();
                    });
                    if (current == null)
                        continue;
                    if (!current.deserialize(abilities.getCompoundTag(id))) {
                        Log.error("Failed to deserialize ability update for %s", id);
                        continue;
                    }
                    abilityInfoMap.put(abilityId, current);
                }
            }
        }

        @Override
        public void serializeUpdate(NBTTagCompound tag) {
            if (list.size() > 0) {
                NBTTagCompound abilities = new NBTTagCompound();
                for (PlayerAbilityInfo info : list) {
                    NBTTagCompound ability = new NBTTagCompound();
                    info.serialize(ability);
                    abilities.setTag(info.getId().toString(), ability);
                }

                tag.setTag("abilityUpdates", abilities);

                list.clear();
            }
        }
    }

    class ActiveAbilityUpdater implements ISupportsPartialSync {
        private List<NBTTagCompound> dirtyActiveAbilities = new ArrayList<>();

        void markAbilityListDirty(String type, int index, ResourceLocation value) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString("type", type);
            tag.setInteger("index", index);
            tag.setString("value", value.toString());
            dirtyActiveAbilities.add(tag);
        }

        @Override
        public boolean isDirty() {
            return dirtyActiveAbilities.size() > 0;
        }

        @Override
        public void deserializeUpdate(NBTTagCompound tag) {
            NBTTagList list = tag.getTagList("lists", Constants.NBT.TAG_COMPOUND);

            for (int i = 0; i < list.tagCount(); i++) {
                NBTTagCompound entry = list.getCompoundTagAt(i);
                int index = entry.getInteger("index");
                ResourceLocation value = new ResourceLocation(entry.getString("value"));
                String listName = entry.getString("type");
                List<ResourceLocation> abilityList = null;
                switch (listName) {
                    case "hotbar":
                        abilityList = hotbar;
                        break;
                    case "passives":
                        abilityList = loadedPassives;
                        break;
                    case "ultimates":
                        abilityList = loadedUltimates;
                        break;
                }
                if (abilityList != null) {
                    abilityList.set(index, value);
                }
            }
        }

        @Override
        public void serializeUpdate(NBTTagCompound tag) {
            if (dirtyActiveAbilities.size() > 0) {
                NBTTagList list = tag.getTagList("lists", Constants.NBT.TAG_COMPOUND);
                dirtyActiveAbilities.forEach(list::appendTag);
                tag.setTag("lists", list);
                dirtyActiveAbilities.clear();
            }
        }
    }
}
