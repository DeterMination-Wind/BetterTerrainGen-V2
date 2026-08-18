package betterterraingen.v2;

import arc.Core;
import arc.func.Prov;
import arc.mock.MockFiles;
import arc.struct.Seq;
import betterterraingen.v2.filters.NaturalWaterFilter;
import mindustry.ai.UnitCommand;
import mindustry.ai.UnitStance;
import mindustry.Vars;
import mindustry.content.Bullets;
import mindustry.core.ContentLoader;
import mindustry.core.FileTree;
import mindustry.content.Blocks;
import mindustry.content.Items;
import mindustry.content.Liquids;
import mindustry.content.StatusEffects;
import mindustry.content.TeamEntries;
import mindustry.content.UnitTypes;
import betterterraingen.v2.NaturalWaterGenerator.Config;
import betterterraingen.v2.NaturalWaterGenerator.Result;
import mindustry.io.JsonIO;
import mindustry.maps.Maps;
import mindustry.maps.filters.GenerateFilter;
import mindustry.maps.filters.GenerateFilter.GenerateInput;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;

public final class NaturalWaterGeneratorTest {
    public static void main(String[] args) {
        deterministicForSameParameters();
        changesWithSeed();
        coverageStaysOnTarget();
        deepWaterNeverTouchesLand();
        cleanupChangesFineFeatures();
        edgeBiasChangesBorderWater();
        edgeWaterCanRemainDeep();
        cleanupLeavesNoEnclosedLand();
        cleanupRemovesSingleCellSpurs();
        extremeInputsRemainValid();
        largeMapParallelPathMatchesSingleThread();
        filterRegistrationAndSerializationAreStable();
        filterClearsNaturalTerrainAndPreservesEditorMarkers();
        optionsCanBeCreatedAcrossClassLoaderBoundary();
        RuinGenerateFilterTest.run();
        System.out.println("NaturalWaterGeneratorTest: all checks passed");
    }

    private static void deterministicForSameParameters() {
        Config config = baseConfig(160, 120, 42);
        Result first = NaturalWaterGenerator.generate(config);
        Result second = NaturalWaterGenerator.generate(config);
        check(Arrays.equals(first.layers, second.layers), "same seed and parameters must be deterministic");
    }

    private static void changesWithSeed() {
        Result first = NaturalWaterGenerator.generate(baseConfig(160, 120, 1));
        Result second = NaturalWaterGenerator.generate(baseConfig(160, 120, 2));
        check(!Arrays.equals(first.layers, second.layers), "different seeds must produce different contours");
    }

    private static void coverageStaysOnTarget() {
        int[][] sizes = {{32, 32}, {96, 64}, {180, 140}, {257, 129}};
        float[] targets = {0.12f, 0.35f, 0.68f, 0.91f};
        for (int[] size : sizes) {
            for (float target : targets) {
                Config config = baseConfig(size[0], size[1], size[0] * 31 + size[1]);
                config.coverage = target;
                Result result = NaturalWaterGenerator.generate(config);
                check(Math.abs(result.coverage() - target) <= 0.03f,
                    "coverage outside tolerance for " + size[0] + "x" + size[1] + ": " + result.coverage());
            }
        }
    }

    private static void deepWaterNeverTouchesLand() {
        Config config = baseConfig(180, 130, 99);
        config.shoalWidth = 0f;
        config.shallowWidth = 0f;
        Result result = NaturalWaterGenerator.generate(config);
        for (int y = 0; y < result.height; y++) {
            for (int x = 0; x < result.width; x++) {
                int index = x + y * result.width;
                if (result.layers[index] != NaturalWaterGenerator.deep) continue;
                checkNoLandNeighbor(result, x, y);
            }
        }
    }

    private static void cleanupChangesFineFeatures() {
        Config raw = baseConfig(112, 96, 4123);
        raw.scale = 7f;
        raw.complexity = 1f;
        raw.cleanup = false;
        Config clean = baseConfig(112, 96, 4123);
        clean.scale = raw.scale;
        clean.complexity = raw.complexity;
        clean.cleanup = true;

        Result rawResult = NaturalWaterGenerator.generate(raw);
        Result cleanResult = NaturalWaterGenerator.generate(clean);
        check(!Arrays.equals(rawResult.layers, cleanResult.layers), "cleanup toggle must alter fine features");
        check(countIsolatedWater(cleanResult) <= countIsolatedWater(rawResult),
            "cleanup must not increase isolated water tiles");
    }

