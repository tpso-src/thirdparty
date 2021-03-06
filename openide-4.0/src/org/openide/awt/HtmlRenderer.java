/*
 *                 Sun Public License Notice
 *
 * The contents of this file are subject to the Sun Public License
 * Version 1.0 (the "License"). You may not use this file except in
 * compliance with the License. A copy of the License is available at
 * http://www.sun.com/
 *
 * The Original Code is NetBeans. The Initial Developer of the Original
 * Code is Sun Microsystems, Inc. Portions Copyright 1997-2003 Sun
 * Microsystems, Inc. All Rights Reserved.
 */

/*
 * HtmlRenderer.java
 *
 * Created on January 2, 2004, 12:49 AM
 */
package org.openide.awt;

import org.openide.ErrorManager;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import javax.swing.tree.TreeCellRenderer;
import java.awt.*;
import java.awt.font.LineMetrics;
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;
import java.util.*;
import org.openide.util.Utilities;

/**
 * A lightweight html renderer supporting a minimal subset of HTML used for
 * markup purposes only - basic font styles, colors, etc.  Also supports
 * named logical colors specified by a preceding ! character for specifying
 * colors that should be looked up in the current look and feel's UIDefaults
 * (e.g. <code>&lt;font color=&amp;!textText&amp&gt;</code>).
 * <p>
 * Provides a generic cell renderer implementation which can be used for trees, tables,
 * lists, combo boxes, etc.
 * <p>
 * If you only need to paint some HTML quickly, use the static methods for
 * painting - <code>renderString</code>, <code>renderPlainString</code> or
 * <code>renderHtml</code>.  These methods differ as follows:
 * <ul>
 * <li><b>renderString</b> will check the string for opening HTML tags
 * (upper or lower but not mixed case) and call either <code>renderPlainString</code>
 * or <code>renderHtml</code> as appropriate.  Note this method does not tolerate
 * whitespace in opening html tags - it expects exactly 6 characters to make up
 * the opening tag if present.</li>
 * <li><b>renderPlainString</b> simply renders a string to the graphics context,
 * takes the same agruments as <code>renderHtml</code>, but will also honor
 * <code>STYLE_TRUNCATE</code>, so strings can be rendered with trailing
 * elipsis if there is not enough space</li>
 * <li><b>renderHtml</b> renders whatever is passed to it as HTML, regardless
 * of whether it has opening HTML tags or not.  It can be used to render plain
 * strings, but <code>renderPlainString</code> is faster for that. It is useful
 * if you want to render a string you <strong>know</strong> to be compliant
 * HTML markup, but which does not have opening and closing HTML tags (though
 * they are harmless if present). </li>
 * <p>
 * This parser is designed entirely for performance; there are no separate parsing
 * and rendering loops.  In order to acheive its performance, some trade offs
 * are required.
 * <strong>To reiterate: This is not a forgiving HTML parser - the HTML supplied
 * must follow the guidelines documented here!</strong>
 * <p>
 * The following tags are supported, in upper or lower (but not mixed) case:
 *
 * <table>
 * <tr>
 *  <td>&lt;B&gt;</td>
 *  <td>Boldface text</td>
 * </tr>
 * <tr>
 *  <td>&lt;S&gt;</td>
 *  <td>Strikethrough text</td>
 * </tr>
 * <tr>
 *  <td>&lt;U&gt;</td>
 *  <td>Underline text</td>
 * </tr>
 * <tr>
 *  <td>&lt;I&gt;</td>
 *  <td>Italic text</td>
 * </tr>
 * <tr>
 *  <td>&lt;EM&gt;</td>
 *  <td>Emphasized text (same as italic)</td>
 * </tr>
 * <tr>
 *  <td>&lt;STRONG&gt;</td>
 *  <td>Strong text (same as bold)</td>
 * </tr>
 * <tr>
 *  <td>&lt;font&gt;</td>
 *  <td>Font color - font attributes other than color are not supported.  Colors
 *  may be specified as hexidecimal strings, such as #FF0000 or as logical colors
 *  defined in the current look and feel by specifying a ! character as the first
 *  character of the color name.  Logical colors are colors available from the
 *  current look and feel's UIManager.  For example, <code>&lt;font
 *  color=&quot;!Tree.background&quot;&gt;</code> will set the font color to the
 *  result of <code>UIManager.getColor(&quot;Tree.background&quot;)</code>.
 * <strong>Font size tags are not supported.</strong>
 * </td>
 * </tr>
 * </table>
 * The lightweight html renderer supports the following named sgml character
 * entities: <code>quot, lt, amp, lsquo, rsquo, ldquo, rdquo, ndash, mdash, ne,
 * le, ge, copy, reg, trade, nbsp.  </code>.  It also supports numeric entities
 * (e.g. <code>&amp;8822;</code>).
 * <p><b>Why not use the JDK's HTML support?</b> The JDK's HTML support works
 * well for stable components, but suffers from performance problems in the
 * case of cell renderers - each call to set the text (which happens once per
 * cell, per paint) causes a document tree to be created in memory.  For small,
 * markup only strings, this is overkill.   For rendering short strings
 * (for example, in a tree or table cell renderer)
 * with limited HTML, this method is approximately 10x faster than standard
 * Swing HTML rendering.
 *
 * <P><B><U>Specifying logical colors</U></B><BR>
 * Hardcoded text colors are undesirable, as they can be incompatible (even
 * invisible) on some look and feels or themes, depending on the background
 * color.
 * The lightweight HTML renderer supports a non-standard syntax for specifying
 * font colors via a key for a color in the UI defaults for the current look
 * and feel.  This is accomplished by prefixing the key name with a <code>!</code>
 * character.  For example: <code>&lt;font color='!controlShadow'&gt;</code>.
 *
 * <P><B><U>Modes of operation</U></B><BR>
 * This method supports two modes of operation:
 * <OL>
 * <LI><CODE>STYLE_CLIP</CODE> - as much text as will fit in the pixel width passed
 * to the method should be painted, and the text should be cut off at the maximum
 * width or clip rectangle maximum X boundary for the graphics object, whichever is
 * smaller.</LI>
 * <LI><CODE>STYLE_TRUNCATE</CODE> - paint as much text as will fit in the pixel
 * width passed to the method, but paint the last three characters as .'s, in the
 * same manner as a JLabel truncates its text when the available space is too
 * small.</LI>
 * </OL>
 * <P>
 * The paint methods can also be used in non-painting mode to establish the space
 * necessary to paint a string.  This is accomplished by passing the value of the
 * <code>paint</code> argument as false.  The return value will be the required
 * width in pixels
 * to display the text.  Note that in order to retrieve an
 * accurate value, the argument for available width should be passed
 * as <code>Integer.MAX_VALUE</code> or an appropriate maximum size - otherwise
 * the return value will either be the passed maximum width or the required
 * width, whichever is smaller.  Also, the clip shape for the passed graphics
 * object should be null or a value larger than the maximum possible render size,
 * or text size measurement will stop at the clip bounds.  <code>HtmlRenderer.getGraphics()</code>
 * will always return non-null and non-clipped, and is suitable to pass in such a
 * situation.
 * <P>
 *
 * @since 4.30
 * @see org.openide.nodes.Node#getHtmlDisplayName
 * @see org.openide.filesystems.FileSystem.HtmlStatus
 * @author  Tim Boudreau
 */
