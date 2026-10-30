package com.henheang.hspaceapi.payload.inbox;

// Allowed values, kept in step with the CHECK constraints in
// V100__inbox_items.sql and with apps/web/lib/inbox.ts.
public final class InboxItemValues {

    public static final String ITEM_TYPES = "^(idea|note|task|activity|phrase|dev|journal)$";
    public static final String ITEM_TYPE_MESSAGE =
            "Item type must be one of idea, note, task, activity, phrase, dev, journal";

    public static final String STATUSES = "^(inbox|processed|archived)$";
    public static final String STATUS_MESSAGE = "Status must be one of inbox, processed, archived";

    public static final String SOURCES = "^(manual|voice|ai|converted)$";
    public static final String SOURCE_MESSAGE =
            "Source must be one of manual, voice, ai, converted";

    public static final String CONVERSION_TARGETS = "^(note|task|journal)$";
    public static final String CONVERSION_TARGET_MESSAGE =
            "Converted-to type must be one of note, task, journal";

    public static final int MAX_LIST_LIMIT = 500;

    private InboxItemValues() {}
}
