package com.eu.habbo.habbohotel.catalog;

/** Pure validation and checked arithmetic for client-controlled catalog quantities. */
public final class CatalogPurchaseLimits {
    public static final int MAXIMUM_QUANTITY = 100;

    private CatalogPurchaseLimits() {
    }

    public static boolean isValidAmount(int amount) {
        return amount > 0 && amount <= MAXIMUM_QUANTITY;
    }

    public static int checkedPriceProduct(int price, int quantity) {
        if (price < 0 || quantity < 0) {
            return -1;
        }
        long total = (long) price * quantity;
        return total <= Integer.MAX_VALUE ? (int) total : -1;
    }
}
