/*
 *                 Sun Public License Notice
 * 
 * The contents of this file are subject to the Sun Public License
 * Version 1.0 (the "License"). You may not use this file except in
 * compliance with the License. A copy of the License is available at
 * http://www.sun.com/
 * 
 * The Original Code is NetBeans. The Initial Developer of the Original
 * Code is Sun Microsystems, Inc. Portions Copyright 1997-2004 Sun
 * Microsystems, Inc. All Rights Reserved.
 */

package org.openide.awt;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.net.URL;
import java.util.Iterator;
import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToolBar;
import javax.swing.ListModel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import org.openide.ErrorManager;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.RequestProcessor;

/**
* Object that provides viewer for HTML pages.
* <p>If all you want to do is to show some URL in the IDE's normal way, this
* is overkill. Just use {@link HtmlBrowser.URLDisplayer#showURL} instead. Using <code>HtmlBrowser</code>
* is appropriate mainly if you want to embed a web browser in some other GUI component
* (if the user has selected an external browser, this will fall back to a simple Swing
* renderer). Similarly <code>Impl</code> (coming from a <code>Factory</code>) is the lower-level
* renderer itself (sans toolbar).
* <p>Summary: for client use, try <code>URLDisplayer.showURL</code>, or for more control
* or where embedding is needed, create an <code>HtmlBrowser</code>. For provider use,
* create a <code>Factory</code> and register an instance of it to lookup.
*/
public class HtmlBrowser extends JPanel {

    // static ....................................................................

    /** generated Serialized Version UID */
    private static final long serialVersionUID = 2912844785502987960L;

    /** Preferred width of the browser */
    public static final int           DEFAULT_WIDTH = 400;
    /** Preferred height of the browser */
    public static final int           DEFAULT_HEIGHT = 600;

    /** current implementation of html browser */
    private static Factory            browserFactory;
    /** home page URL */
    private static String             homePage = null;

    /** Icons for buttons. */
    private static Icon               iBack;
    private static Icon               iForward;
    private static Icon               iHome;
    private static Icon               iReload;
    private static Icon               iStop;
    private static Icon               iHistory;


    /** Sets the home page.
    * @param u the home page
    */
    public static void setHomePage (String u) {
        homePage = u;
    }

    /** Getter for the home page
    * @return the home page
    */
    public static String getHomePage () {
	if (homePage == null) {
	    return NbBundle.getMessage (HtmlBrowser.class, "PROP_HomePage");
	}
        return homePage;
    }

    /**
    * Sets a new implementation of browser visual component
    * for all HtmlBrowers.
    * @deprecated Use Lookup instead to register factories
    */
    public static void setFactory (Factory brFactory) {
        browserFactory = brFactory;
    }


    // variables .................................................................

    /** currently used implementation of browser */
    final Impl                        browserImpl;
    /** true = do not listen on changes of URL on cbLocation */
    private boolean                     everythinkIListenInCheckBoxIsUnimportant = false;
    /** toolbar visible property */
    private boolean                     toolbarVisible = false;
    /** status line visible property */
    private boolean                     statusLineVisible = false;
    /**  Listens on changes in HtmlBrowser.Impl and HtmlBrowser visual components.
    */
    private BrowserListener             browserListener;

    // visual components .........................................................

    private JButton                     bBack,
    bForward,
    bHome,
    bReload,
    bStop,
    bHistory;
    /** URL chooser */
    private JComboBox                   cbLocation;
    private JLabel                      cbLabel;
    private JLabel                      lStatusLine;
    final Component                   browserComponent;
    private JPanel                      head;


    // init ......................................................................

    /**
    * Creates new html browser with toolbar and status line.
    */
    public HtmlBrowser () {
        this (true, true);
    }

    /**
    * Creates new html browser.
    */
    public HtmlBrowser (boolean toolbar, boolean statusLine) {
        this (null, toolbar, statusLine);
    }
    
