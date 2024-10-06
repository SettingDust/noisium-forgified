package io.github.steveplays28.noisium.mixin;

import net.minecraft.world.biome.source.BiomeCoords;
import net.minecraft.world.biome.source.util.VanillaTerrainParameters;
import net.minecraft.world.gen.chunk.GenerationShapeConfig;
import net.minecraft.world.gen.chunk.NoiseSamplingConfig;
import net.minecraft.world.gen.chunk.SlideConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Caches the horizontalCellBlockCount and verticalCellBlockCount, so it doesn't have to convert from biome coordinates to block coordinates every time.
 */
@Mixin(GenerationShapeConfig.class)
public abstract class GenerationShapeConfigMixin {
	@Unique
	private int noisium$horizontalCellBlockCount;
	@Unique
	private int noisium$verticalCellBlockCount;

	@Inject(method = "<init>", at = @At(value = "TAIL"))
	private void noisium$createCacheHorizontalAndVerticalCellBlockCountInject(final int minimumY, final int height, final NoiseSamplingConfig sampling, final SlideConfig topSlide, final SlideConfig bottomSlide, final int horizontalSize, final int verticalSize, final VanillaTerrainParameters vanillaTerrainParameters, final CallbackInfo ci) {
		noisium$horizontalCellBlockCount = BiomeCoords.toBlock(horizontalSize);
		noisium$verticalCellBlockCount = BiomeCoords.toBlock(verticalSize);
	}

	@Inject(method = "horizontalBlockSize", at = @At(value = "HEAD"), cancellable = true)
	private void noisium$horizontalBlockSizeGetFromCacheInject(CallbackInfoReturnable<Integer> cir) {
		cir.setReturnValue(noisium$horizontalCellBlockCount);
	}

	@Inject(method = "verticalBlockSize", at = @At(value = "HEAD"), cancellable = true)
	private void noisium$verticalBlockSizeGetFromCacheInject(CallbackInfoReturnable<Integer> cir) {
		cir.setReturnValue(noisium$verticalCellBlockCount);
	}
}