    private static void edgeBiasChangesBorderWater() {
        int positive = 0;
        int negative = 0;
        for (int seed = 0; seed < 8; seed++) {
            Config coast = baseConfig(128, 96, seed * 97 + 11);
            coast.edgeBias = 0.9f;
            Config lake = baseConfig(128, 96, seed * 97 + 11);
            lake.edgeBias = -0.9f;
            positive += borderWater(NaturalWaterGenerator.generate(coast));
            negative += borderWater(NaturalWaterGenerator.generate(lake));
        }
        check(positive > negative, "positive edge bias must create more border-connected water");
    }

    private static void edgeWaterCanRemainDeep() {
        int deepBorderTiles = 0;
        for (int seed = 0; seed < 8; seed++) {
            Config config = baseConfig(160, 120, seed * 193 + 17);
            config.coverage = 0.45f;
            config.edgeBias = 0.9f;
            Result result = NaturalWaterGenerator.generate(config);
            deepBorderTiles += countDeepBorder(result);
        }
        check(deepBorderTiles > 0, "water continuing beyond the crop must be able to stay deep at the map edge");
    }

    private static void cleanupLeavesNoEnclosedLand() {
        for (int seed = 0; seed < 8; seed++) {
            Config config = baseConfig(180, 140, seed * 271 + 23);
            config.coverage = 0.42f;
            Result result = NaturalWaterGenerator.generate(config);
            check(!hasEnclosedLand(result), "cleanup left an enclosed land island for seed " + seed);
        }
    }

    private static void cleanupRemovesSingleCellSpurs() {
        for (int seed = 0; seed < 8; seed++) {
            Config config = baseConfig(180, 140, seed * 313 + 31);
            config.coverage = 0.42f;
            config.complexity = 0.9f;
            Result result = NaturalWaterGenerator.generate(config);
            check(countSingleCellSpurs(result) == 0, "cleanup left a single-cell spur for seed " + seed);
        }
    }

    private static void extremeInputsRemainValid() {
        int[][] sizes = {{1, 1}, {2, 3}, {7, 5}, {31, 17}};
        float[] coverages = {0f, 0.01f, 0.99f, 1f};
        for (int[] size : sizes) {
            for (float coverage : coverages) {
                Config config = baseConfig(size[0], size[1], 7);
                config.coverage = coverage;
                config.shoalWidth = 10_000f;
                config.shallowWidth = 10_000f;
                Result result = NaturalWaterGenerator.generate(config);
                check(result.layers.length == size[0] * size[1], "extreme input changed result dimensions");
                for (byte layer : result.layers) {
                    check(layer >= NaturalWaterGenerator.land && layer <= NaturalWaterGenerator.deep,
                        "invalid water layer value");
                }
            }
        }
    }

    private static void largeMapParallelPathMatchesSingleThread() {
        Config single = baseConfig(500, 500, 20260711);
        single.allowParallel = false;
        long started = System.nanoTime();
        Result expected = NaturalWaterGenerator.generate(single);

        Config parallel = baseConfig(500, 500, 20260711);
        parallel.allowParallel = true;
        Result actual = NaturalWaterGenerator.generate(parallel);
        long elapsedMillis = (System.nanoTime() - started) / 1_000_000L;

        check(Arrays.equals(expected.layers, actual.layers),
            "parallel and single-thread generation must produce identical output");
        System.out.println("500x500 single+parallel smoke: " + elapsedMillis + " ms");
    }

    @SuppressWarnings("unchecked")
    private static void filterRegistrationAndSerializationAreStable() {
        BetterTerrainGenV2Mod.registerFilter();
        BetterTerrainGenV2Mod.registerFilter();
        int count = 0;
        for (Prov<GenerateFilter> provider : Maps.allFilterTypes) {
            if (provider.get() instanceof NaturalWaterFilter) count++;
        }
        check(count == 1, "natural water filter must register exactly once");

        NaturalWaterFilter filter = new NaturalWaterFilter();
        filter.seed = 123456;
        filter.waterCoverage = 47f;
        filter.edgeBias = -0.35f;
        Seq<GenerateFilter> filters = Seq.with(filter);
        String json = JsonIO.write(filters);
        Seq<GenerateFilter> restored = JsonIO.read(Seq.class, json);
        check(restored.size == 1 && restored.first() instanceof NaturalWaterFilter,
            "filter must deserialize through the registered class tag");
        NaturalWaterFilter restoredFilter = (NaturalWaterFilter) restored.first();
        check(restoredFilter.seed == filter.seed && restoredFilter.waterCoverage == filter.waterCoverage
            && restoredFilter.edgeBias == filter.edgeBias, "public filter parameters must survive serialization");
    }

