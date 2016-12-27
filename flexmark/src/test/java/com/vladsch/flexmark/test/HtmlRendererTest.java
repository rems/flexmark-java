package com.vladsch.flexmark.test;

import com.vladsch.flexmark.ast.FencedCodeBlock;
import com.vladsch.flexmark.ast.Image;
import com.vladsch.flexmark.ast.Link;
import com.vladsch.flexmark.ast.Node;
import com.vladsch.flexmark.html.*;
import com.vladsch.flexmark.html.renderer.*;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.html.Attributes;
import com.vladsch.flexmark.util.options.DataHolder;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class HtmlRendererTest {

    @Test
    public void htmlAllowingShouldNotEscapeInlineHtml() {
        String rendered = htmlAllowingRenderer().render(parse("paragraph with <span id='foo' class=\"bar\">inline &amp; html</span>"));
        assertEquals("<p>paragraph with <span id='foo' class=\"bar\">inline &amp; html</span></p>\n", rendered);
    }

    @Test
    public void htmlAllowingShouldNotEscapeBlockHtml() {
        String rendered = htmlAllowingRenderer().render(parse("<div id='foo' class=\"bar\">block &amp;</div>"));
        assertEquals("<div id='foo' class=\"bar\">block &amp;</div>\n", rendered);
    }

    @Test
    public void htmlEscapingShouldEscapeInlineHtml() {
        String rendered = htmlEscapingRenderer().render(parse("paragraph with <span id='foo' class=\"bar\">inline &amp; html</span>"));
        // Note that &amp; is not escaped, as it's a normal text node, not part of the inline HTML.
        assertEquals("<p>paragraph with &lt;span id='foo' class=&quot;bar&quot;&gt;inline &amp; html&lt;/span&gt;</p>\n", rendered);
    }

    @Test
    public void htmlEscapingShouldEscapeHtmlBlocks() {
        String rendered = htmlEscapingRenderer().render(parse("<div id='foo' class=\"bar\">block &amp;</div>"));
        assertEquals("&lt;div id='foo' class=&quot;bar&quot;&gt;block &amp;amp;&lt;/div&gt;\n", rendered);
    }

    @Test
    public void textEscaping() {
        String rendered = defaultRenderer().render(parse("escaping: & < > \" '"));
        assertEquals("<p>escaping: &amp; &lt; &gt; &quot; '</p>\n", rendered);
    }

    @Test
    public void percendEncodeUrlDisabled() {
        assertEquals("<p><a href=\"foo&amp;bar\">a</a></p>\n", defaultRenderer().render(parse("[a](foo&amp;bar)")));
        assertEquals("<p><a href=\"ä\">a</a></p>\n", defaultRenderer().render(parse("[a](ä)")));
        assertEquals("<p><a href=\"foo%20bar\">a</a></p>\n", defaultRenderer().render(parse("[a](foo%20bar)")));
    }

    @Test
    public void percentEncodeUrl() {
        // Entities are escaped anyway
        assertEquals("<p><a href=\"foo&amp;bar\">a</a></p>\n", percentEncodingRenderer().render(parse("[a](foo&amp;bar)")));
        // Existing encoding is preserved
        assertEquals("<p><a href=\"foo%20bar\">a</a></p>\n", percentEncodingRenderer().render(parse("[a](foo%20bar)")));
        assertEquals("<p><a href=\"foo%61\">a</a></p>\n", percentEncodingRenderer().render(parse("[a](foo%61)")));
        // Invalid encoding is escaped
        assertEquals("<p><a href=\"foo%25\">a</a></p>\n", percentEncodingRenderer().render(parse("[a](foo%)")));
        assertEquals("<p><a href=\"foo%25a\">a</a></p>\n", percentEncodingRenderer().render(parse("[a](foo%a)")));
        assertEquals("<p><a href=\"foo%25a_\">a</a></p>\n", percentEncodingRenderer().render(parse("[a](foo%a_)")));
        assertEquals("<p><a href=\"foo%25xx\">a</a></p>\n", percentEncodingRenderer().render(parse("[a](foo%xx)")));
        // Reserved characters are preserved, except for '[' and ']'
        assertEquals("<p><a href=\"!*'();:@&amp;=+$,/?#%5B%5D\">a</a></p>\n", percentEncodingRenderer().render(parse("[a](!*'();:@&=+$,/?#[])")));
        // Unreserved characters are preserved
        assertEquals("<p><a href=\"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_.~\">a</a></p>\n",
                percentEncodingRenderer().render(parse("[a](ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_.~)")));
        // Other characters are percent-encoded (LATIN SMALL LETTER A WITH DIAERESIS)
        assertEquals("<p><a href=\"%C3%A4\">a</a></p>\n",
                percentEncodingRenderer().render(parse("[a](ä)")));
        // Other characters are percent-encoded (MUSICAL SYMBOL G CLEF, surrogate pair in UTF-16)
        assertEquals("<p><a href=\"%F0%9D%84%9E\">a</a></p>\n",
                percentEncodingRenderer().render(parse("[a](\uD834\uDD1E)")));
    }

    @Test
    public void attributeProviderForCodeBlock() {
        AttributeProviderFactory factory = new IndependentAttributeProviderFactory() {
            @Override
            public AttributeProvider create(NodeRendererContext context) {
                //noinspection ReturnOfInnerClass
                return new AttributeProvider() {
                    @Override
                    public void setAttributes(Node node, AttributablePart part, Attributes attributes) {
                        if (node instanceof FencedCodeBlock && part == CoreNodeRenderer.CODE_CONTENT) {
                            FencedCodeBlock fencedCodeBlock = (FencedCodeBlock) node;
                            // Remove the default attribute for info
                            attributes.remove("class");
                            // Put info in custom attribute instead
                            attributes.replaceValue("data-custom", fencedCodeBlock.getInfo().toString());
                        }
                    }
                };
            }
        };

        HtmlRenderer renderer = HtmlRenderer.builder().attributeProviderFactory(factory).build();
        String rendered = renderer.render(parse("```info\ncontent\n```"));
        assertEquals("<pre><code data-custom=\"info\">content\n</code></pre>\n", rendered);

        String rendered2 = renderer.render(parse("```evil\"\ncontent\n```"));
        assertEquals("<pre><code data-custom=\"evil&quot;\">content\n</code></pre>\n", rendered2);
    }

    @Test
    public void attributeProviderForImage() {
        AttributeProviderFactory factory = new IndependentAttributeProviderFactory() {
            @Override
            public AttributeProvider create(NodeRendererContext context) {
                return new AttributeProvider() {
                    @Override
                    public void setAttributes(Node node, AttributablePart part, Attributes attributes) {
                        if (node instanceof Image) {
                            attributes.remove("alt");
                            attributes.replaceValue("test", "hey");
                        }
                    }
                };
            }
        };

        HtmlRenderer renderer = HtmlRenderer.builder().attributeProviderFactory(factory).build();
        String rendered = renderer.render(parse("![foo](/url)\n"));
        assertEquals("<p><img src=\"/url\" test=\"hey\" /></p>\n", rendered);
    }

    @Test
    public void overrideNodeRender() {
        final NodeRendererFactory nodeRendererFactory = new NodeRendererFactory() {
            @Override
            public NodeRenderer create(final DataHolder options) {
                return new NodeRenderer() {
                    @Override
                    public Set<NodeRenderingHandler<?>> getNodeRenderingHandlers() {
                        HashSet<NodeRenderingHandler<?>> set = new HashSet<>();
                        set.add(new NodeRenderingHandler<>(Link.class, new CustomNodeRenderer<Link>() {
                            @Override
                            public void render(Link node, NodeRendererContext context, HtmlWriter html) {
                                context.getHtmlWriter().text("test");
                            }
                        }));

                        return set;
                    }
                };
            }
        };

        HtmlRenderer renderer = HtmlRenderer.builder().nodeRendererFactory(nodeRendererFactory).build();
        String rendered = renderer.render(parse("foo [bar](/url)"));
        assertEquals("<p>foo test</p>\n", rendered);
    }

    @Test
    public void orderedListStartZero() {
        assertEquals("<ol start=\"0\">\n<li>Test</li>\n</ol>\n", defaultRenderer().render(parse("0. Test\n")));
    }

    @Test
    public void imageAltTextWithSoftLineBreak() {
        assertEquals("<p><img src=\"/url\" alt=\"foo\nbar\" /></p>\n",
                defaultRenderer().render(parse("![foo\nbar](/url)\n")));
    }

    @Test
    public void imageAltTextWithHardLineBreak() {
        assertEquals("<p><img src=\"/url\" alt=\"foo\nbar\" /></p>\n",
                defaultRenderer().render(parse("![foo  \nbar](/url)\n")));
    }

    @Test
    public void imageAltTextWithEntities() {
        assertEquals("<p><img src=\"/url\" alt=\"foo \u00E4\" /></p>\n",
                defaultRenderer().render(parse("![foo &auml;](/url)\n")));
    }

    private static HtmlRenderer defaultRenderer() {
        return HtmlRenderer.builder().build();
    }

    private static HtmlRenderer htmlAllowingRenderer() {
        return HtmlRenderer.builder().escapeHtml(false).build();
    }

    private static HtmlRenderer htmlEscapingRenderer() {
        return HtmlRenderer.builder().escapeHtml(true).build();
    }

    private static HtmlRenderer percentEncodingRenderer() {
        return HtmlRenderer.builder().percentEncodeUrls(true).build();
    }

    private static Node parse(String source) {
        return Parser.builder().build().parse(source);
    }
}
