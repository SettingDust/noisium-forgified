package io.github.steveplays28.noisium.mixin;

import net.minecraft.SharedConstants;
import net.minecraft.block.BlockState;
import net.minecraft.structure.StructureSet;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.RegistryEntry;
import net.minecraft.util.registry.RegistryEntryList;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.*;
import net.minecraft.world.gen.noise.NoiseConfig;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Mixin(NoiseChunkGenerator.class)
public abstract class NoiseChunkGeneratorMixin extends ChunkGenerator {
    @Shadow
    @Final
    protected RegistryEntry<ChunkGeneratorSettings> settings;

    @Shadow protected abstract ChunkNoiseSampler createChunkNoiseSampler(Chunk chunk, StructureAccessor world, Blender blender, NoiseConfig noiseConfig);

    private NoiseChunkGeneratorMixin(
        final Registry<StructureSet> arg,
        final Optional<RegistryEntryList<StructureSet>> optional,
        final BiomeSource arg2
    ) {
        super(arg, optional, arg2);
    }

    /**
     * @return
     * @author Steveplays28
     * @reason Direct palette storage blockstate set optimisation
     */
    @Overwrite
    public final Chunk populateNoise(Blender blender, StructureAccessor structureAccessor, NoiseConfig noiseConfig, Chunk chunk, int minimumCellY, int cellHeight) {
        final ChunkNoiseSampler chunkNoiseSampler = chunk.getOrCreateChunkNoiseSampler(chunk2 -> createChunkNoiseSampler(chunk2, structureAccessor, blender, noiseConfig));
        final Heightmap oceanFloorHeightMap = chunk.getHeightmap(Heightmap.Type.OCEAN_FLOOR_WG);
        final Heightmap worldSurfaceHeightMap = chunk.getHeightmap(Heightmap.Type.WORLD_SURFACE_WG);
        final ChunkPos chunkPos = chunk.getPos();
        final int chunkPosStartX = chunkPos.getStartX();
        final int chunkPosStartZ = chunkPos.getStartZ();
        final var aquiferSampler = chunkNoiseSampler.getAquiferSampler();

        chunkNoiseSampler.sampleStartNoise();

        final int horizontalCellBlockCount = chunkNoiseSampler.horizontalBlockSize;
        final int verticalCellBlockCount = chunkNoiseSampler.verticalBlockSize;
        final int horizontalCellCount = 16 / horizontalCellBlockCount;
        final var mutableBlockPos = new BlockPos.Mutable();

        for (int baseHorizontalWidthCellIndex = 0; baseHorizontalWidthCellIndex < horizontalCellCount; ++baseHorizontalWidthCellIndex) {
            chunkNoiseSampler.sampleEndNoise(baseHorizontalWidthCellIndex);

            for (int baseHorizontalLengthCellIndex = 0; baseHorizontalLengthCellIndex < horizontalCellCount; ++baseHorizontalLengthCellIndex) {
                var nextChunkSectionIndex = chunk.countVerticalSections() - 1;
                var chunkSection = chunk.getSection(nextChunkSectionIndex);

                for (int verticalCellHeightIndex = cellHeight - 1; verticalCellHeightIndex >= 0; --verticalCellHeightIndex) {
                    chunkNoiseSampler.sampleNoiseCorners(verticalCellHeightIndex, baseHorizontalLengthCellIndex);

                    for (int verticalCellBlockIndex = verticalCellBlockCount - 1; verticalCellBlockIndex >= 0; --verticalCellBlockIndex) {
                        int blockPosY = (minimumCellY + verticalCellHeightIndex) * verticalCellBlockCount + verticalCellBlockIndex;
                        int chunkSectionBlockPosY = blockPosY & 0xF;
                        int chunkSectionIndex = chunk.getSectionIndex(blockPosY);

                        if (nextChunkSectionIndex != chunkSectionIndex) {
                            nextChunkSectionIndex = chunkSectionIndex;
                            chunkSection = chunk.getSection(chunkSectionIndex);
                        }

                        double deltaY = (double) verticalCellBlockIndex / verticalCellBlockCount;
                        chunkNoiseSampler.sampleNoiseY(blockPosY, deltaY);

                        for (int horizontalWidthCellBlockIndex = 0; horizontalWidthCellBlockIndex < horizontalCellBlockCount; ++horizontalWidthCellBlockIndex) {
                            int blockPosX = chunkPosStartX + baseHorizontalWidthCellIndex * horizontalCellBlockCount + horizontalWidthCellBlockIndex;
                            int chunkSectionBlockPosX = blockPosX & 0xF;
                            double deltaX = (double) horizontalWidthCellBlockIndex / horizontalCellBlockCount;

                            chunkNoiseSampler.sampleNoiseX(blockPosX, deltaX);

                            for (int horizontalLengthCellBlockIndex = 0; horizontalLengthCellBlockIndex < horizontalCellBlockCount; ++horizontalLengthCellBlockIndex) {
                                int blockPosZ = chunkPosStartZ + baseHorizontalLengthCellIndex * horizontalCellBlockCount + horizontalLengthCellBlockIndex;
                                int chunkSectionBlockPosZ = blockPosZ & 0xF;
                                double deltaZ = (double) horizontalLengthCellBlockIndex / horizontalCellBlockCount;

                                chunkNoiseSampler.sampleNoiseZ(blockPosZ, deltaZ);
                                BlockState blockState = chunkNoiseSampler.sampleBlockState();

                                if (blockState == null) {
                                    blockState = ((NoiseChunkGenerator) (Object) this).getSettings().value().defaultBlock();
                                }

                                if (blockState == NoiseChunkGenerator.AIR || SharedConstants.isOutsideGenerationArea(chunk.getPos())) {
                                    continue;
                                }

                                // Update the non empty block count to avoid issues with MC's lighting engine and other systems not recognising the direct palette storage set
                                // See ChunkSection#setBlockState
                                chunkSection.nonEmptyBlockCount += 1;

                                if (!blockState.getFluidState().isEmpty()) {
                                    chunkSection.nonEmptyFluidCount += 1;
                                }

                                if (blockState.hasRandomTicks()) {
                                    chunkSection.randomTickableBlockCount += 1;
                                }

                                // Set the blockstate in the palette storage directly to improve performance
                                var blockStateId = chunkSection.blockStateContainer.data.palette.index(blockState);
                                chunkSection.blockStateContainer.data.storage().set(
                                        chunkSection.blockStateContainer.paletteProvider.computeIndex(
                                                chunkSectionBlockPosX,
                                                chunkSectionBlockPosY,
                                                chunkSectionBlockPosZ
                                        ), blockStateId);

                                oceanFloorHeightMap.trackUpdate(chunkSectionBlockPosX, blockPosY, chunkSectionBlockPosZ, blockState);
                                worldSurfaceHeightMap.trackUpdate(chunkSectionBlockPosX, blockPosY, chunkSectionBlockPosZ, blockState);

                                if (!aquiferSampler.needsFluidTick() || blockState.getFluidState().isEmpty()) {
                                    continue;
                                }

                                mutableBlockPos.set(blockPosX, blockPosY, blockPosZ);
                                chunk.markBlockForPostProcessing(mutableBlockPos);
                            }
                        }
                    }
                }
            }

            chunkNoiseSampler.swapBuffers();
        }

        chunkNoiseSampler.stopInterpolation();
        return chunk;
    }