    /**
    * Creates new html browser.
     *
     * @param fact Factory that is used for creation. Default factory is used if null is passed
     * @param toolbar visibility of toolbar
     * @param statusLine visibility of statusLine
    */
    public HtmlBrowser (Factory fact, boolean toolbar, boolean statusLine) {
        init ();

        Impl impl = null;
        Component comp = null;
        try {
            if (fact == null) {
                Impl[] arr = new Impl[1];
                comp = findComponent (arr);
                impl = arr[0];
            }
            else {
                try {
                    impl = fact.createHtmlBrowserImpl ();
                    comp = impl.getComponent();
                }
                catch (UnsupportedOperationException ex) {
                    ErrorManager.getDefault().notify(ex);
                    impl = new SwingBrowserImpl ();
                    comp = impl.getComponent ();
                }
            }
        } catch (RuntimeException e) {
	    ErrorManager em = ErrorManager.getDefault();
            // browser was uninstlled ?
	    em.annotate(e, NbBundle.getMessage (HtmlBrowser.class, "EXC_Module"));
	    em.notify(e);
        }
        
        browserImpl = impl;
        browserComponent = comp;

        setLayout (new BorderLayout (0, 2));

        add ((browserComponent != null)? new JScrollPane (browserComponent): new JScrollPane (), "Center"); // NOI18N

        browserListener = new BrowserListener ();
        if (toolbar) initToolbar ();
        if (statusLine) initStatusLine ();

        browserImpl.addPropertyChangeListener (browserListener);

        getAccessibleContext().setAccessibleName(NbBundle.getMessage (HtmlBrowser.class, "ACS_HtmlBrowser"));
        getAccessibleContext().setAccessibleDescription(NbBundle.getMessage (HtmlBrowser.class, "ACSD_HtmlBrowser"));
    }
    
    /** Find Impl of HtmlBrowser. Searches for registered factories in lookup folder.
     *  Tries to create Impl and check if it provides displayable component. 
     *  Both Component and used Impl are returned to avoid resource consuming of new 
     *  Component/Impl.
     *  </P>
     *  <P>
     *  If no browser is found then it tries to use registered factory (now deprecated method
     *  of setting browser) or it uses browser based on swing editor in the worst case.
     *
     *  @param handle used browser implementation is in first element when method 
     *                is finished
     *  @return Component for content displaying 
     */
    private static Component findComponent (Impl[] handle) {
        Lookup.Result r = Lookup.getDefault ().lookup (new Lookup.Template (Factory.class));
        Iterator it = r.allInstances ().iterator ();
        while (it.hasNext ()) {
            Factory f = (Factory)it.next ();
            try {
                Impl impl = f.createHtmlBrowserImpl ();
                Component c = (impl != null)? impl.getComponent (): null;
                if (c != null) {
                    handle[0] = impl;
                    return c;
                }
            }
            catch (UnsupportedOperationException ex) {
                // do nothing: thrown if browser doesn't work on given platform
            }
        }
        
        // 1st fallback to our deprecated method
        Factory f = browserFactory;
        if (f != null) {
            try {
                handle[0] = f.createHtmlBrowserImpl ();
                return handle[0].getComponent ();
            }
            catch (UnsupportedOperationException ex) {
                // do nothing: thrown if browser doesn't work on given platform
            }
        }
        
        // last fallback is to swing
        handle[0] = new SwingBrowserImpl ();
        return handle[0].getComponent ();
    }

    /**
    * Default initializations.
    */
    private static void init () {
        if (iBack != null) return;

        iBack = new ImageIcon (HtmlBrowser.class.getResource (
                                   "/org/openide/resources/html/back.gif" // NOI18N
                               ));
        iForward = new ImageIcon (HtmlBrowser.class.getResource (
                                      "/org/openide/resources/html/forward.gif" // NOI18N
                                  ));
        iHome = new ImageIcon (HtmlBrowser.class.getResource (
                                   "/org/openide/resources/html/home.gif" // NOI18N
                               ));
        iReload = new ImageIcon (HtmlBrowser.class.getResource (
                                     "/org/openide/resources/html/refresh.gif" // NOI18N
                                 ));
        iStop = new ImageIcon (HtmlBrowser.class.getResource (
                                   "/org/openide/resources/html/stop.gif" // NOI18N
                               ));
        iHistory = new ImageIcon (HtmlBrowser.class.getResource (
                                      "/org/openide/resources/html/history.gif" // NOI18N
                                  ));
    }

