package com.alabtnt.bingo.game;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Server-side inventory/chest sorting (requirement 4). */
public final class SortService {
	private SortService() {
	}

	public static void sort(ServerPlayer player, int containerId, boolean sortContainer, boolean sortPlayer) {
		AbstractContainerMenu menu = player.containerMenu;
		if (menu == null || menu.containerId != containerId) return;

		if (sortContainer) {
			if (menu instanceof ChestMenu chest) {
				int size = chest.getContainer().getContainerSize();
				sortRange(menu, 0, size);
				if (sortPlayer) sortRange(menu, size, size + 36);
			}
		} else if (menu instanceof InventoryMenu inventory) {
			sortRange(menu, InventoryMenu.INV_SLOT_START, InventoryMenu.USE_ROW_SLOT_END);
		} else if (sortPlayer) {
			int size = menu instanceof ChestMenu chest ? chest.getContainer().getContainerSize() : 0;
			sortRange(menu, size, size + 36);
		}
		menu.broadcastChanges();
	}

	private static void sortRange(AbstractContainerMenu menu, int from, int to) {
		if (from < 0 || to > menu.slots.size() || from >= to) return;
		List<ItemStack> stacks = new ArrayList<>();
		for (int i = from; i < to; i++) {
			Slot slot = menu.slots.get(i);
			ItemStack stack = slot.getItem();
			if (!stack.isEmpty()) stacks.add(stack.copy());
			slot.set(ItemStack.EMPTY);
		}

		List<ItemStack> merged = new ArrayList<>();
		for (ItemStack stack : stacks) {
			for (ItemStack existing : merged) {
				if (ItemStack.isSameItemSameComponents(existing, stack)) {
					int space = existing.getMaxStackSize() - existing.getCount();
					int move = Math.min(space, stack.getCount());
					existing.grow(move);
					stack.shrink(move);
					if (stack.isEmpty()) break;
				}
			}
			if (!stack.isEmpty()) merged.add(stack);
		}
		merged.sort(Comparator
				.comparing((ItemStack stack) -> {
					Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
					return id == null ? "" : id.toString();
				})
				.thenComparingInt(ItemStack::getCount));

		int slotIndex = from;
		for (ItemStack stack : merged) {
			while (!stack.isEmpty() && slotIndex < to) {
				Slot slot = menu.slots.get(slotIndex++);
				int max = Math.min(slot.getMaxStackSize(stack), stack.getMaxStackSize());
				int count = Math.min(max, stack.getCount());
				ItemStack placed = stack.copyWithCount(count);
				slot.set(placed);
				stack.shrink(count);
			}
		}
	}
}