    private static void filterClearsNaturalTerrainAndPreservesEditorMarkers() {
        initializeMindustryContent();
        NaturalWaterFilter filter = new NaturalWaterFilter();
        filter.seed = 12;
        filter.waterCoverage = 100f;
        filter.shoalWidth = 0f;
        filter.shallowWidth = 0f;
        filter.naturalCleanup = false;

        GenerateInput natural = new GenerateInput();
        natural.begin(4, 4, (x, y) -> null);
        natural.set(0, 1, Blocks.stoneWall, Blocks.stone, Blocks.oreCopper, 0L);
        filter.apply(natural);
        check(natural.block == Blocks.air, "natural wall must be cleared inside water");
        check(natural.overlay == Blocks.air, "ore overlay must be cleared inside water");
        check(natural.floor == Blocks.deepwater, "water continuing beyond the map crop must remain deep at the edge");

        GenerateInput protectedTile = new GenerateInput();
        protectedTile.begin(4, 4, (x, y) -> null);
        protectedTile.set(2, 2, Blocks.copperWall, Blocks.stone, Blocks.spawn, 0L);
        filter.apply(protectedTile);
        check(protectedTile.block == Blocks.copperWall, "synthetic player building must be preserved");
        check(protectedTile.overlay == Blocks.spawn, "spawn overlay must be preserved");
    }

    private static void initializeMindustryContent() {
        if (Blocks.air != null) return;
        Vars.headless = true;
        Core.files = new MockFiles();
        Vars.content = new ContentLoader();
        Vars.tree = new FileTree();
        UnitCommand.loadAll();
        TeamEntries.load();
        Items.load();
        UnitStance.loadAll();
        StatusEffects.load();
        Liquids.load();
        Bullets.load();
        UnitTypes.load();
        Blocks.load();
    }

    private static void optionsCanBeCreatedAcrossClassLoaderBoundary() {
        URL mainClasses = NaturalWaterFilter.class.getProtectionDomain().getCodeSource().getLocation();
        try (ChildFirstModLoader loader = new ChildFirstModLoader(new URL[] {mainClasses},
            NaturalWaterGeneratorTest.class.getClassLoader())) {
            Class<?> filterClass = Class.forName("betterterraingen.v2.filters.NaturalWaterFilter", true, loader);
            check(filterClass.getClassLoader() == loader, "filter must be loaded by the simulated mod ClassLoader");

            Object filter = filterClass.getConstructor().newInstance();
            Object[] options = (Object[]) filterClass.getMethod("options").invoke(filter);
            check(options.length == 14, "filter must expose all editor options");
            for (Object option : options) {
                check(option.getClass().getClassLoader() == loader,
                    "filter option must be implemented by the mod ClassLoader: " + option.getClass().getName());
            }
        } catch (ReflectiveOperationException | java.io.IOException exception) {
            throw new AssertionError("filter options failed across the mod ClassLoader boundary", exception);
        }
    }

    private static Config baseConfig(int width, int height, int seed) {
        Config config = new Config().size(width, height);
        config.seed = seed;
        config.allowParallel = false;
        return config;
    }

    private static void checkNoLandNeighbor(Result result, int x, int y) {
        int[] dx = {1, -1, 0, 0};
        int[] dy = {0, 0, 1, -1};
        for (int i = 0; i < 4; i++) {
            int nx = x + dx[i];
            int ny = y + dy[i];
            if (nx < 0 || nx >= result.width || ny < 0 || ny >= result.height) continue;
            check(result.layers[nx + ny * result.width] != NaturalWaterGenerator.land,
                "deep water directly touched land at " + x + "," + y);
        }
    }

