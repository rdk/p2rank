package cz.siret.prank.features.implementation.table;

import javax.annotation.Nonnull;

/**
 * (groovy version was slow)
 */
public class PropertyTableKey {

    @Nonnull private final String itemName;
    @Nonnull private final String propertyName;

    public PropertyTableKey(@Nonnull String itemName, @Nonnull String propertyName) {
        this.itemName = itemName;
        this.propertyName = propertyName;
    }

    PropertyTableKey reversed() {
        return new PropertyTableKey(propertyName, itemName);
    }

    @Nonnull
    public String getItemName() {
        return itemName;
    }

    @Nonnull
    public String getPropertyName() {
        return propertyName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        PropertyTableKey key = (PropertyTableKey) o;

        if (!itemName.equals(key.itemName)) return false;
        return propertyName.equals(key.propertyName);
    }

    @Override
    public int hashCode() {
        int result = itemName.hashCode();
        result = 31 * result + propertyName.hashCode();
        return result;
    }

}
