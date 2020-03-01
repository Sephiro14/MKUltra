package com.chaosbuffalo.mkultra.core;

import com.chaosbuffalo.mkultra.GameConstants;
import com.chaosbuffalo.mkultra.MKConfig;
import com.chaosbuffalo.mkultra.core.talents.BaseTalent;
import com.chaosbuffalo.mkultra.core.talents.RangedAttributeTalent;
import com.chaosbuffalo.mkultra.core.talents.TalentTree;
import com.chaosbuffalo.mkultra.core.talents.TalentTreeRecord;
import com.chaosbuffalo.mkultra.log.Log;
import com.chaosbuffalo.mkultra.network.packets.ClassUpdatePacket;
import com.google.common.collect.Maps;
import net.minecraft.entity.ai.attributes.AbstractAttributeMap;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

import javax.annotation.Nullable;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;

public class PlayerClassInfo implements ISupportsPartialSync {
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
    private List<PlayerAbilityInfo> dirtyAbilities = new ArrayList<>();
    private List<NBTTagCompound> dirtyActiveAbilities = new ArrayList<>();
    private boolean dirty;
    private String dirtyTrace;

    public PlayerClassInfo(ResourceLocation classId) {
        this.classId = classId;
        loadedPassives = NonNullList.withSize(GameConstants.MAX_PASSIVES, MKURegistry.INVALID_ABILITY);
        loadedUltimates = NonNullList.withSize(GameConstants.MAX_ULTIMATES, MKURegistry.INVALID_ABILITY);
        hotbar = NonNullList.withSize(GameConstants.ACTION_BAR_SIZE, MKURegistry.INVALID_ABILITY);
        abilitySpendOrder = NonNullList.withSize(GameConstants.MAX_CLASS_LEVEL, MKURegistry.INVALID_ABILITY);
        talentTrees = new HashMap<>();
        for (TalentTree tree : MKURegistry.REGISTRY_TALENT_TREES.getValuesCollection()) {
            talentTrees.put(tree.getRegistryName(), new TalentTreeRecord(tree));
        }
    }