    /**
     * @author Steveplays28
     * @reason Micro-optimisation
     */
    @Overwrite
    @SuppressWarnings("ForLoopReplaceableByForEach")
    public CompletableFuture<Chunk> populateNoise(Executor executor, Blender blender, NoiseConfig noiseConfig, StructureAccessor structureAccessor, Chunk chunk) {
        GenerationShapeConfig generationShapeConfig = this.settings.value().generationShapeConfig().trimHeight(chunk.getHeightLimitView());
        int minimumY = generationShapeConfig.minimumY();
        int generationShapeHeightFloorDiv = Math.floorDiv(generationShapeConfig.height(), generationShapeConfig.verticalBlockSize());

        if (generationShapeHeightFloorDiv <= 0) {
            return CompletableFuture.completedFuture(chunk);
        }

        int minimumYFloorDiv = Math.floorDiv(minimumY, generationShapeConfig.verticalBlockSize());
        int startingChunkSectionIndex = chunk.getSectionIndex(generationShapeHeightFloorDiv * generationShapeConfig.verticalBlockSize() - 1 + minimumY);
        int minimumYChunkSectionIndex = chunk.getSectionIndex(minimumY);
        ArrayList<ChunkSection> chunkSections = new ArrayList<>();

        for (int chunkSectionIndex = startingChunkSectionIndex; chunkSectionIndex >= minimumYChunkSectionIndex; --chunkSectionIndex) {
            ChunkSection chunkSection = chunk.getSection(chunkSectionIndex);

            chunkSection.lock();
            chunkSections.add(chunkSection);
        }

        return CompletableFuture.supplyAsync(
                Util.debugSupplier("wgen_fill_noise",
                        () -> this.populateNoise(
                                blender,
                                structureAccessor,
                                noiseConfig,
                                chunk,
                                minimumYFloorDiv,
                                generationShapeHeightFloorDiv
                        )
                ), Util.getMainWorkerExecutor()).whenCompleteAsync((chunk2, throwable) -> {
            // Replace an enhanced for loop with a fori loop
            for (int i = 0; i < chunkSections.size(); i++) {
                chunkSections.get(i).unlock();
            }
        }, executor);
    }
}
