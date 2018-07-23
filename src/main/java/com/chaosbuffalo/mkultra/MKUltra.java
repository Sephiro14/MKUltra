package com.chaosbuffalo.mkultra;

import com.chaosbuffalo.mkultra.command.MKCommand;
import com.chaosbuffalo.mkultra.item.MKUltraTab;
import com.chaosbuffalo.mkultra.network.PacketHandler;
import com.chaosbuffalo.mkultra.party.PartyCommand;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.*;
import org.apache.logging.log4j.Logger;


@Mod(modid = MKUltra.MODID, name= MKUltra.MODNAME, version = MKUltra.VERSION,
        dependencies="required-after:targeting_api;")
public class MKUltra {
    public static final String MODID = "mkultra";
    public static final String VERSION = "@VERSION@";
    public static final String MODNAME = "MKUltra";

    public static final CreativeTabs MKULTRA_TAB = new MKUltraTab(CreativeTabs.getNextID(), MODID + ".general");

    @Mod.Instance
    public static MKUltra INSTANCE = new MKUltra();

    @SidedProxy(clientSide = "com.chaosbuffalo.mkultra.ClientProxy",
            serverSide = "com.chaosbuffalo.mkultra.ServerProxy")
    public static CommonProxy proxy;

    public static PacketHandler packetHandler;
    public static Logger LOG;

    @EventHandler
    public void preInit(FMLPreInitializationEvent e) {
        LOG = e.getModLog();
        proxy.preInit(e);
    }

    @EventHandler
    public void init(FMLInitializationEvent e) {
        proxy.init(e);
    }

    @EventHandler
    public void postInit(FMLPostInitializationEvent e) {
        proxy.postInit(e);
    }

    @EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new PartyCommand());
        event.registerServerCommand(new MKCommand());
    }
}
