package fr.renblood.medievalstructures.inn;

import net.minecraft.network.FriendlyByteBuf;

public record InnReferenceItem(String itemId, String displayName, String category, long referencePrice) {
    public void write(FriendlyByteBuf buffer) {
        buffer.writeUtf(itemId);
        buffer.writeUtf(displayName);
        buffer.writeUtf(category);
        buffer.writeLong(referencePrice);
    }

    public static InnReferenceItem read(FriendlyByteBuf buffer) {
        return new InnReferenceItem(buffer.readUtf(), buffer.readUtf(), buffer.readUtf(), buffer.readLong());
    }
}