    /**
    * Default initialization of toolbar.
    */
    private void initToolbar () {
        toolbarVisible = true;

        // create visual compoments .............................
        head = new JPanel ();
        head.setLayout (new BorderLayout (11, 0));

        JPanel p = new JPanel (new GridBagLayout());
        p.add (bBack = new ToolbarButton (iBack));
        bBack.setToolTipText (NbBundle.getMessage (HtmlBrowser.class, "CTL_Back"));
        bBack.setMnemonic(NbBundle.getMessage (HtmlBrowser.class, "CTL_Back_Mnemonic").charAt(0));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(0, 0, 0, 5);
        p.add (bForward = new ToolbarButton (iForward), gbc);
        bForward.setToolTipText (NbBundle.getMessage (HtmlBrowser.class, "CTL_Forward"));
        bForward.setMnemonic(NbBundle.getMessage (HtmlBrowser.class, "CTL_Forward_Mnemonic").charAt(0));
        p.add (bStop = new ToolbarButton (iStop));
        bStop.setToolTipText (NbBundle.getMessage (HtmlBrowser.class, "CTL_Stop"));
        bStop.setMnemonic(NbBundle.getMessage (HtmlBrowser.class, "CTL_Stop_Mnemonic").charAt(0));
        gbc = new GridBagConstraints();
        gbc.insets = new Insets(0, 0, 0, 5);
        p.add (bReload = new ToolbarButton (iReload), gbc);
        bReload.setToolTipText (NbBundle.getMessage (HtmlBrowser.class, "CTL_Reload"));
        bReload.setMnemonic(NbBundle.getMessage (HtmlBrowser.class, "CTL_Reload_Mnemonic").charAt(0));
        p.add (bHome = new ToolbarButton (iHome));
        bHome.setToolTipText (NbBundle.getMessage (HtmlBrowser.class, "CTL_Home"));
        bHome.setMnemonic(NbBundle.getMessage (HtmlBrowser.class, "CTL_Home_Mnemonic").charAt(0));
        gbc = new GridBagConstraints();
        gbc.insets = new Insets(0, 0, 0, 5);
        p.add (bHistory = new ToolbarButton (iHistory), gbc);
        bHistory.setToolTipText (NbBundle.getMessage (HtmlBrowser.class, "CTL_History"));
        bHistory.setMnemonic(NbBundle.getMessage (HtmlBrowser.class, "CTL_History_Mnemonic").charAt(0));
        if (browserImpl != null) {
            bBack.setEnabled (browserImpl.isBackward ());
            bForward.setEnabled (browserImpl.isForward ());
            bHistory.setEnabled(browserImpl.isHistory());
        }
        JToolBar.Separator ts = new JToolBar.Separator ();
        gbc = new GridBagConstraints();
        gbc.insets = new Insets(0, 0, 0, 5);
        p.add (ts, gbc);
        ts.updateUI ();
        p.add ( cbLabel = new JLabel (NbBundle.getMessage (HtmlBrowser.class, "CTL_Location")));
        cbLabel.setDisplayedMnemonic(NbBundle.getMessage (HtmlBrowser.class, "CTL_Location_Mnemonic").charAt(0));
        head.add ("West", p); // NOI18N

        head.add ("Center", cbLocation = new JComboBox ()); // NOI18N
        cbLocation.setEditable (true);
        cbLabel.setLabelFor(cbLocation);
        add (head, "North"); // NOI18N

        // add listeners ..................... .............................
        cbLocation.addActionListener (browserListener);
        bHistory.addActionListener (browserListener);
        bBack.addActionListener (browserListener);
        bForward.addActionListener (browserListener);
        bReload.addActionListener (browserListener);
        bHome.addActionListener (browserListener);
        bStop.addActionListener (browserListener);

        bHistory.getAccessibleContext().setAccessibleName(bHistory.getToolTipText());
        bBack.getAccessibleContext().setAccessibleName(bBack.getToolTipText());
        bForward.getAccessibleContext().setAccessibleName(bForward.getToolTipText());
        bReload.getAccessibleContext().setAccessibleName(bReload.getToolTipText());
        bHome.getAccessibleContext().setAccessibleName(bHome.getToolTipText());
        bStop.getAccessibleContext().setAccessibleName(bStop.getToolTipText());
        cbLocation.getAccessibleContext().setAccessibleDescription(NbBundle.getMessage (HtmlBrowser.class, "ACSD_HtmlBrowser_Location"));
    }

    /**
    * Default initialization of toolbar.
    */
    private void destroyToolbar () {
        remove (head);
        head = null;
        toolbarVisible = false;
    }

