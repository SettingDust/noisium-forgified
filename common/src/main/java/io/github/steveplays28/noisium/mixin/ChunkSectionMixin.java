package io.github.steveplays28.noisium.mixin;

import net.minecraft.util.registry.RegistryEntry;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeCoords;
import net.minecraft.world.biome.source.BiomeSupplier;
import net.minecraft.world.biome.source.util.MultiNoiseUtil;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.PalettedContainer;
import org.spongepowered.asm.mixin.*;

@Mixin(ChunkSection.class)
public abstract class ChunkSectionMixin {
	@Unique
	private static final int noisium$sliceSize = 4;

	@Mutable
	@Shadow
	@Final
	private PalettedContainer<RegistryEntry<Biome>> biomeContainer;

	@Shadow
	public abstract int getYOffset();

	/**
	 * @author Steveplays28
	 * @reason Axis order micro-optimisation
	 */
	@Overwrite
	public void populateBiomes(BiomeSupplier biomeSupplier, MultiNoiseUtil.MultiNoiseSampler sampler, int x, int z) {
		PalettedContainer<RegistryEntry<Biome>> palettedContainer = biomeContainer;
		palettedContainer.lock();

		try {
			int y = BiomeCoords.fromBlock(this.getYOffset());
			for (int posY = 0; posY < noisium$sliceSize; ++posY) {
				for (int posZ = 0; posZ < noisium$sliceSize; ++posZ) {
					for (int posX = 0; posX < noisium$sliceSize; ++posX) {
						palettedContainer.swapUnsafe(posX, posY, posZ, biomeSupplier.getBiome(x + posX, y + posY, z + posZ, sampler));
					}
				}
			}
		} finally {
			palettedContainer.unlock();
		}

		this.biomeContainer = palettedContainer;
	}
}
