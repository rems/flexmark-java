package com.vladsch.flexmark.configure;

import com.vladsch.flexmark.ast.DoNotDecorate;
import com.vladsch.flexmark.ast.Node;
import com.vladsch.flexmark.util.sequence.BasedSequence;

/**
 * A node containing the abbreviated text that will be rendered as an abbr tag or a link with title attribute
 */
public class Configurable extends Node implements DoNotDecorate {
    protected final String abbreviation;

    public Configurable(BasedSequence chars, String abbreviation) {
        super(chars);
        this.abbreviation = abbreviation;
    }

    public String getAbbreviation() {
        return abbreviation;
    }

    @Override
    public BasedSequence[] getSegments() {
        return EMPTY_SEGMENTS;
    }

    @Override
    public void getAstExtra(StringBuilder out) {
        astExtraChars(out);
    }

    @Override
    protected String toStringAttributes() {
        return "text=" + getChars();
    }

}
