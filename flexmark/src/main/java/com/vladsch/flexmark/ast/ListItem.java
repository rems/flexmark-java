package com.vladsch.flexmark.ast;

import com.vladsch.flexmark.util.sequence.BasedSequence;

import java.util.List;

public abstract class ListItem extends Block {
    protected BasedSequence openingMarker = BasedSequence.NULL;
    private boolean tight = true;
    private boolean hadBlankAfterItemParagraph = false;

    @Override
    public void getAstExtra(StringBuilder out) {
        segmentSpanChars(out, openingMarker, "open");
        if (isTight()) out.append(" isTight");
        else out.append(" isLoose");
        if (isHadBlankAfterItemParagraph()) out.append(" hadBlankLineAfter");
    }

    @Override
    public BasedSequence[] getSegments() {
        return new BasedSequence[] { openingMarker };
    }

    public BasedSequence getOpeningMarker() {
        return openingMarker;
    }

    public void setOpeningMarker(BasedSequence openingMarker) {
        this.openingMarker = openingMarker;
    }

    public void setTight(boolean tight) {
        this.tight = tight;
    }

    public void setLoose(boolean loose) {
        this.tight = !loose;
    }

    public boolean isTight() {
        return tight && isInTightList();
    }

    public boolean isLoose() {
        return !isTight();
    }

    public boolean isParagraphWrappingDisabled() {
        return false;
    }

    public boolean isInTightList() {
        return !(getParent() instanceof ListBlock) || ((ListBlock) getParent()).isTight();
    }

    public boolean isHadBlankAfterItemParagraph() {
        return hadBlankAfterItemParagraph;
    }

    @SuppressWarnings("SameParameterValue")
    public void setHadBlankAfterItemParagraph(boolean hadBlankAfterItemParagraph) {
        this.hadBlankAfterItemParagraph = hadBlankAfterItemParagraph;
    }

    public boolean isParagraphInTightListItem(Paragraph node) {
        if (!isTight()) return false;

        // see if this is the first paragraph child item
        Node child = getFirstChild();
        while (child != null && !(child instanceof Paragraph)) child = child.getNext();
        return child == node;
    }

    @Override
    public Node getLastBlankLineChild() {
        return getLastChild();
    }

    public ListItem() {
    }

    public ListItem(BasedSequence chars) {
        super(chars);
    }

    public ListItem(BasedSequence chars, List<BasedSequence> segments) {
        super(chars, segments);
    }

    public ListItem(BlockContent blockContent) {
        super(blockContent);
    }
}