    /**
    * Default initialization of status line.
    */
    private void initStatusLine () {
        statusLineVisible = true;
        add (
            lStatusLine = new JLabel (NbBundle.getMessage (HtmlBrowser.class, "CTL_Loading")),
            "South" // NOI18N
        );
        lStatusLine.setLabelFor(this);
    }

    /**
    * Destroyes status line.
    */
    private  void destroyStatusLine () {
        remove (lStatusLine);
        lStatusLine = null;
        statusLineVisible = false;
    }


    // public methods ............................................................

    /**
    * Sets new URL.
    *
    * @param str URL to show in this browser.
    */
    public void setURL (String str) {
        URL URL;
        try {
            URL = new java.net.URL (str);
        } catch (java.net.MalformedURLException ee) {
            try {
                URL = new java.net.URL ("http://" + str); // NOI18N
            } catch (java.net.MalformedURLException e) {
                if (browserImpl instanceof SwingBrowserImpl) {
                    ((SwingBrowserImpl) browserImpl).setStatusText(
                        NbBundle.getMessage(SwingBrowserImpl.class,
                        "FMT_InvalidURL", new Object[] {str}));
                } else {
                    ErrorManager.getDefault().notify(ee);
                }
                return;
            }
        }
        setURL (URL);
    }

    /**
    * Sets new URL.
    *
    * @param url URL to show in this browser.
    */
    public void setURL (final URL url) {
        if (url == null)
            return;
        
        class URLSetter implements Runnable {
            private boolean sameHosts=false;
            public void run () {
                if (!SwingUtilities.isEventDispatchThread()) {
                    if ("nbfs".equals(url.getProtocol())) { // NOI18N
                        sameHosts = true;
                    }
                    else {
                        sameHosts = (url.getHost () != null)
                                        && (browserImpl.getURL () != null)
                                        && (url.getHost ().equals (browserImpl.getURL ().getHost ()));
                    }
                    SwingUtilities.invokeLater(this);
                } else {
                    if (url.equals (browserImpl.getURL ()) && sameHosts) { // see bug 9470
                        browserImpl.reloadDocument ();
                    } else {
                        browserImpl.setURL (url);
                    }
                }
            }
        }
        rp.getDefault().post(new URLSetter());
    }
    private RequestProcessor rp = new RequestProcessor();

    /**
    * Gets current document url.
    */
    public final URL getDocumentURL () {
        return browserImpl.getURL ();
    }

    /**
    * Enables/disables Home button.
    */
    public final void setEnableHome (boolean b) {
        bHome.setEnabled (b);
        bHome.setVisible (b);
    }

    /**
    * Enables/disables location.
    */
    public final void setEnableLocation (boolean b) {
        cbLocation.setEditable (b);
        cbLocation.setVisible (b);
        cbLabel.setVisible (b);
    }

    /**
    * Gets status line state.
    */
    public boolean isStatusLineVisible () {
        return statusLineVisible;
    }

    /**
    * Shows/hides status line.
    */
    public void setStatusLineVisible (boolean v) {
        if (v == statusLineVisible) return;
        if (v) initStatusLine ();
        else destroyStatusLine ();
    }

    /**
    * Gets status toolbar.
    */
    public boolean isToolbarVisible () {
        return toolbarVisible;
    }

    /**
    * Shows/hides toolbar.
    */
    public void setToolbarVisible (boolean v) {
        if (v == toolbarVisible) return;
        if (v) initToolbar ();
        else destroyToolbar ();
    }

    /**
     * Get the browser implementation.
     * @return the implementation
     * @since org.openide/1 4.27
     */
    public final Impl getBrowserImpl() {
        return browserImpl;
    }

    /**
     * Get the browser component.
     * @return a component or null
     * @since org.openide/1 4.27
     */
    public final Component getBrowserComponent() {
        return browserComponent;
    }

    // helper methods .......................................................................

    /**
    * Returns preferred size.
    */
    public java.awt.Dimension getPreferredSize () {
        java.awt.Dimension superPref = super.getPreferredSize ();
        return new java.awt.Dimension (
                   Math.max (DEFAULT_WIDTH, superPref.width),
                   Math.max (DEFAULT_HEIGHT, superPref.height)
               );
    }


    // innerclasses ..............................................................

    /**
    * Listens on changes in HtmlBrowser.Impl and HtmlBrowser visual components.
    */
    private class BrowserListener implements ActionListener, PropertyChangeListener {
        BrowserListener() {}