public final class HtmlRenderer {
    private static HtmlRendererImpl LABEL = null;

    private HtmlRenderer() {
        //do nothing
    }

    /**
     * Returns an instance of Renderer which may be used as a table/tree/list cell renderer.
     * This method must be called on the AWT event thread.  If you <strong>know</strong> you will
     * be passing it legal HTML (legal as documented here), call <code>setHtml(true)</code> on the
     * result of this call <strong>after calling getNNNCellRenderer</code> to provide this hint.
     *
     * @return A cell renderer that can render HTML.
     */
    public static final Renderer createRenderer () {
        return new HtmlRendererImpl();
    }

    /**
     * For HTML rendering jobs outside of trees/lists/tables, returns a JLabel which will paint its text using
     * the lightweight HTML renderer.  The result of this call will implement the <code>Renderer</code> interface.
     * <strong>Do not add the result of this call to the AWT hierarchy.  It is not a general purpose JLabel, and
     * will not behave correctly.  Use the result of this call to paint or to measure text.  Example:
     * <pre>
     * private final JLabel label = HtmlRenderer.createLabel();
     *
     * public void paint (Graphics g) {
     *    //background/whatever painting code here...
     *    label.setText (someHtmlText);
     *    label.paint (g);
     * }
     * </pre>
     *
     *
     * @return A JLabel which can render a subset of html very quickly
     *         if the text should be rendered as plain text even <i>if</i> it contains
     *        HTML markup.
     */
    public static final JLabel createLabel () {
        return new HtmlRendererImpl();
    }

    /** Interface aggregating TableCellRenderer, TreeCellRenderer and ListCellRenderer.
     * Return type of <code>sharedInstance()</code>.
     */
    public interface Renderer extends TableCellRenderer, TreeCellRenderer, ListCellRenderer {
        /** Indicate that the component being rendered has keyboard focus.  NetBeans requires that a different
         * selection color be used depending on whether the view has focus.
         *
         * @param parentFocused Whether or not the focused selection color should be used
         */
        void setParentFocused (boolean parentFocused);

        /**
         * Indicate that the text should be painted centered below the icon.  This is primarily used
         * by org.openide.explorer.view.IconView
         *
         * @param centered Whether or not centered painting should be used.
         */
        void setCentered (boolean centered);

        /**
         * Set a number of pixels the icon and text should be indented.  Used by ChoiceView and ListView to
         * fake tree-style nesting.  This value has no effect if <code>setCentered(true)</code> has been called.
         *
         * @param pixels The number of pixels to indent
         */
        void setIndent (int pixels);

        /**
         * Explicitly tell the renderer it is going to receive HTML markup, or it is not.  If the renderer should
         * check the string for opening HTML tags to determine this, don't call this method.  If you <strong>know</strong>
         * the string will be compliant HTML, it is preferable to call this method with true; if you want to intentionally
         * render HTML markup literally, call this method with false.
         *
         * @param val
         */
        void setHtml (boolean val);

        /**
         * Set the rendering style - this can be JLabel-style truncated-with-elipsis (...) text, or clipped text.
         * The default is STYLE_CLIP.
         *
         * @param style The text style
         */
        void setRenderStyle (int style);

        /** Set the icon to be used for painting
         *
         * @param icon An icon or null
         */
        void setIcon (Icon icon);

        /** Clear any stale data from previous use by other components,
         * clearing the icon, text, disabled state and other customizations, returning the component
         * to its initialized state.  This is done automatically when get*CellRenderer() is called,
         * and to the shared instance when <code>createLabel()</code> is called.<p>
         * Users of the static <code>createLabel()</code> method may want to call this method if they
         * use the returned instance more than once without again calling <code>createLabel()</code>.
         */
        void reset();

        /** Set the text to be displayed.  Use this if the object being rendered's toString() does not
         * return a real user-displayable string, after calling get**CellRenderer().  Typically after calling
         * this one calls setHtml() if the text is known to either be or not be HTML markup.
         *
         * @param txt The text that should be displayed
         */
        void setText (String txt);

        /**
         * Convenience method to set the gap between the icon and text.
         *
         * @param gap an integer number of pixels
         */
        void setIconTextGap (int gap);
    }

