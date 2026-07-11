package betterterraingen.v2;

import arc.func.Prov;
import arc.util.serialization.Json;
import betterterraingen.v2.filters.NaturalWaterFilter;
import mindustry.io.JsonIO;
import mindustry.maps.Maps;
import mindustry.maps.filters.GenerateFilter;
import mindustry.mod.Mod;

import java.util.Arrays;

public class BetterTerrainGenV2Mod extends Mod {
    private static final String classTag = "NaturalWater";

    public BetterTerrainGenV2Mod() {
        registerFilter();
    }

    @Override
    public void init() {
        registerFilter();
    }

    public static synchronized void registerFilter() {
        if (!containsNaturalWaterFilter()) {
            Prov<GenerateFilter>[] current = Maps.allFilterTypes;
            Prov<GenerateFilter>[] expanded = Arrays.copyOf(current, current.length + 1);
            expanded[current.length] = NaturalWaterFilter::new;
            Maps.allFilterTypes = expanded;
        }

        Json json = JsonIO.json;
        json.addClassTag(classTag, NaturalWaterFilter.class);
    }

    private static boolean containsNaturalWaterFilter() {
        for (Prov<GenerateFilter> provider : Maps.allFilterTypes) {
            try {
                if (provider.get() instanceof NaturalWaterFilter) return true;
            } catch (Throwable ignored) {
                // A provider from another mod must not prevent this filter from registering.
            }
        }
        return false;
    }
}
