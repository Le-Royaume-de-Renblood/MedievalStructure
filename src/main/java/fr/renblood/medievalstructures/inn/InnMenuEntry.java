package fr.renblood.medievalstructures.inn;

import net.minecraft.nbt.CompoundTag;

public class InnMenuEntry {
    public static final int MIN_MARKUP_PERCENT = -50;
    public static final int MAX_MARKUP_PERCENT = 100;
    public static final int MARKUP_STEP = 5;

    public enum Type {
        DISH,
        DRINK,
        STARTER;

        public static Type fromId(String id) {
            for (Type type : values()) {
                if (type.name().equalsIgnoreCase(id)) return type;
            }
            return DISH;
        }

        public String displayName() {
            return switch (this) {
                case DISH -> "Plat";
                case DRINK -> "Boisson";
                case STARTER -> "Starter";
            };
        }
    }

    private final String itemId;
    private final String displayName;
    private final long referencePrice;
    private Type type;
    private int markupPercent;

    public InnMenuEntry(String itemId, String displayName, long referencePrice, Type type, int markupPercent) {
        this.itemId = itemId;
        this.displayName = displayName;
        this.referencePrice = referencePrice;
        this.type = type;
        this.markupPercent = clampMarkup(markupPercent);
    }

    public String getItemId() {
        return itemId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public long getReferencePrice() {
        return referencePrice;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type == null ? Type.DISH : type;
    }

    public int getMarkupPercent() {
        return markupPercent;
    }

    public void addMarkupStep(int direction) {
        markupPercent = clampMarkup(markupPercent + (direction * MARKUP_STEP));
    }

    public long getFinalPrice() {
        return Math.max(1L, Math.round(referencePrice * (100.0D + markupPercent) / 100.0D));
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("ItemId", itemId);
        tag.putString("DisplayName", displayName);
        tag.putLong("ReferencePrice", referencePrice);
        tag.putString("Type", type.name());
        tag.putInt("MarkupPercent", markupPercent);
        return tag;
    }

    public static InnMenuEntry load(CompoundTag tag) {
        return new InnMenuEntry(
                tag.getString("ItemId"),
                tag.getString("DisplayName"),
                tag.getLong("ReferencePrice"),
                Type.fromId(tag.getString("Type")),
                tag.getInt("MarkupPercent")
        );
    }

    private static int clampMarkup(int value) {
        int stepped = Math.round(value / (float) MARKUP_STEP) * MARKUP_STEP;
        return Math.max(MIN_MARKUP_PERCENT, Math.min(MAX_MARKUP_PERCENT, stepped));
    }
}
