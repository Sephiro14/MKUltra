package com.chaosbuffalo.mkultra.core.abilities;

import com.chaosbuffalo.mkultra.GameConstants;
import com.chaosbuffalo.mkultra.MKUltra;
import com.chaosbuffalo.mkultra.core.BaseAbility;
import com.chaosbuffalo.mkultra.core.IPlayerData;
import com.chaosbuffalo.mkultra.api.Targeting;
import com.chaosbuffalo.mkultra.effects.spells.WhirlpoolPotion;
import com.chaosbuffalo.mkultra.fx.ParticleEffects;
import com.chaosbuffalo.mkultra.network.packets.server.ParticleEffectSpawnPacket;
import com.chaosbuffalo.mkultra.utils.RayTraceUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.MobEffects;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.List;

/**
 * Created by Jacob on 3/25/2018.
 */
public class WaveDash extends BaseAbility {

    public static float BASE_DAMAGE = 4.0f;
    public static float DAMAGE_SCALE = 3.0f;
    public static float DASH_DISTANCE = 8.0f;

    public WaveDash() {
        super(MKUltra.MODID, "ability.wave_dash");
    }

    @Override
    public String getAbilityName() {
        return "Wave Dash";
    }

    @Override
    public String getAbilityDescription() {
        return "Dashes forward hitting all targets that cross your path.";
    }

    @Override
    public String getAbilityType() {
        return "Line Attack";
    }

    @Override
    public ResourceLocation getAbilityIcon(){
        return new ResourceLocation(MKUltra.MODID, "textures/class/abilities/wave_dash.png");
    }

    @Override
    public int getCooldown(int currentLevel) {
        return 8 - currentLevel;
    }

    @Override
    public int getType() {
        return ACTIVE_ABILITY;
    }

    @Override
    public Targeting.TargetType getTargetType() {
        return Targeting.TargetType.ENEMY;
    }

    @Override
    public int getManaCost(int currentLevel) {
        return 2 + currentLevel * 2;
    }

    @Override
    public float getDistance(int currentLevel) {
        return DASH_DISTANCE;
    }

    @Override
    public int getRequiredLevel(int currentLevel) {
        return currentLevel * 2;
    }

    @Override
    public void execute(EntityPlayer entity, IPlayerData pData, World theWorld) {
        int level = pData.getLevelForAbility(getAbilityId());
        pData.startAbility(this);
        Vec3d look = entity.getLookVec().scale(DASH_DISTANCE);
        Vec3d from = entity.getPositionVector().addVector(0, entity.getEyeHeight(), 0);
        Vec3d to = from.add(look);

        Vec3d lookVec = entity.getLookVec();
        List<Entity> entityHit = getTargetsInLine(entity, from, to, true);
        float damage = BASE_DAMAGE + DAMAGE_SCALE * level;
        for (Entity entHit : entityHit){
            if (entHit instanceof EntityLivingBase){
                if (((EntityLivingBase)entHit).isPotionActive(WhirlpoolPotion.INSTANCE)){
                    damage = damage * 2.0f;
                }
            }
            entHit.attackEntityFrom(DamageSource.causeIndirectMagicDamage(entity, entity), damage);
            pData.setCooldown(getAbilityId(), Math.max(0, pData.getCurrentAbilityCooldown(getAbilityId()) - 2));
            if (entHit instanceof EntityLivingBase){
                EntityLivingBase livEnt = (EntityLivingBase) entHit;
                livEnt.addPotionEffect(new PotionEffect(MobEffects.WEAKNESS, 2 * GameConstants.TICKS_PER_SECOND * level, level, false, true));
                livEnt.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 2 * GameConstants.TICKS_PER_SECOND * level, 100, false, true));
            }

            MKUltra.packetHandler.sendToAllAround(
                    new ParticleEffectSpawnPacket(
                            EnumParticleTypes.WATER_BUBBLE.getParticleID(),
                            ParticleEffects.CIRCLE_MOTION, 20, 10,
                            entHit.posX, entHit.posY + 1.0,
                            entHit.posZ, 1.0, 1.0, 1.0, 2.0,
                            lookVec),
                    entity.dimension, entHit.posX,
                    entHit.posY, entHit.posZ, 50.0f);
        }
        RayTraceResult blockHit = RayTraceUtils.rayTraceBlocks(entity.getEntityWorld(), from, to, false);
        if (blockHit != null && blockHit.typeOfHit == RayTraceResult.Type.BLOCK)
        {
            to = blockHit.hitVec;
        }
        entity.setPositionAndUpdate(to.x, to.y, to.z);
        MKUltra.packetHandler.sendToAllAround(
                new ParticleEffectSpawnPacket(
                        EnumParticleTypes.END_ROD.getParticleID(),
                        ParticleEffects.SPHERE_MOTION, 60, 10,
                        entity.posX, entity.posY + 1.0,
                        entity.posZ, 1.0, 1.0, 1.0, 2.0,
                        lookVec),
                entity.dimension, entity.posX,
                entity.posY, entity.posZ, 50.0f);

    }
}