        /**
        * Listens on changes in HtmlBrowser.Impl.
        */
        public void propertyChange (PropertyChangeEvent evt) {
            String property = evt.getPropertyName ();
            if (property == null) return;

            if (property.equals (Impl.PROP_URL) ||
                property.equals (Impl.PROP_TITLE))
                HtmlBrowser.this.firePropertyChange(evt.getPropertyName(), evt.getOldValue(), evt.getNewValue());

            if (property.equals (Impl.PROP_URL)) {
                updateLocationBar();
            } else if (property.equals (Impl.PROP_STATUS_MESSAGE)) {
                String s = browserImpl.getStatusMessage ();
                if ((s == null) || (s.length () < 1))
                    s = NbBundle.getMessage (HtmlBrowser.class, "CTL_Document_done");
                if (lStatusLine != null) lStatusLine.setText (s);
            } 
            else if (property.equals (Impl.PROP_FORWARD) && bForward != null) {
                bForward.setEnabled (browserImpl.isForward ());
            } else if (property.equals (Impl.PROP_BACKWARD) && bBack != null) {
                bBack.setEnabled (browserImpl.isBackward ());
            } else if (property.equals (Impl.PROP_HISTORY) && bHistory != null) {
                bHistory.setEnabled (browserImpl.isHistory ());
            }
        }

        /**
        * Listens on changes in HtmlBrowser visual components.
        */
        public void actionPerformed (ActionEvent e) {
            if (e.getSource () == cbLocation) {
                // URL manually changed
                if (everythinkIListenInCheckBoxIsUnimportant) return;
                JComboBox cb = (JComboBox)e.getSource ();
                Object o = cb.getSelectedItem ();
                if (o == null)  // empty combo box
                    return;
                setURL ((String)o);
                ListModel lm = cb.getModel ();
                int i, k = lm.getSize ();
                for (i = 0; i < k; i++) if (o.equals (lm.getElementAt (i))) break;
                if (i != k) return;
                if (k == 20) cb.removeItem (lm.getElementAt (k - 1));
                cb.insertItemAt (o, 0);
            } else

                if (e.getSource () == bHistory) {
                    browserImpl.showHistory ();
                } else

                    if (e.getSource () == bBack) {
                        browserImpl.backward ();
                    } else

                        if (e.getSource () == bForward) {
                            browserImpl.forward ();
                        } else

                            if (e.getSource () == bReload) {
                                updateLocationBar();
                                browserImpl.reloadDocument ();
                            } else

                                if (e.getSource () == bHome) {
                                    setURL (homePage);
                                } else

                                    if (e.getSource () == bStop) {
                                        browserImpl.stopLoading ();
                                    }
        }
    }

    /**
     * Show current brower's URL in the location bar combo box.
     */
    private void updateLocationBar() {
        if (toolbarVisible) {
            everythinkIListenInCheckBoxIsUnimportant = true;
            String url = browserImpl.getURL ().toString ();
            cbLocation.setSelectedItem (url);
            everythinkIListenInCheckBoxIsUnimportant = false;
        }
    }

    /**
    * This interface represents an implementation of html browser used in HtmlBrowser. Each BrowserImpl
    * implementation corresponds with some BrowserFactory implementation.
    */
    public static abstract class Impl {

        /** generated Serialized Version UID */
        static final long            serialVersionUID = 2912844785502962114L;

        /** The name of property representing status of html browser. */
        public static final String PROP_STATUS_MESSAGE = "statusMessage"; // NOI18N
        /** The name of property representing current URL. */
        public static final String PROP_URL = "url"; // NOI18N
        /** Title property */
        public static final String PROP_TITLE = "title"; // NOI18N
        /** forward property */
        public static final String PROP_FORWARD = "forward"; // NOI18N
        /** backward property name */
        public static final String PROP_BACKWARD = "backward"; // NOI18N
        /** history property name */
        public static final String PROP_HISTORY = "history"; // NOI18N

        /**
        * Returns visual component of html browser.
        *
        * @return visual component of html browser.
        */
        public abstract java.awt.Component getComponent ();

        /**
        * Reloads current html page.
        */
        public abstract void reloadDocument ();

        /**
        * Stops loading of current html page.
        */
        public abstract void stopLoading ();

