package org.waste.of.time;

import net.fabricmc.loader.api.FabricLoader;

public class LoaderInfo {
    public static String getVersion() {
        return FabricLoader.getInstance().getModContainer("worldtools")
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("dev");
    }
}
