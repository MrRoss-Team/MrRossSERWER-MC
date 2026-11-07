package pl.easytp.mixin;

import com.mojang.brigadier.ParseResults;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.easytp.session.CommandGuard;

@Mixin(Commands.class)
abstract class CommandsMixin {
	@Inject(method = "performCommand", at = @At("HEAD"), cancellable = true)
	private void easytp$guard(ParseResults<CommandSourceStack> parseResults, String command, CallbackInfo info) {
		if (CommandGuard.blocks(parseResults.getContext().getSource(), command)) {
			info.cancel();
		}
	}
}
