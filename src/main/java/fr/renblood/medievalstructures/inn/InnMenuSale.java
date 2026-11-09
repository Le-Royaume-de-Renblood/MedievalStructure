package fr.renblood.medievalstructures.inn;

import net.minecraft.nbt.CompoundTag;

public record InnMenuSale(String itemName, String sellerName, long price, long soldAt) {
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("ItemName", itemName);
        tag.putString("SellerName", sellerName);
        tag.putLong("Price", price);
        tag.putLong("SoldAt", soldAt);
        return tag;
    }

    public static InnMenuSale load(CompoundTag tag) {
        return new InnMenuSale(
                tag.getString("ItemName"),
                tag.getString("SellerName"),
                tag.getLong("Price"),
                tag.getLong("SoldAt")
        );
    }
}