    private void markDirty() {
        if (dirtyTrace == null) {
            try {
                throw new Exception("Setting class dirty");
            } catch (Exception e) {
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw));
                dirtyTrace = sw.toString();
            }
        }
        dirty = true;
    }

    private void markClean() {
        dirtyTrace = null;
        dirty = false;
    }

    IMessage getUpdateMessage() {
        if (isDirty()) {
            if (dirtyTrace != null) {
//                Log.info("class dirty stack trace");
//                Log.info(dirtyTrace);
            }
            IMessage message = new ClassUpdatePacket(this, ClassUpdatePacket.UpdateType.UPDATE);
            markClean();
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
        markDirty();
    }

    public int getUnspentPoints() {
        return unspentAbilityPoints.get();
//        return unspentPoints;
    }

    void setUnspentPoints(int unspentPoints) {
        // You shouldn't have more unspent points than your levels
        if (unspentPoints > getLevel())
            return;
        unspentAbilityPoints.set(unspentPoints);
//        this.unspentPoints = unspentPoints;
        markDirty();
    }

    public int getTotalTalentPoints() {
        return totalTalentPoints.get();
    }

    private void setTotalTalentPoints(int points) {
        totalTalentPoints.set(points);
        markDirty();
    }

    public int getUnspentTalentPoints() {
        return unspentTalentPoints.get();
    }

    private void setUnspentTalentPoints(int points) {
        unspentTalentPoints.set(points);
        markDirty();
    }

    boolean isAtTalentPointLimit() {
        int max = getTalentPointLimit();
        return max != -1 && getTotalTalentPoints() >= max;
    }

    private int getTalentPointLimit() {
        return MKConfig.gameplay.MAX_TALENT_POINTS_PER_CLASS;
    }

    public Collection<PlayerAbilityInfo> getAbilities() {
        return abilityInfoMap.values();
    }

    public List<ResourceLocation> getActivePassives() {
        return Collections.unmodifiableList(loadedPassives);
    }

    public List<ResourceLocation> getActiveUltimates() {
        return Collections.unmodifiableList(loadedUltimates);
    }

    public ResourceLocation getAbilityInSlot(int index) {
        if (index < hotbar.size()) {
            return hotbar.get(index);
        }
        return MKURegistry.INVALID_ABILITY;
    }

    public int getSlotForAbility(ResourceLocation abilityId) {
        int slot = hotbar.indexOf(abilityId);
        if (slot == -1)
            return GameConstants.ACTION_BAR_INVALID_SLOT;
        return slot;
    }

    void setAbilityInSlot(int index, ResourceLocation abilityId) {
        if (index < hotbar.size()) {
            hotbar.set(index, abilityId);
            markAbilityListDirty("hotbar", index, abilityId);
        }
    }

    boolean checkTalentTotals() {
        int maxPoints = getTalentPointLimit();
        if (maxPoints >= 0 && getTotalTalentPoints() > maxPoints) {
            Log.info("player has too many talents! %d max %d", getTotalTalentPoints(), maxPoints);
            resetTalents();
            return false;
        }
        int spent = getTotalSpentPoints();
        if (getTotalTalentPoints() - spent != getUnspentTalentPoints()) {
            resetTalents();
            return false;
        }
        return true;
    }

    void abilityUpdate(ResourceLocation abilityId, PlayerAbilityInfo info) {
        abilityInfoMap.put(abilityId, info);
        dirtyAbilities.add(info);
        checkHotBar(abilityId);
        markDirty();
    }

    public void applyPassives(EntityPlayer player, IPlayerData data, World world) {
//        Log.debug("applyPassives - loadedPassives %s %s", loadedPassives[0], loadedPassives[1]);
        for (ResourceLocation loc : loadedPassives) {
            if (!loc.equals(MKURegistry.INVALID_ABILITY)) {
                PlayerAbility ability = MKURegistry.getAbility(loc);
                if (ability != null) {
                    ability.execute(player, data, world);
                }
            }
        }
    }

    public boolean hasUltimate() {
        return getUltimateAbilitiesFromTalents().size() > 0;
    }

    private int getFirstFreeAbilitySlot() {
        return getSlotForAbility(MKURegistry.INVALID_ABILITY);
    }

    public int tryPlaceOnBar(ResourceLocation abilityId) {
        int slot = getSlotForAbility(abilityId);
        if (slot == GameConstants.ACTION_BAR_INVALID_SLOT) {
            // Skill was just learned so let's try to put it on the bar
            slot = getFirstFreeAbilitySlot();
            if (slot != GameConstants.ACTION_BAR_INVALID_SLOT) {
                setAbilityInSlot(slot, abilityId);
                return slot;
            }
        }

        return GameConstants.ACTION_BAR_INVALID_SLOT;
    }

    public boolean addPassiveToSlot(ResourceLocation abilityId, int slotIndex) {
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

    public boolean addUltimateToSlot(ResourceLocation abilityId, int slotIndex) {
        if (canAddUltimateToSlot(abilityId, slotIndex)) {
            ResourceLocation currentAbility = getUltimateForSlot(slotIndex);
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

    public int getUltimateSlot(ResourceLocation abilityId) {
        int index = loadedUltimates.indexOf(abilityId);
        if (index == -1)
            return GameConstants.ULTIMATE_INVALID_SLOT;
        return index;
    }


    public int getPassiveSlot(ResourceLocation abilityId) {
        int index = loadedPassives.indexOf(abilityId);
        if (index == -1)
            return GameConstants.PASSIVE_INVALID_SLOT;
        return index;
    }

    private void setUltimateSlot(int slotIndex, ResourceLocation abilityId) {
        loadedUltimates.set(slotIndex, abilityId);
        markAbilityListDirty("ultimates", slotIndex, abilityId);
    }

    private void setPassiveSlot(int slotIndex, ResourceLocation abilityId) {
        loadedPassives.set(slotIndex, abilityId);
        markAbilityListDirty("passives", slotIndex, abilityId);
    }

    public void clearUltimateSlot(int slotIndex) {
        ResourceLocation currentAbility = getUltimateForSlot(slotIndex);
        setUltimateSlot(slotIndex, MKURegistry.INVALID_ABILITY);
        if (!currentAbility.equals(MKURegistry.INVALID_ABILITY)) {
            removeFromHotBar(currentAbility);
        }
    }

    void removeFromHotBar(ResourceLocation abilityId) {
        int slot = getSlotForAbility(abilityId);
        if (slot != GameConstants.ACTION_BAR_INVALID_SLOT) {
            setAbilityInSlot(slot, MKURegistry.INVALID_ABILITY);
        }
    }

    public void clearPassiveSlot(int slotIndex) {
        setPassiveSlot(slotIndex, MKURegistry.INVALID_ABILITY);
    }

    public ResourceLocation getPassiveForSlot(int slotIndex) {
        if (slotIndex >= GameConstants.MAX_PASSIVES) {
            return MKURegistry.INVALID_ABILITY;
        }
        return loadedPassives.get(slotIndex);
    }

    public ResourceLocation getUltimateForSlot(int slotIndex) {
        if (slotIndex >= GameConstants.MAX_ULTIMATES) {
            return MKURegistry.INVALID_ABILITY;
        }
        return loadedUltimates.get(slotIndex);
    }

    public boolean canAddUltimateToSlot(ResourceLocation abilityId, int slotIndex) {
        return slotIndex < GameConstants.MAX_ULTIMATES && hasTrainedUltimate(abilityId);
    }

    public boolean canAddPassiveToSlot(ResourceLocation abilityId, int slotIndex) {
        return slotIndex < GameConstants.MAX_PASSIVES && hasTrainedPassive(abilityId);
    }

    public HashSet<PlayerAbility> getUltimateAbilitiesFromTalents() {
        HashSet<PlayerAbility> abilities = new HashSet<>();
        for (TalentTreeRecord rec : talentTrees.values()) {
            if (rec.hasPointsInTree()) {
                rec.getUltimatesWithPoints().forEach(talent -> abilities.add(talent.getAbility()));
            }
        }
        return abilities;
    }

    public HashSet<PlayerPassiveAbility> getPassiveAbilitiesFromTalents() {
        HashSet<PlayerPassiveAbility> abilities = new HashSet<>();
        for (TalentTreeRecord rec : talentTrees.values()) {
            if (rec.hasPointsInTree()) {
                rec.getPassivesWithPoints().forEach(talent -> abilities.add(talent.getAbility()));
            }
        }
        return abilities;
    }

    public boolean hasTrainedUltimate(ResourceLocation abilityId) {
        if (abilityId.equals(MKURegistry.INVALID_ABILITY)) {
            return true;
        }
        HashSet<PlayerAbility> abilities = getUltimateAbilitiesFromTalents();
        PlayerAbility ability = MKURegistry.getAbility(abilityId);
        return ability != null && abilities.contains(ability);
    }

    public boolean hasTrainedPassive(ResourceLocation abilityId) {
        if (abilityId.equals(MKURegistry.INVALID_ABILITY)) {
            return true;
        }
        HashSet<PlayerPassiveAbility> abilities = getPassiveAbilitiesFromTalents();
        PlayerAbility ability = MKURegistry.getAbility(abilityId);
        return ability instanceof PlayerPassiveAbility && abilities.contains(ability);
    }

    public HashSet<RangedAttributeTalent> getAttributeTalentSet() {
        HashSet<RangedAttributeTalent> attributeTalents = new HashSet<>();
        for (TalentTreeRecord rec : talentTrees.values()) {
            if (rec.hasPointsInTree()) {
                attributeTalents.addAll(rec.getAttributeTalentsWithPoints());
            }
        }
        return attributeTalents;
    }

    public Map<IAttribute, AttributeModifier> getAttributeModifiers() {
        HashSet<RangedAttributeTalent> presentTalents = getAttributeTalentSet();
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
        HashSet<PlayerPassiveAbility> knownPassives = getPassiveAbilitiesFromTalents();
        for (int i = 0; i < loadedPassives.size(); i++) {
            ResourceLocation abilityId = loadedPassives.get(i);
            PlayerAbility ability = MKURegistry.getAbility(abilityId);
            if (ability == null || (ability instanceof PlayerPassiveAbility && !knownPassives.contains(ability))) {
                clearPassiveSlot(i);
            }
        }

        HashSet<PlayerAbility> knownUltimates = getUltimateAbilitiesFromTalents();
        for (int i = 0; i < loadedUltimates.size(); i++) {
            ResourceLocation id = loadedUltimates.get(i);
            PlayerAbility ability = MKURegistry.getAbility(id);
            if (ability == null || !knownUltimates.contains(ability)) {
                clearUltimateSlot(i);
            }
        }
    }

    private void resetTalents() {
        talentTrees.values().forEach(TalentTreeRecord::reset);
        clearPassiveAbilities();
        clearUltimateAbilities();

        int maxPoints = Math.min(getTotalTalentPoints(), getTalentPointLimit());
        setTotalTalentPoints(maxPoints);
        setUnspentTalentPoints(maxPoints);
    }

    private void clearSpentAbilities() {
        setUnspentPoints(getLevel());
        clearAbilitySpendOrder();
        clearActiveAbilities();
        abilityInfoMap.clear();
        markDirty();
    }

    public void addTalentPoints(int pointCount) {
        totalTalentPoints.add(pointCount);
        unspentTalentPoints.add(pointCount);
        markDirty();
    }

    public boolean canIncrementPointInTree(ResourceLocation tree, String line, int index) {
        if (getUnspentTalentPoints() == 0)
            return false;
        TalentTreeRecord talentTree = talentTrees.get(tree);
        return talentTree != null && talentTree.canIncrementPoint(line, index);
    }

    public boolean canDecrementPointInTree(ResourceLocation tree, String line, int index) {
        TalentTreeRecord talentTree = talentTrees.get(tree);
        return talentTree != null && talentTree.canDecrementPoint(line, index);
    }

    public boolean spendTalentPoint(EntityPlayer player, ResourceLocation tree, String line, int index) {
        if (canIncrementPointInTree(tree, line, index)) {
            TalentTreeRecord talentTree = talentTrees.get(tree);
            BaseTalent talentDef = talentTree.getTalentDefinition(line, index);
            if (talentDef.onAdd(player, this)) {
                talentTree.incrementPoint(line, index);
                unspentTalentPoints.add(-1);
                markDirty();
                return true;
            }
        }
        return false;
    }

    public int getTotalSpentPoints() {
        int tot = 0;
        for (TalentTreeRecord talentTree : talentTrees.values()) {
            tot += talentTree.getPointsInTree();
        }
        return tot;
    }

    public boolean refundTalentPoint(EntityPlayer player, ResourceLocation tree, String line, int index) {
        if (canDecrementPointInTree(tree, line, index)) {
            TalentTreeRecord talentTree = talentTrees.get(tree);
            BaseTalent talentDef = talentTree.getTalentDefinition(line, index);
            if (talentDef.onRemove(player, this)) {
                talentTree.decrementPoint(line, index);
                unspentTalentPoints.add(1);
                markDirty();
                return true;
            }
        }
        return false;
    }

    public void setAbilitySpendOrder(ResourceLocation abilityId, int level) {
        if (level > 0) {
            abilitySpendOrder.set(level - 1, abilityId);
        }
    }

    @Nullable
    public PlayerAbilityInfo getAbilityInfo(ResourceLocation abilityId) {
        return abilityInfoMap.get(abilityId);
    }

    public void clearUltimateAbilities() {
        loadedUltimates.clear();
        markDirty();
    }

    public void clearPassiveAbilities() {
        loadedPassives.clear();
        markDirty();
    }

    public void clearActiveAbilities() {
        hotbar.clear();
        markDirty();
    }

    public void clearAbilitySpendOrder() {
        abilitySpendOrder.clear();
        markDirty();
    }

    public ResourceLocation getAbilitySpendOrder(int index) {
        ResourceLocation id = MKURegistry.INVALID_ABILITY;
        if (index > 0) {
            id = abilitySpendOrder.get(index - 1);
            abilitySpendOrder.set(index - 1, MKURegistry.INVALID_ABILITY);
        }
        return id;
    }

    public TalentTreeRecord getTalentTree(ResourceLocation loc) {
        return talentTrees.get(loc);
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

    public ResourceLocation getLastLeveledAbility() {
        return getAbilitySpendOrder(getAbilityLearnIndex());
    }

    @Override
    public boolean isDirty() {
        return dirtyAbilities.size() > 0 ||
                dirtyActiveAbilities.size() > 0 ||
                dirty ||
                talentTrees.values().stream().anyMatch(TalentTreeRecord::isDirty);
    }

    private void markAbilityListDirty(String type, int index, ResourceLocation value) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("type", type);
        tag.setInteger("index", index);
        tag.setString("value", value.toString());
        dirtyActiveAbilities.add(tag);
        markDirty();
    }

    void serializeAbilityLists(NBTTagCompound tag) {
        if (dirtyActiveAbilities.size() > 0) {
            NBTTagList list = tag.getTagList("lists", Constants.NBT.TAG_COMPOUND);
            dirtyActiveAbilities.forEach(list::appendTag);
            tag.setTag("lists", list);
            dirtyActiveAbilities.clear();
        }
    }

    void deserializeAbilityLists(NBTTagCompound tag) {
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
        level.serializeUpdate(tag);
        unspentAbilityPoints.serializeUpdate(tag);
        unspentTalentPoints.serializeUpdate(tag);
        totalTalentPoints.serializeUpdate(tag);
        serializeAbilityLists(tag);

        if (dirtyAbilities.size() > 0) {
            NBTTagCompound abilities = new NBTTagCompound();
            for (PlayerAbilityInfo info : dirtyAbilities) {
                NBTTagCompound ability = new NBTTagCompound();
                info.serialize(ability);
                abilities.setTag(info.getId().toString(), ability);
            }

            tag.setTag("abilityUpdates", abilities);

            dirtyAbilities.clear();
        }
        for (TalentTreeRecord record : talentTrees.values()) {
            record.serializeUpdate(tag);
        }

        Log.info(tag.toString());
    }

    @Override
    public void deserializeUpdate(NBTTagCompound tag) {
        level.deserializeUpdate(tag);
        unspentAbilityPoints.deserializeUpdate(tag);
        unspentTalentPoints.deserializeUpdate(tag);
        totalTalentPoints.deserializeUpdate(tag);
        deserializeAbilityLists(tag);

        if (tag.hasKey("abilityUpdates")) {
            NBTTagCompound abilities = tag.getCompoundTag("abilityUpdates");

            for (String id : abilities.getKeySet()) {
                ResourceLocation abilityId = new ResourceLocation(id);
                PlayerAbilityInfo current = getAbilityInfo(abilityId);
                if (current == null) {
                    PlayerAbility ability = MKURegistry.getAbility(abilityId);
                    if (ability == null) {
                        continue;
                    }

                    current = ability.createAbilityInfo();
                    abilityInfoMap.put(abilityId, current);
                }

                if (!current.deserialize(abilities.getCompoundTag(id))) {
                    Log.error("Failed to deserialize ability update for %s", id);
                    continue;
                }
            }
        }
        for (TalentTreeRecord record : talentTrees.values()) {
            record.deserializeUpdate(tag);
        }
    }

    public void serialize(NBTTagCompound tag) {
        tag.setString("id", classId.toString());
        tag.setInteger("level", level.get());
        PlayerClass classObj = MKURegistry.getClass(classId);
        if (classObj != null) {
            tag.setString("classAbilityHash", classObj.hashAbilities());
        } else {
            tag.setString("classAbilityHash", "invalid_hash");
        }

        tag.setInteger("unspentPoints", getUnspentPoints());
        serializeAbilities(tag);
        writeNBTAbilityArray(tag, "abilitySpendOrder", abilitySpendOrder, GameConstants.MAX_CLASS_LEVEL);
        writeNBTAbilityArray(tag, "hotbar", hotbar, GameConstants.ACTION_BAR_SIZE);
        serializeTalentInfo(tag);
    }

    public void deserialize(NBTTagCompound tag) {
        classId = new ResourceLocation(tag.getString("id"));
        level.set(tag.getInteger("level"));
        deserializeAbilities(tag);
        PlayerClass classObj = MKURegistry.getClass(classId);
        if (classObj != null) {
            if (tag.hasKey("classAbilityHash")) {
                String abilityHash = tag.getString("classAbilityHash");
                if (abilityHash.equals(classObj.hashAbilities())) {
                    unspentAbilityPoints.set(tag.getInteger("unspentPoints"));
                    abilitySpendOrder = parseNBTAbilityList(tag, "abilitySpendOrder", GameConstants.MAX_CLASS_LEVEL);
                    hotbar = parseNBTAbilityList(tag, "hotbar", GameConstants.ACTION_BAR_SIZE);
                } else {
                    clearSpentAbilities();
                }
            } else {
                clearSpentAbilities();
            }
        } else {
            clearSpentAbilities();
        }
        deserializeTalentInfo(tag);
    }

    private void writeActiveTalentInfo(NBTTagCompound tag) {
        tag.setInteger("unspentTalentPoints", getUnspentTalentPoints());
        tag.setInteger("totalTalentPoints", getTotalTalentPoints());
        writeNBTAbilityArray(tag, "loadedPassives", loadedPassives, GameConstants.MAX_PASSIVES);
        writeNBTAbilityArray(tag, "loadedUltimates", loadedUltimates, GameConstants.MAX_ULTIMATES);
    }

    private void readActiveTalentInfo(NBTTagCompound tag) {
        unspentTalentPoints.set(tag.getInteger("unspentTalentPoints"));
        totalTalentPoints.set(tag.getInteger("totalTalentPoints"));
        if (tag.hasKey("loadedPassives")) {
            loadedPassives = parseNBTAbilityList(tag, "loadedPassives", GameConstants.MAX_PASSIVES);
        }
        if (tag.hasKey("loadedUltimates")) {
            loadedUltimates = parseNBTAbilityList(tag, "loadedUltimates", GameConstants.MAX_ULTIMATES);
        }
    }

    public void serializeTalentInfo(NBTTagCompound tag) {
        writeActiveTalentInfo(tag);
        writeTalentTrees(tag);
    }

    public void deserializeTalentInfo(NBTTagCompound tag) {
        readActiveTalentInfo(tag);
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

    public void writeTalentTrees(NBTTagCompound tag) {
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

    public void parseTalentTrees(NBTTagCompound tag) {
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

    static class DirtyInt implements ISupportsPartialSync {
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
}
