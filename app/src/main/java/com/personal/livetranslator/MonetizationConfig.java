package com.personal.livetranslator;

final class MonetizationConfig {
    static final boolean CLOUD_MODE_ENABLED = false;
    static final int TRIAL_MINUTES = 5;
    static final Offer[] CLOUD_OFFERS = new Offer[]{
            new Offer("Starter", 60, "$4.99", "Best for testing a few streams."),
            new Offer("Regular", 180, "$14.99", "For a few longer sessions."),
            new Offer("Deep Watch", 400, "$29.99", "Lowest planned per-minute price.")
    };

    private MonetizationConfig() {
    }

    static String cloudStatusLabel() {
        return CLOUD_MODE_ENABLED
                ? "Cloud mode can use paid translation minutes."
                : "Cloud mode is planned, but billing and backend are not enabled in this test build.";
    }

    static String pricingSummary() {
        return "Cloud mode will be metered, not unlimited. Planned trial: "
                + TRIAL_MINUTES
                + " minutes after audio capture passes preflight.";
    }

    static final class Offer {
        final String name;
        final int minutes;
        final String price;
        final String note;

        Offer(String name, int minutes, String price, String note) {
            this.name = name;
            this.minutes = minutes;
            this.price = price;
            this.note = note;
        }
    }
}