    /** Stack object used during HTML rendering to hold previous colors in
     * the case of nested color entries. */
    private static Stack colorStack = new Stack();
    /** Constant used by <code>renderString</code>, <code>renderPlainString</code>
     * <code>renderHTML</code>, and <code>HtmlRenderer.setRenderStyle</code>
     * if painting should simply be cut off at the boundary of the cooordinates passed.     */
    public static final int STYLE_CLIP=0;
    /** Constant used by <code>renderString</code>, <code>renderPlainString</code>
     * <code>renderHTML</code>  and <code>HtmlRenderer.setRenderStyle</code> if
     * painting should produce an ellipsis (...)
     * if the text would overlap the boundary of the coordinates passed.
     */
    public static final int STYLE_TRUNCATE=1;
    /** Constant used by <code>renderString</code>, <code>renderPlainString</code>
     * <code>renderHTML</code> and <code>HtmlRenderer.setRenderStyle</code>
     * if painting should word wrap the text.  In
     * this case, the return value of any of the above methods will be the
     * height, rather than width painted. */
    private static final int STYLE_WORDWRAP=2;
    /** System property to cause exceptions to be thrown when unparsable
     * html is encountered */
    private static final boolean strictHtml = Boolean.getBoolean(
        "netbeans.lwhtml.strict"); //NOI18N
    /** Cache for strings which have produced errors, so we don't post an
     * error message more than once */
    private static Set badStrings=null;
    /** Definitions for a limited subset of sgml character entities */
    private static final Object[] entities = new Object[] {
        new char[] {'g','t'}, new char[] {'l','t'}, //NOI18N
        new char[] {'q','u','o','t'}, new char[] {'a','m','p'}, //NOI18N
        new char[] {'l','s','q','u','o'}, //NOI18N
        new char[] {'r','s','q','u','o'}, //NOI18N
        new char[] {'l','d','q','u','o'}, //NOI18N
        new char[] {'r','d','q','u','o'}, //NOI18N
        new char[] {'n','d','a','s','h'}, //NOI18N
        new char[] {'m','d','a','s','h'}, //NOI18N
        new char[] {'n','e'}, //NOI18N
        new char[] {'l','e'}, //NOI18N
        new char[] {'g','e'}, //NOI18N

        new char[] {'c','o','p','y'}, //NOI18N
        new char[] {'r','e','g'},  //NOI18N
        new char[] {'t','r','a','d','e'}, //NOI18N
        new char[] {'n','b','s','p'} //NOI18N
        //The rest of the SGML entities are left as an excercise for the reader
    }; //NOI18N
    /** Mappings for the array of sgml character entities to characters */
    private static final char[] entitySubstitutions = new char[] {
        '>','<','"','&',8216, 8217, 8220, 8221, 8211, 8212, 8800, 8804, 8805, //NOI18N
        169, 174, 8482, ' '
    };

    /**Render a string to a graphics instance, using the same API as renderHTML().
     * Can render a string using JLabel-style ellipsis (...) in the case that
     * it will not fit in the passed rectangle, if the style parameter is
     * STYLE_CLIP. Returns the width in pixels successfully painted.
     * <strong>This method is not thread-safe and should not be called off
     * the AWT thread.</strong>
     *
     * @see #renderHTML */
    public static double renderPlainString(String s, Graphics g, int x, int y, int w, int h, Font f, Color defaultColor, int style, boolean paint) {
        //per Jarda's request, keep the word wrapping code but don't expose it.
        if (style < 0 || style > 1) {
            throw new IllegalArgumentException(
            "Unknown rendering mode: " + style); //NOI18N
        }
        return _renderPlainString(s, g, x, y, w, h, f, defaultColor, style,
        paint);
    }

    private static double _renderPlainString(String s, Graphics g, int x, int y, int w, int h, Font f, Color foreground, int style, boolean paint) {
        if (f == null) {
            f = UIManager.getFont("controlFont"); //NOI18N
            if (f == null) {
                int fs = 11;
                Object cfs = UIManager.get("customFontSize"); //NOI18N
                if (cfs instanceof Integer) {
                    fs = ((Integer) cfs).intValue();
                }
                f = new Font ("Dialog", Font.PLAIN, fs); //NOI18N
            }
        }
        FontMetrics fm = g.getFontMetrics(f);
        Rectangle2D r = fm.getStringBounds(s, g);
        if (paint) {
            g.setColor(foreground);
            g.setFont(f);
            if ((r.getWidth() <= w) || (style == STYLE_CLIP)) {
                g.drawString(s, x, y);
            } else {
                char[] chars = s.toCharArray();
                if (chars.length == 0) {
                    return 0;
                }
                double chWidth = r.getWidth() / chars.length;
                int estCharsOver = new Double((r.getWidth() - w) / chWidth).intValue();
                if (style == STYLE_TRUNCATE) {
                    int length = chars.length - estCharsOver;
                    if (length <=0) {
                        return 0;
                    }
                    if (paint) {
                        if (length > 3) {
                            Arrays.fill(chars, length-3, length, '.'); //NOI18N
                            g.drawChars(chars, 0, length, x, y);
                        } else {
                            Shape shape = g.getClip();
                            if (s != null) {
                                Area area = new Area(shape);
                                area.intersect (new Area(new Rectangle(x,y,w,h)));
                                g.setClip(area);
                            } else {
                                g.setClip(new Rectangle(x,y,w,h));
                            }
                            g.drawString("...", x,y);
                            g.setClip(shape);
                        }
                    }
                } else {
                    //TODO implement plaintext word wrap if we want to support it at some point
                }
            }
        }
        return r.getWidth();
    }

    /** Render a string to a graphics context, using HTML markup if the string
     * begins with html tags.  Delegates to <code>renderPlainString()</code>
     * or <code>renderHTML()</code> as appropriate.  See the documentation for
     * <code>renderHTML()</code> for details of the subset of HTML that is
     * supported.
     * @param s The string to render
     * @param g A graphics object into which the string should be drawn, or which should be
     * used for calculating the appropriate size
     * @param x The x coordinate to paint at.
     * @param y The y position at which to paint.  Note that this method does not calculate font
     * height/descent - this value should be the baseline for the line of text, not
     * the upper corner of the rectangle to paint in.
     * @param w The maximum width within which to paint.
     * @param h The maximum height within which to paint.
     * @param f The base font to be used for painting or calculating string width/height.
     * @param defaultColor The base color to use if no font color is specified as html tags
     * @param style The wrapping style to use, either <code>STYLE_CLIP</CODE>,
     * or <CODE>STYLE_TRUNCATE</CODE>
     * @param paint True if actual painting should occur.  If false, this method will not actually
     * paint anything, only return a value representing the width/height needed to
     * paint the passed string.
     * @return The width in pixels required
     * to paint the complete string, or the passed parameter <code>w</code> if it is
     * smaller than the required width.
     */
    public static double renderString(String s, Graphics g, int x, int y, int w, int h, Font f, Color defaultColor, int style, boolean paint) {
        switch (style) {
            case STYLE_CLIP :
            case STYLE_TRUNCATE :
                break;
            default :
                throw new IllegalArgumentException ("Unknown rendering mode: " + style); //NOI18N
        }
//        System.err.println ("rps: " + y + " " + s);
        if (s.startsWith("<html") || s.startsWith("<HTML")) { //NOI18N
            return _renderHTML(s, 6, g, x, y, w, h, f, defaultColor, style, paint, null);
        } else {
            return renderPlainString(s, g, x, y, w, h, f, defaultColor, style, paint);
        }
    }

