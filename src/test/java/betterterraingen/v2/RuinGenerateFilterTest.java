package betterterraingen.v2;

import arc.func.Prov;
import arc.struct.IntSeq;
import arc.struct.Seq;
import betterterraingen.v2.filters.NaturalWaterFilter;
import betterterraingen.v2.filters.RuinGenerateFilter;
import betterterraingen.v2.filters.RuinStep;
import betterterraingen.v2.filters.StructuredRuinGenerateFilter;
import mindustry.content.Blocks;
import mindustry.io.JsonIO;
import mindustry.maps.Maps;
import mindustry.maps.filters.GenerateFilter;
import mindustry.maps.filters.GenerateFilter.GenerateInput;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.environment.Floor;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Integration checks for registration, serialization, center selection, and tile writeback. */
public final class RuinGenerateFilterTest {
    private RuinGenerateFilterTest() {
    }

    public static void run() {
        registrationAndSerializationAreStable();
        overlayStepsAndSyntheticProtectionWork();
        automaticCentersAreDeterministicAndSpaced();
        automaticCentersSkipLiquidAndSyntheticTiles();
        distortionDoesNotPolluteInputCoordinates();
        structuredLayoutsAreDeterministicAndConnected();
        structuredSettingsRoundTrip();
        structuredAutoCentersRespectBoundsAndProtection();
        structuredLargeAutoUsesOneFullMapStructure();
        optionsLoadAcrossClassLoaderBoundary();
        System.out.println("RuinGenerateFilterTest: all checks passed");
    }

    private static void registrationAndSerializationAreStable() {
        BetterTerrainGenV2Mod.registerFilter();
        BetterTerrainGenV2Mod.registerFilter();

        int natural = 0;
        int ruin = 0;
        int structured = 0;
        for (Prov<GenerateFilter> provider : Maps.allFilterTypes) {
            GenerateFilter filter = provider.get();
            if (filter instanceof NaturalWaterFilter) natural++;
            if (filter.getClass() == RuinGenerateFilter.class) ruin++;
            if (filter.getClass() == StructuredRuinGenerateFilter.class) structured++;
        }
        check(natural == 1, "natural water filter must remain registered exactly once");
        check(ruin == 1, "ruin filter must register exactly once");
        check(structured == 1, "structured ruin filter must register exactly once");

        RuinGenerateFilter filter = new RuinGenerateFilter();
        filter.seed = 123456;
        filter.centerMode = RuinGenerateFilter.CenterMode.auto;
        filter.autoDensity = 41f;
        filter.minCenterSpacing = 19f;
        filter.scl = 77f;
        filter.threshold = 0.37f;
        filter.octaves = 5f;
        filter.falloff = 0.41f;
        filter.tilt = -0.8f;
        filter.distortScl = 8f;
        filter.distortMag = 2.5f;
        RuinStep step = RuinStep.floor(7f, Blocks.metalFloor);
        step.stepMode = RuinStep.StepMode.manhattan;
        filter.steps = new RuinStep[]{step, RuinStep.removeWall(3f)};

        Seq<GenerateFilter> filters = Seq.with((GenerateFilter)filter);
        String json = JsonIO.write(filters);
        check(!json.contains("\"RuinGenerate\""), "legacy NH RuinGenerate tag must not be emitted");
        check(json.contains("BetterRuinGenerate"), "BT ruin class tag must be emitted");

        Seq<GenerateFilter> restored = JsonIO.read(Seq.class, json);
        check(restored.size == 1 && restored.first() instanceof RuinGenerateFilter,
            "ruin filter must deserialize through the BT class tag");
        RuinGenerateFilter copy = (RuinGenerateFilter)restored.first();
        check(copy.centerMode == filter.centerMode && copy.autoDensity == filter.autoDensity
            && copy.minCenterSpacing == filter.minCenterSpacing, "center settings must survive JSON roundtrip");
        check(copy.scl == filter.scl && copy.threshold == filter.threshold && copy.octaves == filter.octaves
            && copy.falloff == filter.falloff && copy.tilt == filter.tilt
            && copy.distortScl == filter.distortScl && copy.distortMag == filter.distortMag,
            "noise and distortion settings must survive JSON roundtrip");
        check(copy.steps.length == 2 && copy.steps[0].stepMode == RuinStep.StepMode.manhattan
            && copy.steps[0].floor == Blocks.metalFloor && copy.steps[1].removesWall(),
            "ordered ruin steps must survive JSON roundtrip");

        StructuredRuinGenerateFilter structuredFilter = new StructuredRuinGenerateFilter();
        check(structuredFilter.generationMode == RuinGenerateFilter.GenerationMode.structured
                && structuredFilter.structurePreset == RuinGenerateFilter.StructurePreset.large,
            "standalone structured filter must default to structured Large mode");
        String structuredJson = JsonIO.write(Seq.with((GenerateFilter)structuredFilter));
        check(structuredJson.contains("BetterStructuredRuinGenerate"),
            "structured ruin class tag must be emitted");
        Seq<GenerateFilter> structuredRestored = JsonIO.read(Seq.class, structuredJson);
        check(structuredRestored.size == 1 && structuredRestored.first() instanceof StructuredRuinGenerateFilter,
            "structured ruin filter must deserialize through its standalone class tag");
    }