    private static int countIsolatedWater(Result result) {
        int isolated = 0;
        for (int y = 0; y < result.height; y++) {
            for (int x = 0; x < result.width; x++) {
                int index = x + y * result.width;
                if (result.layers[index] == NaturalWaterGenerator.land) continue;
                int neighbors = 0;
                if (x > 0 && result.layers[index - 1] != 0) neighbors++;
                if (x + 1 < result.width && result.layers[index + 1] != 0) neighbors++;
                if (y > 0 && result.layers[index - result.width] != 0) neighbors++;
                if (y + 1 < result.height && result.layers[index + result.width] != 0) neighbors++;
                if (neighbors == 0) isolated++;
            }
        }
        return isolated;
    }

    private static int borderWater(Result result) {
        int count = 0;
        for (int x = 0; x < result.width; x++) {
            if (result.layers[x] != 0) count++;
            if (result.height > 1 && result.layers[x + (result.height - 1) * result.width] != 0) count++;
        }
        for (int y = 1; y + 1 < result.height; y++) {
            if (result.layers[y * result.width] != 0) count++;
            if (result.width > 1 && result.layers[result.width - 1 + y * result.width] != 0) count++;
        }
        return count;
    }

    private static int countDeepBorder(Result result) {
        int count = 0;
        for (int x = 0; x < result.width; x++) {
            if (result.layers[x] == NaturalWaterGenerator.deep) count++;
            if (result.height > 1 && result.layers[x + (result.height - 1) * result.width] == NaturalWaterGenerator.deep) count++;
        }
        for (int y = 1; y + 1 < result.height; y++) {
            if (result.layers[y * result.width] == NaturalWaterGenerator.deep) count++;
            if (result.width > 1 && result.layers[result.width - 1 + y * result.width] == NaturalWaterGenerator.deep) count++;
        }
        return count;
    }

    private static boolean hasEnclosedLand(Result result) {
        boolean[] visited = new boolean[result.layers.length];
        int[] queue = new int[result.layers.length];
        for (int start = 0; start < result.layers.length; start++) {
            if (result.layers[start] != NaturalWaterGenerator.land || visited[start]) continue;
            int head = 0;
            int tail = 0;
            boolean touchesEdge = false;
            queue[tail++] = start;
            visited[start] = true;
            while (head < tail) {
                int index = queue[head++];
                int x = index % result.width;
                int y = index / result.width;
                if (x == 0 || y == 0 || x == result.width - 1 || y == result.height - 1) touchesEdge = true;
                if (x > 0) tail = enqueueLand(result, visited, queue, tail, index - 1);
                if (x + 1 < result.width) tail = enqueueLand(result, visited, queue, tail, index + 1);
                if (y > 0) tail = enqueueLand(result, visited, queue, tail, index - result.width);
                if (y + 1 < result.height) tail = enqueueLand(result, visited, queue, tail, index + result.width);
            }
            if (!touchesEdge) return true;
        }
        return false;
    }

    private static int enqueueLand(Result result, boolean[] visited, int[] queue, int tail, int index) {
        if (!visited[index] && result.layers[index] == NaturalWaterGenerator.land) {
            visited[index] = true;
            queue[tail++] = index;
        }
        return tail;
    }

    private static int countSingleCellSpurs(Result result) {
        int spurs = 0;
        for (int y = 1; y + 1 < result.height; y++) {
            for (int x = 1; x + 1 < result.width; x++) {
                int index = x + y * result.width;
                boolean water = result.layers[index] != NaturalWaterGenerator.land;
                int matching = 0;
                if ((result.layers[index - 1] != NaturalWaterGenerator.land) == water) matching++;
                if ((result.layers[index + 1] != NaturalWaterGenerator.land) == water) matching++;
                if ((result.layers[index - result.width] != NaturalWaterGenerator.land) == water) matching++;
                if ((result.layers[index + result.width] != NaturalWaterGenerator.land) == water) matching++;
                if (matching <= 1) spurs++;
            }
        }
        return spurs;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class ChildFirstModLoader extends URLClassLoader {
        ChildFirstModLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null && name.startsWith("betterterraingen.v2.")) {
                    try {
                        loaded = findClass(name);
                    } catch (ClassNotFoundException ignored) {
                        // Test-only classes are intentionally supplied by the parent loader.
                    }
                }
                if (loaded == null) loaded = super.loadClass(name, false);
                if (resolve) resolveClass(loaded);
                return loaded;
            }
        }
    }
}
