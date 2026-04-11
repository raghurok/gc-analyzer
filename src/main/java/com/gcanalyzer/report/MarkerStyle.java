package com.gcanalyzer.report;

/**
 * How the chart reports should mark individual plotted sample positions on
 * top of ASCIIGraph's smoothed line. Default is {@link #OFF} — the overlay
 * is opt-in via the {@code --markers} CLI flag because every glyph we've
 * tried trades readability somewhere, and the unmarked line is the cleanest
 * default.
 *
 * <p>The four choices are calibrated from lightest to heaviest visual weight:
 * <ul>
 *   <li>{@link #OFF}    — no overlay; ASCIIGraph's line is untouched.</li>
 *   <li>{@link #DOT}    — {@code ·} (U+00B7 middle dot). Lightest marker;
 *       the eye still reads the line as continuous through it.</li>
 *   <li>{@link #CROSS}  — {@code ×} (U+00D7 multiplication sign). Classic
 *       scientific-plot point marker; slimmer than a full circle but more
 *       prominent than a middle dot.</li>
 *   <li>{@link #BULLET} — {@code ●} (U+25CF black circle). Heaviest marker;
 *       use when you need to pick out individual sample positions at a
 *       glance and don't mind the line appearing interrupted.</li>
 * </ul>
 */
public enum MarkerStyle {

    OFF(' '),
    DOT('·'),
    CROSS('×'),
    BULLET('●');

    private final char glyph;

    MarkerStyle(char glyph) {
        this.glyph = glyph;
    }

    public char glyph() {
        return glyph;
    }

    public boolean enabled() {
        return this != OFF;
    }

    /** Lowercase form so help text and default-value rendering read naturally. */
    @Override
    public String toString() {
        return name().toLowerCase();
    }
}