    /** Render a string as HTML using a fast, lightweight renderer supporting a limited
     * subset of HTML.  The following tags are supported, in upper or lower case:
     *
     * <table>
     * <tr>
     *  <td>&lt;B&gt;</td>
     *  <td>Boldface text</td>
     * </tr>
     * <tr>
     *  <td>&lt;S&gt;</td>
     *  <td>Strikethrough text</td>
     * </tr>
     * <tr>
     *  <td>&lt;U&gt;</td>
     *  <td>Underline text</td>
     * </tr>
     * <tr>
     *  <td>&lt;I&gt;</td>
     *  <td>Italic text</td>
     * </tr>
     * <tr>
     *  <td>&lt;EM&gt;</td>
     *  <td>Emphasized text (same as italic)</td>
     * </tr>
     * <tr>
     *  <td>&lt;STRONG&gt;</td>
     *  <td>Strong text (same as bold)</td>
     * </tr>
     * <tr>
     *  <td>&lt;font&gt;</td>
     *  <td>Font color - font attributes other than color are not supported.  Colors
     *  may be specified as hexidecimal strings, such as #FF0000 or as logical colors
     *  defined in the current look and feel by specifying a ! character as the first
     *  character of the color name.  Logical colors are colors available from the
     *  current look and feel's UIManager.  For example, <code>&lt;font
     *  color=&quot;!Tree.background&quot;&gt;</code> will set the font color to the
     *  result of <code>UIManager.getColor(&quot;Tree.background&quot)</code>.
     * <strong>Font size tags are not supported.</strong>
     * </td>
     * </tr>
     * </table>
     * The lightweight html renderer supports the following named sgml character
     * entities: <code>quot, lt, amp, lsquo, rsquo, ldquo, rdquo, ndash, mdash, ne,
     * le, ge, copy, reg, trade.  </code>.  It also supports numeric entities
     * (e.g. <code>&amp;#8822;</code>).
     * <p><b>When to use this method instead of the JDK's HTML support: </b> when
     * rendering short strings (for example, in a tree or table cell renderer)
     * with limited HTML, this method is approximately 10x faster than JDK HTML
     * rendering (it does not build and parse a document tree).
     *
     * <P><B><U>Specifying logical colors</U></B><BR>
     * Hardcoded text colors are undesirable, as they can be incompatible (even
     * invisible) on some look and feels or themes.
     * The lightweight HTML renderer supports a non-standard syntax for specifying
     * font colors via a key for a color in the UI defaults for the current look
     * and feel.  This is accomplished by prefixing the key name with a <code>!</code>
     * character.  For example: <code>&lt;font color='!controlShadow'&gt;</code>.
     *
     * <P><B><U>Modes of operation</U></B><BR>
     * This method supports two modes of operation:
     * <OL>
     * <LI><CODE>STYLE_CLIP</CODE> - as much text as will fit in the pixel width passed
     * to the method should be painted, and the text should be cut off at the maximum
     * width or clip rectangle maximum X boundary for the graphics object, whichever is
     * smaller.</LI>
     * <LI><CODE>STYLE_TRUNCATE</CODE> - paint as much text as will fit in the pixel
     * width passed to the method, but paint the last three characters as .'s, in the
     * same manner as a JLabel truncates its text when the available space is too
     * small.</LI>
     * </OL>
     * <P>
     * This method can also be used in non-painting mode to establish the space
     * necessary to paint a string.  This is accomplished by passing the value of the
     * <code>paint</code> argument as false.  The return value will be the required
     * width in pixels
     * to display the text.  Note that in order to retrieve an
     * accurate value, the argument for available width should be passed
     * as <code>Integer.MAX_VALUE</code> or an appropriate maximum size - otherwise
     * the return value will either be the passed maximum width or the required
     * width, whichever is smaller.  Also, the clip shape for the passed graphics
     * object should be null or a value larger than the maximum possible render size.
     * <P>
     * This method will log a warning if it encounters HTML markup it cannot
     * render.  To aid diagnostics, if NetBeans is run with the argument
     * <code>-J-Dnetbeans.lwhtml.strict=true</code> an exception will be thrown
     * when an attempt is made to render unsupported HTML.</code><p>
     * <p>
     * @param s The string to render
     * @param g A graphics object into which the string should be drawn, or which should be
     * used for calculating the appropriate size
     * @param x The x coordinate to paint at.
     * @param y The y position at which to paint.  Note that this method does not calculate font
     * height/descent - this value should be the baseline for the line of text, not
     * the upper corner of the rectangle to paint in.
     * @param w The maximum width within which to paint.
     * @param h The maximum height within which to paint.
     * @param f The base font to be used for painting or calculating string width/height.
     * @param defaultColor The base color to use if no font color is specified as html tags
     * @param style The wrapping style to use, either <code>STYLE_CLIP</CODE>,
     * or <CODE>STYLE_TRUNCATE</CODE>
     * @param paint True if actual painting should occur.  If false, this method will not actually
     * paint anything, only return a value representing the width/height needed to
     * paint the passed string.
     * @return The width in pixels required
     * to paint the complete string, or the passed parameter <code>w</code> if it is
     * smaller than the required width.
     */
    public static double renderHTML(String s, Graphics g, int x, int y,
        int w, int h, Font f,
        Color defaultColor, int style,
        boolean paint) {

        //per Jarda's request, keep the word wrapping code but don't expose it.
        if (style < 0 || style > 1) {
            throw new IllegalArgumentException(
            "Unknown rendering mode: " + style); //NOI18N
        }
        return _renderHTML(s, 0, g, x, y, w, h, f, defaultColor, style,
            paint, null);
    }