    private static void overlayStepsAndSyntheticProtectionWork() {
        int width = 11;
        int height = 11;
        TestTile[] tiles = grid(width, height, Blocks.stone, Blocks.air, Blocks.air);
        tiles[5 + 5 * width].setOverlayForTest(Blocks.metalTiles1);
        tiles[5 + 5 * width].setBlockForTest(Blocks.copperWall);

        RuinGenerateFilter filter = new RuinGenerateFilter();
        filter.centerMode = RuinGenerateFilter.CenterMode.overlay;
        filter.replaceOverlay = Blocks.metalTiles1;
        filter.threshold = 2f;
        filter.distortMag = 0f;
        filter.steps = new RuinStep[]{
            RuinStep.floor(2f, Blocks.metalFloor),
            RuinStep.wall(1f, Blocks.stoneWall)
        };
        applyToTiles(filter, tiles, width, height);

        check(tiles[5 + 5 * width].floor() == Blocks.metalFloor,
            "overlay center must receive the floor step");
        check(tiles[5 + 5 * width].block() == Blocks.copperWall,
            "synthetic center block must be preserved");
        check(tiles[6 + 5 * width].floor() == Blocks.metalFloor,
            "floor step must cover the configured radius");
        check(tiles[6 + 5 * width].block() == Blocks.stoneWall,
            "wall step must write a static ruin wall");
        check(tiles[8 + 5 * width].floor() == Blocks.stone,
            "tiles outside the ruin radius must remain unchanged");

        RuinStep geometric = RuinStep.floor(1f, Blocks.metalFloor);
        geometric.stepMode = RuinStep.StepMode.geometric;
        RuinStep manhattan = geometric.copy();
        manhattan.stepMode = RuinStep.StepMode.manhattan;
        RuinStep chebyshev = geometric.copy();
        chebyshev.stepMode = RuinStep.StepMode.chebyshev;
        check(geometric.matches(0, 0, 1, 1) == false, "geometric distance must exclude a diagonal at radius one");
        check(manhattan.matches(0, 0, 1, 1) == false, "Manhattan distance must exclude a diagonal at radius one");
        check(chebyshev.matches(0, 0, 1, 1), "Chebyshev distance must include a diagonal at radius one");
    }

