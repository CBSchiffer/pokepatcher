package com.clokkworkk.pokepatcher;

import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

public class PreLaunchManager implements PreLaunchEntrypoint {

    @Override
    public void onPreLaunch() {
        // This code runs before the game is launched.
        // It is used to modify the game before it starts.
        // For example, you can use this to modify the classpath or add new classes.
        PokePatcher.LOGGER.info("Packaging datapacks...");
        DatapackManager.packageDatapacks();
    }
}