    /** Implementation of HTML rendering */
     static double _renderHTML(String s, int pos, Graphics g, int x,
                               int y, int w, int h, Font f, Color defaultColor, int style,
                               boolean paint, Color background) {
//        System.err.println ("rhs: " + y + " " + s);

        if (f == null) {
            f = UIManager.getFont("controlFont"); //NOI18N
            if (f == null) {
                int fs = 11;
                Object cfs = UIManager.get("customFontSize"); //NOI18N
                if (cfs instanceof Integer) {
                    fs = ((Integer) cfs).intValue();
                }
                f = new Font ("Dialog", Font.PLAIN, fs); //NOI18N
            }
        }

        //Thread safety - avoid allocating memory for the common case
        Stack colorStack = SwingUtilities.isEventDispatchThread() ? 
            HtmlRenderer.colorStack : new Stack();

        g.setColor(defaultColor);
        g.setFont(f);
        char[] chars = s.toCharArray();
        int origX = x;
        boolean done = false;  //flag if rendering completed, either by finishing the string or running out of space
        boolean inTag = false; //flag if the current position is inside a tag, and the tag should be processed rather than rendering
        boolean inClosingTag = false; //flag if the current position is inside a closing tag
        boolean strikethrough = false; //flag if a strikethrough line should be painted
        boolean underline = false; //flag if an underline should be painted
        boolean bold = false; //flag if text is currently bold
        boolean italic = false; //flag if text is currently italic
        boolean truncated = false; //flag if the last possible character has been painted, and the next loop should paint "..." and return
        double widthPainted = 0; //the total width painted, for calculating needed space
        double heightPainted = 0; //the total height painted, for calculating needed space
        boolean lastWasWhitespace = false; //flag to skip additional whitespace if one whitespace char already painted
        double lastHeight=0; //the last line height, for calculating total required height

        double dotWidth = 0;
        //Calculate the width of a . character if we may need to truncate
        if (style == STYLE_TRUNCATE) {
            dotWidth = g.getFontMetrics().charWidth('.'); //NOI18N
        }

        /* How this all works, for anyone maintaining this code (hopefully it will
          never need it):
          1. The string is converted to a char array
          2. Loop over the characters.  Variable pos is the current point.
            2a. See if we're in a tag by or'ing inTag with currChar == '<'
              If WE ARE IN A TAG:
               2a1: is it an opening tag?
                 If YES:
                   - Identify the tag, Configure the Graphics object with
                     the appropriate font, color, etc.  Set pos = the first
                     character after the tag
                 If NO (it's a closing tag)
                   - Identify the tag.  Reconfigure the Graphics object
                     with the state it should be in outside the tag
                     (reset the font if italic, pop a color off the stack, etc.)
            2b. If WE ARE NOT IN A TAG
               - Locate the next < or & character or the end of the string
               - Paint the characters using the Graphics object
               - Check underline and strikethrough tags, and paint line if
                 needed
            See if we're out of space, and do the right thing for the style
            (paint ..., give up or skip to the next line)
         */

        //Clear any junk left behind from a previous rendering loop
        colorStack.clear();

        //Enter the painting loop
        while (!done) {
            if (pos == s.length()) {
                return widthPainted;
            }
            //see if we're in a tag
            try {
                inTag |= chars[pos] == '<';
            } catch (ArrayIndexOutOfBoundsException e) {
                //Should there be any problem, give a meaningful enough
                //message to reproduce the problem
                ArrayIndexOutOfBoundsException aib =
                new ArrayIndexOutOfBoundsException(
                "HTML rendering failed at position " + pos + " in String \"" //NOI18N
                + s + "\".  Please report this at http://www.netbeans.org"); //NOI18N

                if (strictHtml) {
                    throw aib;
                } else {
                    ErrorManager.getDefault().notify (ErrorManager.WARNING, aib);
                    return renderPlainString(s, g, x, y, w, h, f, defaultColor, style, paint);
                }
            }
            inClosingTag = inTag && (pos+1 < chars.length) && chars[pos+1]  == '/'; //NOI18N

            if (truncated) {
                //Then we've almost run out of space, time to print ... and quit
                g.setColor(defaultColor);
                g.setFont(f);
                if (paint) {
                    g.drawString("...", x, y); //NOI18N
                }
                done = true;
            } else if (inTag) {
                //If we're in a tag, don't paint, process it
                pos++;
                int tagEnd = pos;
                while (!done && (chars[tagEnd] != '>')) {
                    done = tagEnd == chars.length -1;
                    tagEnd++;
                }

                if (inClosingTag) {
                    //Handle closing tags by resetting the Graphics object (font, etc.)
                    pos++;
                    switch (chars[pos]) {
                        case 'P' : //NOI18N
                        case 'p' : //NOI18N
                        case 'H' : //NOI18N
                        case 'h' : break; //ignore html opening/closing tags
                        case 'B' : //NOI18N
                        case 'b' : //NOI18N
                            if (chars[pos+1] == 'r' || chars[pos+1] == 'R') {
                                break;
                            }
                            if (!bold) {
                                throwBadHTML("Closing bold tag w/o " + //NOI18N
                                "opening bold tag", pos, chars); //NOI18N
                            }
                            if (italic) {
                                g.setFont(deriveFont(f, Font.ITALIC));
                            } else {
                                g.setFont(deriveFont(f, Font.PLAIN));
                            }
                            bold = false;
                            break;
                        case 'E' : //NOI18N
                        case 'e' : //em tag
                        case 'I' : //NOI18N
                        case 'i' : //NOI18N
                            if (bold) {
                                g.setFont(deriveFont(f, Font.BOLD));
                            } else {
                                g.setFont(deriveFont(f, Font.PLAIN));
                            }
                            if (!italic) {
                                throwBadHTML("Closing italics tag w/o" //NOI18N
                                + "opening italics tag", pos, chars); //NOI18N
                            }
                            italic = false;
                            break;
                        case 'S' : //NOI18N
                        case 's' : //NOI18N
                            switch (chars[pos+1]) {
                                case 'T' : //NOI18N
                                case 't' : if (italic) { //NOI18N
                                    g.setFont(deriveFont( f,
                                    Font.ITALIC));
                                } else {
                                    g.setFont(deriveFont( f,
                                    Font.PLAIN));
                                }
                                bold = false;
                                break;
                                case '>' : //NOI18N
                                    strikethrough = false;
                                    break;
                            }
                            break;
                        case 'U' : //NOI18N
                        case 'u' : underline = false; //NOI18N
                        break;
                        case 'F' : //NOI18N
                        case 'f' : //NOI18N
                            if (colorStack.isEmpty()) {
                                g.setColor(defaultColor);
                            } else {
                                g.setColor((Color) colorStack.pop());
                            }
                            break;
                        default  :
                            throwBadHTML(
                            "Malformed or unsupported HTML", //NOI18N
                            pos,  chars);
                    }
                } else {
                    //Okay, we're in an opening tag.  See which one and configure the Graphics object
                    switch (chars[pos]) {
                        case 'B' : //NOI18N
                        case 'b' : //NOI18N
                            switch (chars[pos+1]) {
                                case 'R' :  //NOI18N
                                case 'r' : //NOI18N
                                    if (style == STYLE_WORDWRAP) {
                                        x = origX;
                                        int lineHeight = g.getFontMetrics().getHeight();
                                        y += lineHeight;
                                        heightPainted += lineHeight;
                                        widthPainted = 0;
                                    }
                                    break;
                                case '>' :
                                    bold = true;
                                    if (italic) {
                                        g.setFont(deriveFont(f, Font.BOLD | Font.ITALIC));
                                    } else {
                                        g.setFont(deriveFont(f, Font.BOLD));
                                    }
                                    break;
                            }
                            break;
                        case 'e' : //NOI18N  //em tag
                        case 'E' : //NOI18N
                        case 'I' : //NOI18N
                        case 'i' : //NOI18N
                            italic = true;
                            if (bold) {
                                g.setFont(deriveFont(f, Font.ITALIC | Font.BOLD));
                            } else {
                                g.setFont(deriveFont(f, Font.ITALIC));
                            }
                            break;
                        case 'S' : //NOI18N
                        case 's' : //NOI18N
                            switch (chars[pos+1]) {
                                case '>' :
                                    strikethrough = true;
                                    break;
                                case 'T' :
                                case 't' :
                                    bold = true;
                                    if (italic) {
                                        g.setFont(deriveFont(f, Font.BOLD | Font.ITALIC));
                                    } else {
                                        g.setFont(deriveFont(f, Font.BOLD));
                                    }
                                    break;
                            }
                            break;
                        case 'U' : //NOI18N
                        case 'u' : //NOI18N
                            underline = true;
                            break;
                        case 'f' : //NOI18N
                        case 'F' : //NOI18N
                            Color c = findColor(chars, pos, tagEnd);
                            colorStack.push(g.getColor());
                            if (background != null) {
                                c = HtmlLabelUI.ensureContrastingColor(c, background);
                            }
                            g.setColor(c);
                            break;
                        case 'P' : //NOI18N
                        case 'p' : //NOI18N
                            if (style == STYLE_WORDWRAP) {
                                x = origX;
                                int lineHeight=g.getFontMetrics().getHeight();
                                y +=  lineHeight + (lineHeight / 2);
                                heightPainted = y + lineHeight;
                                widthPainted = 0;
                            }
                            break;
                        case 'H' :
                        case 'h' : //Just an opening HTML tag
                            if (pos == 1) {
                                break;
                            }
                        default  : throwBadHTML(
                            "Malformed or unsupported HTML", pos, chars); //NOI18N
                    }
                }

                pos = tagEnd + (done ? 0 : 1);
                inTag = false;
            } else {
                //Okay, we're not in a tag, we need to paint

                if (lastWasWhitespace) {
                    //Skip multiple whitespace characters
                    while (pos < s.length() && Character.isWhitespace(chars[pos])) {
                        pos++;
                    }
                    //Check strings terminating with multiple whitespace -
                    //otherwise could get an AIOOBE here
                    if (pos == chars.length - 1) {
                        return style != STYLE_WORDWRAP ? widthPainted : heightPainted;
                    }
                }

                //Flag to indicate if an ampersand entity was processed,
                //so the resulting & doesn't get treated as the beginning of
                //another entity (and loop endlessly)
                boolean isAmp=false;
                //Flag to indicate the next found < character really should
                //be painted (it came from an entity), it is not the beginning
                //of a tag
                boolean nextLtIsEntity=false;
                int nextTag = chars.length-1;
                if ((chars[pos] == '&')) { //NOI18N
                    boolean inEntity=pos != chars.length-1;
                    if (inEntity) {
                        int newPos = substEntity(chars, pos+1);
                        inEntity = newPos != -1;
                        if (inEntity) {
                            pos = newPos;
                            isAmp = chars[pos] == '&'; //NOI18N
                            //flag it so the next iteration won't think the <
                            //starts a tag
                            nextLtIsEntity = chars[pos] == '<';
                        } else {
                            nextLtIsEntity = false;
                            isAmp = true;
                        }
                    }
                } else {
                    nextLtIsEntity=false;
                }

                for (int i=pos; i < chars.length; i++) {
                    if (((chars[i] == '<') && (!nextLtIsEntity)) || ((chars[i] == '&') && !isAmp)) { //NOI18N
                        nextTag = i-1;
                        break;
                    }
                    //Reset these flags so we don't skip all & or < chars for the rest of the string
                    isAmp = false;
                    nextLtIsEntity=false;
                }


                FontMetrics fm = g.getFontMetrics();

                //Get the bounds of the substring we'll paint
                Rectangle2D r = fm.getStringBounds(chars, pos, nextTag + 1, g);
                //Store the height, so we can add it if we're in word wrap mode,
                //to return the height painted
                lastHeight = r.getHeight();
                //Work out the length of this tag
                int length = (nextTag + 1) - pos;

                //Flag to be set to true if we run out of space
                boolean goToNextRow = false;

                //Flag that the current line is longer than the available width,
                //and should be wrapped without finding a word boundary
                boolean brutalWrap = false;
                //Work out the per-character avg width of the string, for estimating
                //when we'll be out of space and should start the ... in truncate
                //mode
                double chWidth;

                if (style == STYLE_TRUNCATE) {
                    //if we're truncating, use the width of one dot from an
                    //ellipsis to get an accurate result for truncation
                    chWidth = dotWidth;
                } else {
                    //calculate an average character width
                    chWidth= r.getWidth() / (nextTag - pos);
                    //can return this sometimes, so handle it
                    if (chWidth == Double.POSITIVE_INFINITY || chWidth == Double.NEGATIVE_INFINITY) {
                        chWidth = fm.getMaxAdvance();
                    }
                }


                if ((style != STYLE_CLIP) &&
                    ((style == STYLE_TRUNCATE &&
                    (widthPainted + r.getWidth() > w - (chWidth * 3)))) ||
                    (style == STYLE_WORDWRAP &&
                    (widthPainted + r.getWidth() > w))) {

                    if (chWidth > 3) {
                        double pixelsOff = (widthPainted + (
                            r.getWidth() + 5)
                            ) - w;

                        double estCharsOver = pixelsOff / chWidth;

                        if (style == STYLE_TRUNCATE) {
                            int charsToPaint = Math.round(Math.round(Math.ceil((w - widthPainted) / chWidth)));
/*                            System.err.println("estCharsOver = " + estCharsOver);
                            System.err.println("Chars to paint " + charsToPaint + " chwidth = " + chWidth + " widthPainted " + widthPainted);
                            System.err.println("Width painted + width of tag: " + (widthPainted + r.getWidth()) + " available: " + w);
 */

                            int startPeriodsPos = pos + charsToPaint -3;
                            if (startPeriodsPos >= chars.length) {
                                startPeriodsPos = chars.length - 4;
                            }
                            length = (startPeriodsPos - pos);
                            if (length < 0) length = 0;
                            r = fm.getStringBounds(chars, pos, pos+length, g);
//                            System.err.println("Truncated set to true at " + pos + " (" + chars[pos] + ")");
                            truncated = true;
                        } else {
                            //Word wrap mode
                            goToNextRow = true;
                            int lastChar = new Double(nextTag -
                                estCharsOver).intValue();
                            //Unlike Swing's word wrap, which does not wrap on tag boundaries correctly, if we're out of space,
                            //we're out of space
                            brutalWrap = x == 0;
                            for (int i = lastChar; i > pos; i--) {
                                lastChar--;
                                if (Character.isWhitespace(chars[i])) {
                                    length = (lastChar - pos) + 1;
                                    brutalWrap = false;
                                    break;
                                }
                            }
                            if ((lastChar <= pos) && (length > estCharsOver)
                            && !brutalWrap) {
                                x = origX;
                                y += r.getHeight();
                                heightPainted += r.getHeight();
                                boolean boundsChanged = false;
                                while (!done && Character.isWhitespace(
                                chars[pos]) && (pos < nextTag)) {
                                    pos++;
                                    boundsChanged = true;
                                    done = pos == chars.length -1;
                                }
                                if (pos == nextTag) {
                                    lastWasWhitespace = true;
                                }
                                if (boundsChanged) {
                                    //recalculate the width we will add
                                    r = fm.getStringBounds(chars, pos,
                                    nextTag + 1, g);
                                }
                                goToNextRow = false;
                                widthPainted = 0;
                                if (chars[pos - 1 + length] == '<') {
                                    length --;
                                }
                            } else if (brutalWrap) {
                                //wrap without checking word boundaries
                                length = (new Double(
                                    (w - widthPainted) / chWidth)).intValue();
                                if (pos + length > nextTag) {
                                    length = (nextTag - pos);
                                }
                                goToNextRow = true;
                            }
                        }
                    }
                }
                if (!done) {
                    if (paint) {
                        g.drawChars(chars, pos, length, x, y);
                    }

                    if (strikethrough || underline) {
                        LineMetrics lm = fm.getLineMetrics(chars, pos,
                            length - 1, g);
                        int lineWidth = new Double(x + r.getWidth()).intValue();
                        if (paint) {
                            if (strikethrough) {
                                int stPos = Math.round(lm.getStrikethroughOffset()) +
                                        g.getFont().getBaselineFor(chars[pos])
                                        + 1;
                                //PENDING - worth supporting with g.setStroke()? A one pixel line is most likely
                                //good enough

                                //int stThick = Math.round (lm.getStrikethroughThickness());
                                g.drawLine(x, y + stPos, lineWidth, y + stPos);
                            }
                            if (underline) {
                                int stPos = Math.round(
                                    lm.getUnderlineOffset()) +
                                    g.getFont().getBaselineFor(chars[pos])
                                    + 1;
                                //PENDING - worth supporting with g.setStroke()? A one pixel line is most likely
                                //good enough

                                //int stThick = new Float (lm.getUnderlineThickness()).intValue();
                                g.drawLine(x, y + stPos, lineWidth, y + stPos);
                            }
                        }
                    }
                    if (goToNextRow) {
                        //if we're in word wrap mode and need to go to the next
                        //line, reconfigure the x and y coordinates
                        x = origX;
                        y += r.getHeight();
                        heightPainted += r.getHeight();
                        widthPainted = 0;
                        pos += (length);
                        //skip any leading whitespace
                        while ((pos < chars.length) &&
                        (Character.isWhitespace(chars[pos])) &&
                            (chars[pos] != '<')) {
                                pos++;
                            }
                        lastWasWhitespace = true;
                        done |= pos >= chars.length;
                    } else {
                        x += r.getWidth();
                        widthPainted += r.getWidth();
                        lastWasWhitespace = Character.isWhitespace(
                            chars[nextTag]);
                        pos = nextTag + 1;
                    }
                    done |= nextTag == chars.length;
                }
            }
        }
        if (style != STYLE_WORDWRAP) {
            return widthPainted;
        } else {
            return heightPainted + lastHeight;
        }
    }