    private static void automaticCentersAreDeterministicAndSpaced() {
        int width = 96;
        int height = 72;
        TestTile[] base = grid(width, height, Blocks.stone, Blocks.air, Blocks.air);

        RuinGenerateFilter first = autoFilter(9371);
        TestTile[] firstOutput = applyToTiles(first, copy(base), width, height);
        IntSeq firstCenters = centers(first);

        RuinGenerateFilter same = autoFilter(9371);
        TestTile[] sameOutput = applyToTiles(same, copy(base), width, height);
        check(Arrays.equals(floorIds(firstOutput), floorIds(sameOutput)),
            "auto mode must be deterministic for the same seed");
        check(Arrays.equals(centerPositions(firstCenters), centerPositions(centers(same))),
            "auto center positions must be deterministic for the same seed");

        RuinGenerateFilter different = autoFilter(9372);
        TestTile[] differentOutput = applyToTiles(different, copy(base), width, height);
        check(!Arrays.equals(floorIds(firstOutput), floorIds(differentOutput)),
            "auto mode must change with the map seed");

        for (int i = 0; i < firstCenters.size; i++) {
            int firstPosition = firstCenters.get(i);
            int firstX = firstPosition % width;
            int firstY = firstPosition / width;
            for (int j = i + 1; j < firstCenters.size; j++) {
                int secondPosition = firstCenters.get(j);
                int secondX = secondPosition % width;
                int secondY = secondPosition / width;
                int dx = firstX - secondX;
                int dy = firstY - secondY;
                check(dx * dx + dy * dy >= 12 * 12,
                    "auto centers must respect the minimum spacing");
            }
        }
    }

    private static void automaticCentersSkipLiquidAndSyntheticTiles() {
        int width = 5;
        int height = 5;
        TestTile[] liquidMap = grid(width, height, Blocks.water, Blocks.air, Blocks.air);
        liquidMap[2 + 2 * width].setFloorForTest(Blocks.stone);

        RuinGenerateFilter filter = autoFilter(1);
        filter.autoDensity = 100f;
        filter.minCenterSpacing = 24f;
        TestTile[] output = applyToTiles(filter, liquidMap, width, height);
        check(filter.centerCount() == 1, "Auto must select the only eligible land tile");
        check(output[2 + 2 * width].floor() == Blocks.metalFloor,
            "Auto must process the eligible land center");
        check(output[0].floor() == Blocks.water,
            "Auto must not process liquid floors");

        TestTile[] protectedMap = grid(width, height, Blocks.stone, Blocks.air, Blocks.air);
        protectedMap[2 + 2 * width].setBlockForTest(Blocks.copperWall);
        RuinGenerateFilter protectedFilter = autoFilter(1);
        protectedFilter.autoDensity = 100f;
        TestTile[] protectedOutput = applyToTiles(protectedFilter, protectedMap, width, height);
        check(protectedFilter.centerCount() == 1,
            "Auto should select an eligible land tile while skipping the protected one");
        check(protectedOutput[2 + 2 * width].block() == Blocks.copperWall,
            "Auto must skip protected synthetic blocks");
    }