        /**
        * Sets current URL.
        *
        * @param url URL to show in the browser.
        */
        public abstract void setURL (URL url);

        /**
        * Returns current URL.
        *
        * @return current URL.
        */
        public abstract URL getURL ();

        /**
        * Returns status message representing status of html browser.
        *
        * @return status message.
        */
        public abstract String getStatusMessage ();

        /** Returns title of the displayed page.
        * @return title 
        */
        public abstract String getTitle ();


        /** Is forward button enabled?
        * @return true if it is
        */
        public abstract boolean isForward ();

        /** Moves the browser forward. Failure is ignored.
        */
        public abstract void forward ();

        /** Is backward button enabled?
        * @return true if it is
        */
        public abstract boolean isBackward ();

        /** Moves the browser forward. Failure is ignored.
        */
        public abstract void backward ();

        /** Is history button enabled?
        * @return true if it is
        */
        public abstract boolean isHistory ();

        /** Invoked when the history button is pressed.
        */
        public abstract void showHistory ();

        /**
        * Adds PropertyChangeListener to this browser.
        *
        * @param l Listener to add.
        */
        public abstract void addPropertyChangeListener (PropertyChangeListener l);

        /**
        * Removes PropertyChangeListener from this browser.
        *
        * @param l Listener to remove.
        */
        public abstract void removePropertyChangeListener (PropertyChangeListener l);
    }

    /**
    * Implementation of BrowerFactory creates new instances of some Browser implementation.
    *
    * @see HtmlBrowser.Impl
    */
    public interface Factory {
        /**
        * Returns a new instance of BrowserImpl implementation.
        */
        public Impl createHtmlBrowserImpl ();
    }
    
    /** A manager class which can display URLs in the proper way.
     * Might open a selected HTML browser, knows about embedded vs. external
     * browsers, etc.
     * @since 3.14
     */
    public static abstract class URLDisplayer {
        
        /** Get the default URL displayer.
         * @return the default instance from lookup
         */
        public static URLDisplayer getDefault() {
            URLDisplayer dflt = (URLDisplayer) Lookup.getDefault().lookup(URLDisplayer.class);
            if (dflt == null) {
                // Fallback.
                dflt = new TrivialURLDisplayer();
            }
            return dflt;
        }
        
        /** Subclass constructor. */
        protected URLDisplayer() {}

        /** Display a URL to the user somehow.
         * @param u the URL to show
         */
        public abstract void showURL(URL u);
        
    }
    
    private static final class TrivialURLDisplayer extends URLDisplayer {
        
        public TrivialURLDisplayer() {}
        
        public void showURL(URL u) {
            HtmlBrowser browser = new HtmlBrowser();
            browser.setURL(u);
            JFrame frame = new JFrame();
            frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            frame.getContentPane().add(browser);
            frame.pack();
            frame.setVisible(true);
        }
        
    }
    
////// Accessibility //////
    
    public void requestFocus() {
        if (browserComponent != null) {
            boolean ownerFound = false;
            if (browserComponent instanceof JComponent) {
                ownerFound = ((JComponent)browserComponent).requestDefaultFocus();
            }
            if (!ownerFound) {
                browserComponent.requestFocus();
            }
        } else {
            super.requestFocus();
        }
    }
    
    public boolean requestFocusInWindow() {
        if (browserComponent != null) {
            boolean ownerFound = false;
            if (browserComponent instanceof JComponent) {
                ownerFound = ((JComponent)browserComponent).requestDefaultFocus();
            }
            if (!ownerFound) {
                return browserComponent.requestFocusInWindow();
            } else {
                return true;
            }
        } else {
            return super.requestFocusInWindow();
        }
    }
    
    public AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
            accessibleContext = new AccessibleHtmlBrowser();
        }
        return accessibleContext;
    }

    private class AccessibleHtmlBrowser extends JPanel.AccessibleJPanel {
        AccessibleHtmlBrowser() {}
        public void setAccessibleName(String name) {
            super.setAccessibleName(name);
            if (browserComponent instanceof Accessible) {
                browserComponent.getAccessibleContext().setAccessibleName(name);
            }
        }
        public void setAccessibleDescription(String desc) {
            super.setAccessibleDescription(desc);
            if (browserComponent instanceof Accessible) {
                browserComponent.getAccessibleContext().setAccessibleDescription(desc);
            }
        }
    }
    
}
