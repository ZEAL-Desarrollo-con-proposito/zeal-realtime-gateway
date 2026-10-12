package co.zeal.realtime.events;

/** Canales que publican el Wellbeing y el Marketplace Service. */
public final class Channels {

    public static final String SLOTS = "zeal.wellbeing.slots";
    public static final String FREED_SLOT_OFFERED = "zeal.wellbeing.freed-slots.offered";
    public static final String FREED_SLOT_TAKEN = "zeal.wellbeing.freed-slots.taken";
    public static final String ROOM_UPDATED = "zeal.wellbeing.rooms.updated";
    public static final String NOTIFICATIONS = "zeal.notifications";
    public static final String PROJECT_PUBLISHED = "zeal.marketplace.projects.published";
    public static final String PROJECT_SPOTS = "zeal.marketplace.projects.spots";
    public static final String APPLICATIONS = "zeal.marketplace.applications";
    public static final String WORKSHOP_PUBLISHED = "zeal.marketplace.workshops.published";
    public static final String WORKSHOP_SPOTS = "zeal.marketplace.workshops.spots";
    public static final String ENROLLMENTS = "zeal.marketplace.enrollments";

    private Channels() {
    }
}