    /** Parse a font color tag and return an appopriate java.awt.Color instance */
    private static Color findColor(final char[] ch, final int pos,
    final int tagEnd) {
        int colorPos = pos;
        boolean useUIManager = false;
        for (int i=pos; i < tagEnd; i ++) {
            if (ch[i] == 'c') {
                colorPos = i + 6;
                if (ch[colorPos] == '\'' || ch[colorPos] == '"') {
                    colorPos++;
                }
                //skip the leading # character
                if (ch[colorPos] == '#') {
                    colorPos++;
                } else if (ch[colorPos] == '!') {
                    useUIManager = true;
                    colorPos++;
                }
                break;
            }
        }
        if (colorPos == pos) {
            String out = "Could not find color identifier in font declaration"; //NOI18N
            throwBadHTML(out, pos, ch);
        }
        //Okay, we're now on the first character of the hex color definition
        String s;
        if (useUIManager) {
            int end = ch.length-1;
            for (int i=colorPos; i < ch.length; i++) {
                if (ch[i] == '"' || ch[i] == '\'') { //NOI18N
                    end = i;
                    break;
                }
            }
            s = new String(ch, colorPos, end-colorPos);
        } else {
            s = new String(ch, colorPos, 6);
        }
        Color result=null;
        if (useUIManager) {
            result = UIManager.getColor(s);
            //Not all look and feels will provide standard colors; handle it gracefully
            if (result == null) {
                throwBadHTML(
                "Could not resolve logical font declared in HTML: " + s, //NOI18N
                pos, ch);
                result = UIManager.getColor("textText"); //NOI18N
                //Avoid NPE in headless situation?
                if (result == null) {
                    result = Color.BLACK;
                }
            }
        } else {
            try {
                int rgb = Integer.parseInt(s, 16);
                result = new Color(rgb);
            } catch (NumberFormatException nfe) {
                throwBadHTML(
                "Illegal hexadecimal color text: " + s + //NOI18N
                " in HTML string", colorPos, ch);   //NOI18N
            }
        }
        if (result == null) {
            throwBadHTML("Unresolvable html color: " + s //NOI18N
            + " in HTML string \n  ", pos,  ch);  //NOI18N
        }
        return result;
    }
    