    private static void distortionDoesNotPolluteInputCoordinates() {
        int width = 17;
        int height = 17;
        TestTile[] tiles = grid(width, height, Blocks.stone, Blocks.air, Blocks.air);
        tiles[8 + 8 * width].setOverlayForTest(Blocks.metalTiles1);

        RuinGenerateFilter filter = new RuinGenerateFilter();
        filter.seed = 44;
        filter.threshold = 2f;
        filter.distortScl = 7f;
        filter.distortMag = 6f;
        filter.steps = new RuinStep[]{RuinStep.floor(3f, Blocks.metalFloor)};

        GenerateInput input = new GenerateInput();
        input.begin(width, height, (x, y) -> tiles[x + y * width]);
        int modified = 0;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                TestTile tile = tiles[x + y * width];
                input.set(tile);
                filter.apply(input);
                check(input.x == x && input.y == y,
                    "distortion must not modify GenerateInput coordinates");
                if (input.floor == Blocks.metalFloor) modified++;
                tile.write(input);
            }
        }
        check(modified > 0, "distortion test must write at least one transformed floor");
    }

    private static void optionsLoadAcrossClassLoaderBoundary() {
        URL mainClasses = RuinGenerateFilter.class.getProtectionDomain().getCodeSource().getLocation();
        try (ChildFirstModLoader loader = new ChildFirstModLoader(new URL[]{mainClasses},
            RuinGenerateFilterTest.class.getClassLoader())) {
            Class<?> filterClass = Class.forName("betterterraingen.v2.filters.RuinGenerateFilter", true, loader);
            Object filter = filterClass.getConstructor().newInstance();
            Object[] options = (Object[])filterClass.getMethod("options").invoke(filter);
            check(options.length == 13, "scatter ruin filter must expose all editor options");
            for (Object option : options) {
                check(option.getClass().getClassLoader() == loader,
                    "ruin option must be implemented by the mod ClassLoader: " + option.getClass().getName());
            }
            filterClass.getField("generationMode").set(filter,
                Enum.valueOf((Class<Enum>)Class.forName(
                    "betterterraingen.v2.filters.RuinGenerateFilter$GenerationMode", true, loader), "structured"));
            Object[] structuredOptions = (Object[])filterClass.getMethod("options").invoke(filter);
            check(structuredOptions.length == 18,
                "structured ruin filter must expose preset and material options");
            for (Object option : structuredOptions) {
                check(option.getClass().getClassLoader() == loader,
                    "structured option must be implemented by the mod ClassLoader: "
                        + option.getClass().getName());
            }
            Class<?> dialogClass = Class.forName("betterterraingen.v2.ui.RuinStepsDialog", true, loader);
            check(dialogClass.getClassLoader() == loader,
                "ruin step dialog must be loaded by the mod ClassLoader");
        } catch (ReflectiveOperationException | IOException exception) {
            throw new AssertionError("ruin filter options failed across the mod ClassLoader boundary", exception);
        }
    }

    private static void structuredLayoutsAreDeterministicAndConnected() {
        int width = 160;
        int height = 160;
        TestTile[] base = grid(width, height, Blocks.stone, Blocks.air, Blocks.air);
        base[80 + 80 * width].setOverlayForTest(Blocks.metalTiles1);

        RuinGenerateFilter first = structuredOverlayFilter(77);
        TestTile[] firstOutput = applyToTiles(first, copy(base), width, height);
        RuinGenerateFilter same = structuredOverlayFilter(77);
        TestTile[] sameOutput = applyToTiles(same, copy(base), width, height);
        check(Arrays.equals(floorIds(firstOutput), floorIds(sameOutput)),
            "structured mode must be deterministic for the same seed");
        check(Arrays.equals(blockIds(firstOutput), blockIds(sameOutput)),
            "structured walls must be deterministic for the same seed");

        RuinGenerateFilter different = structuredOverlayFilter(78);
        TestTile[] differentOutput = applyToTiles(different, copy(base), width, height);
        check(!Arrays.equals(floorIds(firstOutput), floorIds(differentOutput))
                || !Arrays.equals(blockIds(firstOutput), blockIds(differentOutput)),
            "structured mode must change with the map seed");

        Set<Integer> structure = new HashSet<>();
        int structureFloorCount = 0;
        int mainFloorCount = 0;
        int panelFloorCount = 0;
        int wallCount = 0;
        int accentWallCount = 0;
        int mainWallCount = 0;
        int debrisWallCount = 0;
        Set<Integer> damagedFloors = new HashSet<>();
        for (int i = 0; i < firstOutput.length; i++) {
            Block floor = firstOutput[i].floor();
            if (floor == Blocks.metalFloor || floor == Blocks.metalTiles1
                || floor == Blocks.metalTiles2 || floor == Blocks.metalTiles3
                || floor == Blocks.metalTiles4 || floor == Blocks.metalFloorDamaged
                || floor == Blocks.metalFloor2 || floor == Blocks.metalFloor3
                || floor == Blocks.metalFloor4 || floor == Blocks.metalFloor5
                || floor == Blocks.metalTiles8 || floor == Blocks.metalTiles11) {
                structure.add(i);
                structureFloorCount++;
            }
            if (floor == Blocks.metalFloorDamaged || floor == Blocks.metalFloor2
                || floor == Blocks.metalFloor3 || floor == Blocks.metalFloor4
                || floor == Blocks.metalFloor5 || floor == Blocks.metalTiles8
                || floor == Blocks.metalTiles11) {
                damagedFloors.add((int)floor.id);
            }
            if (floor == Blocks.metalTiles1) mainFloorCount++;
            if (floor == Blocks.metalTiles2 || floor == Blocks.metalTiles3
                || floor == Blocks.metalTiles4) panelFloorCount++;
            if (firstOutput[i].block() == Blocks.metalWall1) {
                accentWallCount++;
                wallCount++;
            } else if (firstOutput[i].block() == Blocks.metalWall2) {
                mainWallCount++;
                wallCount++;
            } else if (firstOutput[i].block() == Blocks.metalWall3) {
                debrisWallCount++;
                wallCount++;
            }
        }
        check(structureFloorCount > 100, "structured layout must generate a substantial floor plan");
        check(wallCount > 20, "structured layout must generate static walls");
        check(mainFloorCount > 20, "structured layout must generate a distinct main corridor");
        check(panelFloorCount > 0, "structured layout must generate varied panel floors");
        check(damagedFloors.size() >= 2,
            "structured layout must generate multiple damaged floor materials");
        check(accentWallCount > 0 && mainWallCount > 0,
            "structured layout must use distinct main and secondary wall materials");
        check(debrisWallCount > 0, "structured layout must generate detached debris walls");
        check(isConnected(structure, width, height),
            "structured rooms and corridors must remain connected");
    }

    private static void structuredSettingsRoundTrip() {
        RuinGenerateFilter filter = new RuinGenerateFilter();
        filter.generationMode = RuinGenerateFilter.GenerationMode.structured;
        filter.structurePreset = RuinGenerateFilter.StructurePreset.medium;
        filter.structureFloor = Blocks.metalFloor4;
        filter.structureMainFloor = Blocks.metalTiles3;
        filter.structureWall = Blocks.metalWall3;
        filter.structureAccentWall = Blocks.metalWall1;
        filter.structureDamageFloor = Blocks.metalFloor5;

        String json = JsonIO.write(Seq.with((GenerateFilter)filter));
        Seq<GenerateFilter> restored = JsonIO.read(Seq.class, json);
        RuinGenerateFilter copy = (RuinGenerateFilter)restored.first();
        check(copy.generationMode == RuinGenerateFilter.GenerationMode.structured,
            "generation mode must survive JSON roundtrip");
        check(copy.structurePreset == RuinGenerateFilter.StructurePreset.medium
                && copy.structureFloor == Blocks.metalFloor4
                && copy.structureMainFloor == Blocks.metalTiles3
                && copy.structureWall == Blocks.metalWall3
                && copy.structureAccentWall == Blocks.metalWall1
                && copy.structureDamageFloor == Blocks.metalFloor5,
            "structured preset and materials must survive JSON roundtrip");

        String oldJson = "[{\"class\":\"BetterRuinGenerate\",\"seed\":7}]";
        Seq<GenerateFilter> oldRestored = JsonIO.read(Seq.class, oldJson);
        RuinGenerateFilter old = (RuinGenerateFilter)oldRestored.first();
        check(old.generationMode == RuinGenerateFilter.GenerationMode.scatter
                && old.structurePreset == RuinGenerateFilter.StructurePreset.large
                && old.structureFloor == Blocks.metalFloor
                && old.structureMainFloor == Blocks.metalTiles1
                && old.structureWall == Blocks.metalWall2
                && old.structureAccentWall == Blocks.metalWall1
                && old.structureDamageFloor == Blocks.metalFloorDamaged,
            "old JSON without structured fields must retain safe defaults");
    }

    private static void structuredAutoCentersRespectBoundsAndProtection() {
        int width = 128;
        int height = 128;
        TestTile[] tiles = grid(width, height, Blocks.stone, Blocks.air, Blocks.air);
        tiles[64 + 64 * width].setBlockForTest(Blocks.copperWall);

        RuinGenerateFilter filter = new RuinGenerateFilter();
        filter.seed = 123;
        filter.centerMode = RuinGenerateFilter.CenterMode.auto;
        filter.generationMode = RuinGenerateFilter.GenerationMode.structured;
        filter.structurePreset = RuinGenerateFilter.StructurePreset.medium;
        filter.autoDensity = 100f;
        filter.minCenterSpacing = 0f;
        filter.threshold = 2f;
        filter.distortMag = 0f;
        filter.structureFloor = Blocks.metalFloor2;
        filter.structureWall = Blocks.metalWall1;
        filter.structureDamageFloor = Blocks.metalFloorDamaged;
        TestTile[] output = applyToTiles(filter, tiles, width, height);

        IntSeq selected = centers(filter);
        int span = Math.min(128, Math.max(34, Math.round(128f * 0.34f)));
        for (int i = 0; i < selected.size; i++) {
            int position = selected.get(i);
            int x = position % width;
            int y = position / width;
            check(x - span / 2 >= 0 && y - span / 2 >= 0
                    && x - span / 2 + span - 1 < width
                    && y - span / 2 + span - 1 < height,
                "structured Auto centers must keep their full bounding boxes on the map");
            for (int j = i + 1; j < selected.size; j++) {
                int other = selected.get(j);
                int otherX = other % width;
                int otherY = other / width;
                check(Math.abs(x - otherX) >= span || Math.abs(y - otherY) >= span,
                    "structured Auto bounding boxes must not overlap");
            }
        }
        check(output[64 + 64 * width].block() == Blocks.copperWall,
            "structured Auto must preserve synthetic blocks");
    }

    private static void structuredLargeAutoUsesOneFullMapStructure() {
        int width = 256;
        int height = 256;
        TestTile[] tiles = grid(width, height, Blocks.stone, Blocks.air, Blocks.air);

        RuinGenerateFilter filter = new RuinGenerateFilter();
        filter.seed = 991;
        filter.centerMode = RuinGenerateFilter.CenterMode.auto;
        filter.generationMode = RuinGenerateFilter.GenerationMode.structured;
        filter.structurePreset = RuinGenerateFilter.StructurePreset.large;
        filter.autoDensity = 100f;
        filter.minCenterSpacing = 0f;
        filter.threshold = 2f;
        filter.distortMag = 0f;
        TestTile[] output = applyToTiles(filter, tiles, width, height);

        IntSeq selected = centers(filter);
        check(selected.size == 1, "structured Large Auto must generate exactly one center");

        int expectedSpan = Math.round(Math.min(width, height) * 0.80f);
        int minX = width;
        int minY = height;
        int maxX = -1;
        int maxY = -1;
        for (int i = 0; i < output.length; i++) {
            Block floor = output[i].floor();
            Block block = output[i].block();
            if (!isStructuredFloor(floor) && block != Blocks.metalWall1
                && block != Blocks.metalWall2 && block != Blocks.metalWall3) continue;
            int x = i % width;
            int y = i / width;
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
        }

        check(minX >= 0 && minY >= 0 && maxX < width && maxY < height,
            "structured Large must remain inside the map");
        check(maxX - minX + 1 >= expectedSpan - 4
                && maxY - minY + 1 >= expectedSpan - 4,
            "structured Large must occupy about 80% of the map");
    }

    private static boolean isStructuredFloor(Block floor) {
        return floor == Blocks.metalFloor || floor == Blocks.metalTiles1
            || floor == Blocks.metalTiles2 || floor == Blocks.metalTiles3
            || floor == Blocks.metalTiles4 || floor == Blocks.metalFloorDamaged
            || floor == Blocks.metalFloor2 || floor == Blocks.metalFloor3
            || floor == Blocks.metalFloor4 || floor == Blocks.metalFloor5
            || floor == Blocks.metalTiles8 || floor == Blocks.metalTiles11;
    }

    private static RuinGenerateFilter structuredOverlayFilter(int seed) {
        RuinGenerateFilter filter = new RuinGenerateFilter();
        filter.seed = seed;
        filter.centerMode = RuinGenerateFilter.CenterMode.overlay;
        filter.generationMode = RuinGenerateFilter.GenerationMode.structured;
        filter.structurePreset = RuinGenerateFilter.StructurePreset.large;
        filter.replaceOverlay = Blocks.metalTiles1;
        filter.threshold = 2f;
        filter.distortMag = 0f;
        return filter;
    }

    private static RuinGenerateFilter autoFilter(int seed) {
        RuinGenerateFilter filter = new RuinGenerateFilter();
        filter.seed = seed;
        filter.centerMode = RuinGenerateFilter.CenterMode.auto;
        filter.autoDensity = 100f;
        filter.minCenterSpacing = 12f;
        filter.threshold = 2f;
        filter.distortMag = 0f;
        filter.steps = new RuinStep[]{RuinStep.floor(0f, Blocks.metalFloor)};
        return filter;
    }

    private static TestTile[] applyToTiles(RuinGenerateFilter filter, TestTile[] tiles, int width, int height) {
        GenerateInput input = new GenerateInput();
        input.begin(width, height, (x, y) -> tiles[x + y * width]);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                TestTile tile = tiles[x + y * width];
                input.set(tile);
                filter.apply(input);
                tile.write(input);
            }
        }
        return tiles;
    }

    private static TestTile[] grid(int width, int height, Block floor, Block overlay, Block block) {
        TestTile[] result = new TestTile[width * height];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                result[x + y * width] = new TestTile(x, y, floor, overlay, block);
            }
        }
        return result;
    }

    private static TestTile[] copy(TestTile[] source) {
        TestTile[] result = new TestTile[source.length];
        for (int i = 0; i < source.length; i++) {
            TestTile tile = source[i];
            result[i] = new TestTile(tile.x, tile.y, tile.floor(), tile.overlay(), tile.block());
        }
        return result;
    }

    private static int[] floorIds(TestTile[] tiles) {
        int[] result = new int[tiles.length];
        for (int i = 0; i < tiles.length; i++) result[i] = tiles[i].floor().id;
        return result;
    }

    private static int[] blockIds(TestTile[] tiles) {
        int[] result = new int[tiles.length];
        for (int i = 0; i < tiles.length; i++) result[i] = tiles[i].block().id;
        return result;
    }

    private static boolean isConnected(Set<Integer> structure, int width, int height) {
        if (structure.isEmpty()) return false;
        Set<Integer> visited = new HashSet<>();
        int first = structure.iterator().next();
        int[] queue = new int[structure.size()];
        int head = 0;
        int tail = 0;
        queue[tail++] = first;
        visited.add(first);
        while (head < tail) {
            int current = queue[head++];
            int x = current % width;
            int y = current / width;
            int[] neighbors = {current - 1, current + 1, current - width, current + width};
            for (int neighbor : neighbors) {
                if (neighbor < 0 || neighbor >= width * height || visited.contains(neighbor)
                    || !structure.contains(neighbor)) continue;
                int neighborX = neighbor % width;
                if (Math.abs(neighborX - x) > 1) continue;
                visited.add(neighbor);
                queue[tail++] = neighbor;
            }
        }
        return visited.size() == structure.size();
    }

    private static IntSeq centers(RuinGenerateFilter filter) {
        try {
            Field field = RuinGenerateFilter.class.getDeclaredField("centers");
            field.setAccessible(true);
            return (IntSeq)field.get(filter);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("failed to inspect generated center positions", exception);
        }
    }

    private static int[] centerPositions(IntSeq values) {
        int[] result = new int[values.size];
        for (int i = 0; i < values.size; i++) result[i] = values.get(i);
        return result;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class TestTile extends Tile {
        TestTile(int x, int y, Block floor, Block overlay, Block block) {
            super(x, y);
            this.floor = (Floor)floor;
            this.overlay = (Floor)overlay;
            this.block = block;
        }

        void setFloorForTest(Block value) {
            this.floor = (Floor)value;
        }

        void setOverlayForTest(Block value) {
            this.overlay = (Floor)value;
        }

        void setBlockForTest(Block value) {
            this.block = value;
        }

        void write(GenerateInput input) {
            if (input.floor instanceof Floor floor) this.floor = floor;
            if (input.overlay instanceof Floor overlay) this.overlay = overlay;
            if (input.block != null) this.block = input.block;
        }
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
