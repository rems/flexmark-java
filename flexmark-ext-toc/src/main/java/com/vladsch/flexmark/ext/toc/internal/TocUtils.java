package com.vladsch.flexmark.ext.toc.internal;

import com.vladsch.flexmark.ast.Heading;
import com.vladsch.flexmark.ast.util.TextCollectingVisitor;
import com.vladsch.flexmark.ext.toc.SimTocContent;
import com.vladsch.flexmark.html.HtmlWriter;
import com.vladsch.flexmark.html.renderer.AttributablePart;
import com.vladsch.flexmark.html.renderer.NodeRendererContext;
import com.vladsch.flexmark.html.renderer.TextCollectingAppendable;
import com.vladsch.flexmark.util.Computable;
import com.vladsch.flexmark.util.Escaping;
import com.vladsch.flexmark.util.ValueRunnable;
import com.vladsch.flexmark.util.options.DelimitedBuilder;
import com.vladsch.flexmark.util.sequence.BasedSequence;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class TocUtils {
    final static public AttributablePart TOC_CONTENT = new AttributablePart("TOC_CONTENT");

    public static String getTocPrefix(TocOptions options, TocOptions defaultOptions) {
        DelimitedBuilder out = new DelimitedBuilder(" ");
        out.append("[TOC").mark();

        TocOptionsParser optionsParser = new TocOptionsParser();
        out.append(optionsParser.getOptionText(options, defaultOptions));
        out.unmark().append("]");
        out.append("\n").unmark();
        return out.toString();
    }

    public static String getSimTocPrefix(TocOptions options, TocOptions defaultOptions) {
        DelimitedBuilder out = new DelimitedBuilder(" ");
        out.append("[TOC").mark();

        SimTocOptionsParser optionsParser = new SimTocOptionsParser();
        out.append(optionsParser.getOptionText(options, defaultOptions));
        out.unmark().append("]:").mark().append('#').mark();

        String optionTitleHeading = options.getTitleHeading();
        String optionTitle = options.title;

        if (defaultOptions == null || !optionTitleHeading.equals(defaultOptions.getTitleHeading())) {
            if (!optionTitle.isEmpty()) {
                out.append('"');
                if (defaultOptions == null || options.titleLevel != defaultOptions.titleLevel) {
                    out.append(optionTitleHeading);
                } else {
                    out.append(optionTitle);
                }
                out.append('"').mark();
            } else {
                out.append("\"\"").mark();
            }
        }

        out.unmark().append("\n").unmark();
        return out.toString();
    }

    public static void renderTocContent(HtmlWriter out, String tocContents, TocOptions options, List<Heading> headings, List<String> headingTexts) {
        if (headings.isEmpty()) return;

        if (options.isHtml) {
            renderHtmlToc(out, BasedSequence.NULL, headings, headingTexts, options);
        } else {
            renderMarkdownToc(out, headings, headingTexts, options);
        }
    }

    public static void renderHtmlToc(HtmlWriter html, BasedSequence sourceText, List<Heading> headings, List<String> headingTexts, TocOptions tocOptions) {
        if (headings.size() > 0 && (sourceText.isNotNull() || !tocOptions.title.isEmpty())) {
            if (sourceText.isNotNull()) html.srcPos(sourceText);
            html.withAttr(TOC_CONTENT).tag("div").line().indent();
            html.tag("h" + tocOptions.titleLevel).text(tocOptions.title).tag("/h" + tocOptions.titleLevel).line();
        }

        int initLevel = -1;
        int lastLevel = -1;
        String listOpen = tocOptions.isNumbered ? "ol" : "ul";
        String listClose = "/" + listOpen;
        int listNesting = 0;
        boolean[] openedItems = new boolean[7];
        boolean[] openedList = new boolean[7];
        int[] openedItemAppendCount = new int[7];

        for (int i = 0; i < headings.size(); i++) {
            Heading header = headings.get(i);
            String headerText = headingTexts.get(i);
            int headerLevel = header.getLevel();

            if (initLevel == -1) {
                initLevel = headerLevel;
                lastLevel = headerLevel;
                html.withAttr().line().tag(listOpen).indent().line();
                openedList[0] = true;
            }

            if (lastLevel < headerLevel) {
                for (int lv = lastLevel; lv < headerLevel; lv++) {
                    openedItems[lv + 1] = false;
                    openedList[lv + 1] = false;
                }

                if (!openedList[lastLevel]) {
                    html.withAttr().indent().line().tag(listOpen).indent();
                    openedList[lastLevel] = true;
                }
            } else if (lastLevel == headerLevel) {
                if (openedItems[lastLevel]) {
                    if (openedList[lastLevel]) html.unIndent().tag(listClose).line();
                    html.lineIf(openedItemAppendCount[lastLevel] != html.getAppendCount()).tag("/li").line();
                }
                openedItems[lastLevel] = false;
                openedList[lastLevel] = false;
            } else {
                // lastLevel > headerLevel
                for (int lv = lastLevel; lv >= headerLevel; lv--) {
                    if (openedItems[lv]) {
                        if (openedList[lv]) html.unIndent().tag(listClose).unIndent().line();
                        html.lineIf(openedItemAppendCount[lastLevel] != html.getAppendCount()).tag("/li").line();
                    }
                    openedItems[lv] = false;
                    openedList[lv] = false;
                }
            }

            html.line().tag("li");
            openedItems[headerLevel] = true;
            String headerId = header.getAnchorRefId();
            if (headerId == null || headerId.isEmpty()) {
                html.raw(headerText);
            } else {
                html.attr("href", "#" + headerId).withAttr().tag("a");
                html.raw(headerText);
                html.tag("/a");
            }

            lastLevel = headerLevel;
            openedItemAppendCount[headerLevel] = html.getAppendCount();
        }

        for (int i = lastLevel; i >= 1; i--) {
            if (openedItems[i]) {
                if (openedList[i]) html.unIndent().tag(listClose).unIndent().line();
                html.lineIf(openedItemAppendCount[lastLevel] != html.getAppendCount()).tag("/li").line();
            }
        }

        // close original list
        if (openedList[0]) html.unIndent().tag(listClose).line();

        if (headings.size() > 0 && (sourceText.isNotNull() || !tocOptions.title.isEmpty())) {
            html.line().unIndent().tag("/div");
        }

        html.line();
    }

    public static List<Heading> filteredHeadings(List<Heading> headings, TocOptions tocOptions) {
        ArrayList<Heading> filteredHeadings = new ArrayList<>(headings.size());

        for (Heading header : headings) {
            if (tocOptions.isLevelIncluded(header.getLevel()) && !(header.getParent() instanceof SimTocContent)) {
                filteredHeadings.add(header);
            }
        }

        return filteredHeadings;
    }

    public static List<String> htmlHeaderTexts(NodeRendererContext context, List<Heading> headings, TocOptions tocOptions) {
        ArrayList<String> headerTexts = new ArrayList<>(headings.size());

        for (Heading header : headings) {
            String headerText;
            boolean isRaw;
            // need to skip anchor links but render emphasis
            if (tocOptions.isTextOnly) {
                headerText = getHeadingText(header);
                isRaw = false;
            } else {
                TextCollectingAppendable out = getHeadingContent(context, header);
                headerText = out.getHtml();
            }
            headerTexts.add(headerText);
        }

        return headerTexts;
    }

    private static String getHeadingText(Heading header) {
        return Escaping.escapeHtml(new TextCollectingVisitor().collectAndGetText(header), false);
    }

    private static TextCollectingAppendable getHeadingContent(NodeRendererContext context, Heading header) {
        TextCollectingAppendable out = new TextCollectingAppendable();
        NodeRendererContext subContext = context.getSubContext(out, false);
        subContext.doNotRenderLinks();
        subContext.renderChildren(header);
        return out;
    }

    public static List<Heading> sortAlpha(final NodeRendererContext context, List<Heading> headings, TocOptions tocOptions) {
        List<Heading> headerTexts = new ArrayList<>(headings.size());
        headerTexts.addAll(headings);

        if (tocOptions.isTextOnly) {

            Collections.sort(headerTexts, new Comparator<Heading>() {
                @Override
                public int compare(Heading heading1, Heading heading2) {
                    String header1Text = getHeadingText(heading1);
                    String header2Text = getHeadingText(heading2);
                    return header1Text.compareTo(header2Text);
                }
            });

        } else {

            Collections.sort(headerTexts, new Comparator<Heading>() {
                @Override
                public int compare(Heading heading1, Heading heading2) {
                    String header1Text = getHeadingContent(context, heading1).getHtml();
                    String header2Text = getHeadingContent(context, heading2).getHtml();
                    return header1Text.compareTo(header2Text);
                }
            });
        }

        return headerTexts;
    }

    public static List<String> markdownHeaderTexts(List<Heading> headings, TocOptions tocOptions) {
        ArrayList<String> headingTexts = new ArrayList<String>(headings.size());
        for (Heading header : headings) {
            String headerText;
            // need to skip anchor links but render emphasis
            if (tocOptions.isTextOnly) {
                headerText = new TextCollectingVisitor().collectAndGetText(header);
            } else {
                headerText = header.getChars().toString();
            }

            String headerId = header.getAnchorRefId();
            String headerLink;
            if (headerId == null || headerText.isEmpty()) {
                headerLink = headerText;
            } else {
                headerLink = "[" + headerText + "](#" + headerId + ")";
            }

            headingTexts.add(headerLink);
        }

        return headingTexts;
    }

    public static void renderMarkdownToc(HtmlWriter html, List<Heading> headings, List<String> headingTexts, final TocOptions tocOptions) {
        int initLevel = -1;
        int lastLevel = -1;
        final int[] headingNumbers = new int[7];
        final boolean[] openedItems = new boolean[7];

        Computable<String, Integer> listOpen = new Computable<String, Integer>() {
            @Override
            public String compute(Integer level) {
                openedItems[level] = true;
                if (tocOptions.isNumbered) {
                    int v = ++headingNumbers[level];
                    return v + ". ";
                } else {
                    return "- ";
                }
            }
        };

        ValueRunnable<Integer> listClose = new ValueRunnable<Integer>() {
            @Override
            public void run(Integer level) {
                if (tocOptions.isNumbered) {
                    headingNumbers[level] = 0;
                }
            }
        };

        if (headings.size() > 0 && !tocOptions.title.isEmpty()) {
            html.raw("######".substring(0, tocOptions.titleLevel)).raw(" ").raw(tocOptions.title).line();
        }

        for (int i = 0; i < headings.size(); i++) {
            Heading header = headings.get(i);
            String headerText = headingTexts.get(i);
            int headerLevel = header.getLevel();

            if (initLevel == -1) {
                initLevel = headerLevel;
                lastLevel = headerLevel;
            }

            if (lastLevel < headerLevel) {
                for (int lv = lastLevel; lv <= headerLevel - 1; lv++) {
                    openedItems[lv + 1] = false;
                }
                html.indent();
            } else if (lastLevel == headerLevel) {
                if (i != 0) {
                    html.line();
                }
            } else {
                for (int lv = lastLevel; lv >= headerLevel + 1; lv++) {
                    if (openedItems[lv]) {
                        html.unIndent();
                        listClose.run(lv);
                    }
                }
                html.line();
            }

            html.line().raw(listOpen.compute(headerLevel));
            html.raw(headerText);

            lastLevel = headerLevel;
        }

        html.line();
    }
}