    private static final boolean needCacheDerivedFonts =
        (Utilities.getOperatingSystem() & Utilities.OS_MAC) != 0;
    
    private static Map fontCache = null;
    
    /**
     * Workaround for Apple bug 3644261 - after using form editor, all boldface
     * fonts start showing up with incorrect metrics, such that all boldface
     * fonts in the entire IDE are displayed 12px below where they should be.
     * Embarrassing and awful.
     */
    private static final Font deriveFont (Font f, int style) {
        Font result;
        if (needCacheDerivedFonts) {
            if (fontCache == null) {
                fontCache = new HashMap();
            }
            result = getFromFontCache (f, style);
            if (result == null) {
                result = f.deriveFont(style);
                putToFontCache (result, f, style);
            }
        } else {
            result = f.deriveFont(style);
        }
        return result;
    }
    
    //Apple bug 3644261 workaround
    private static Font getFromFontCache(Font f, int style) {
        Font result = null;
        Font[] fonts = (Font[]) fontCache.get(f);
        if (fonts != null) {
            if (fonts != null) {
                switch (style) {
                    case Font.PLAIN :
                        result = fonts[0];
                        break;
                    case Font.BOLD :
                        result = fonts[1];
                        break;
                    case Font.ITALIC :
                        result = fonts[2];
                        break;
                    case (Font.BOLD | Font.ITALIC) :
                        result = fonts[3];
                        break;
                    default :
                        throw new IllegalArgumentException (Integer.toString(style));
                }
            }
        }
        return result;
    }
    
    //Apple bug 3644261 workaround
    private static void putToFontCache(Font f, Font from, int style) {
        Font[] fonts = (Font[]) fontCache.get(from);
        boolean replace = fonts == null;
        if (replace) {
            fonts = new Font[4];
        }
        switch (style) {
            case Font.PLAIN :
                fonts[0] = f;
                break;
            case Font.BOLD :
                fonts[1] = f;
                break;
            case Font.ITALIC :
                fonts[2] = f;
                break;
            case (Font.BOLD | Font.ITALIC) :
                fonts[3] = f;
                break;
            default :
                throw new IllegalArgumentException (Integer.toString(style));
        }
        if (replace) {
            fontCache.put (from, fonts);
        }
    }

    /** Find an entity at the passed character position in the passed array.
     * If an entity is found, the trailing ; character will be substituted
     * with the resulting character, and the position of that character
     * in the array will be returned as the new position to render from,
     * causing the renderer to skip the intervening characters */
    private static final int substEntity(char[] ch, int pos) {
        //There are no 1 character entities, abort
        if (pos >= ch.length-2) {
            return -1;
        }
        //if it's numeric, parse out the number
        if (ch[pos] == '#') { //NOI18N
            return substNumericEntity(ch, pos+1);
        }
        //Okay, we've potentially got a named character entity. Try to find it.
        boolean match;
        for (int i=0; i < entities.length; i++) {
            char[] c = (char[]) entities[i];
            match = true;
            if (c.length < ch.length-pos) {
                for (int j=0; j < c.length; j++) {
                    match &= c[j] == ch[j+pos];
                }
            } else {
                match = false;
            }
            if (match) {
                //if it's a match, we still need the trailing ;
                if (ch[pos+c.length] == ';') { //NOI18N
                    //substitute the character referenced by the entity
                    ch[pos+c.length] = entitySubstitutions[i];
                    return pos+c.length;
                }
            }
        }
        return -1;
    }

    /** Finds a character defined as a numeric entity (e.g. &amp;#8222;)
     * and replaces the trailing ; with the referenced character, returning
     * the position of it so the renderer can continue from there.
     */
    private static final int substNumericEntity(char[] ch, int pos) {
        for (int i=pos; i < ch.length; i++) {
            if (ch[i] == ';') {
                try {
                    ch[i] = (char) Integer.parseInt(
                    new String(ch, pos, i - pos));
                    return i;
                } catch (NumberFormatException nfe) {
                    throwBadHTML("Unparsable numeric entity: " + //NOI18N
                        new String(ch, pos, i - pos), pos, ch); //NOI18N
                }
            }
        }
        return -1;
    }

    /** Throw an exception for unsupported or bad html, indicating where the problem is
     * in the message  */
    private static void throwBadHTML(String msg, int pos, char[] chars) {
        char[] chh = new char[pos];
        Arrays.fill(chh, ' '); //NOI18N
        chh[pos-1] = '^'; //NOI18N
        String out = msg + "\n  " + new String(chars) + "\n  "
            + new String(chh) + "\n Full HTML string:" + new String(chars); //NOI18N
        if (!strictHtml) {
            if (ErrorManager.getDefault().isLoggable(ErrorManager.WARNING)) {
                if (badStrings == null) {
                    badStrings = new HashSet();
                }
                if (!badStrings.contains(msg)) {
                    //ErrorManager bug, issue 38372 - log messages containing
                    //newlines are truncated - so for now we iterate the
                    //string we've just constructed
                    StringTokenizer tk = new StringTokenizer(out, "\n", false);
                    while (tk.hasMoreTokens()) {
                        ErrorManager.getDefault().log(ErrorManager.WARNING,
                            tk.nextToken());
                    }
                    badStrings.add(msg.intern());
                }
            }
        } else {
            throw new IllegalArgumentException(out);
        }
    }
}
