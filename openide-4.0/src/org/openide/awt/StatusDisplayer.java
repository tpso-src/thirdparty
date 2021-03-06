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

package org.openide.awt;

import java.util.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.openide.util.Lookup;

/** Permits control of a status line.
 * The default instance may correspond to the NetBeans status line in the main window.
 * @author Jesse Glick
 * @since 3.14
 */
public abstract class StatusDisplayer {
    
    private static StatusDisplayer INSTANCE = null;

    /** Get the default status displayer.
     * @return the default instance from lookup
     */
    public static synchronized StatusDisplayer getDefault() {
        if (INSTANCE == null) {
            INSTANCE = (StatusDisplayer)Lookup.getDefault().lookup(StatusDisplayer.class);
            if (INSTANCE == null) {
                INSTANCE = new Trivial();
            }
        }
        return INSTANCE;
    }

    /** Subclass constructor. */
    protected StatusDisplayer() {}

    /** Get the currently displayed text.
     * <p>Modules should <strong>not</strong> need to call this method.
     * If you think you really do, please explain why on nbdev.
     * The implementation of the GUI component (if any) which displays
     * the text naturally needs to call it.
     * @return some text
     */
    public abstract String getStatusText();

    /** Show text in the status line.
     * Can be called at any time, but remember the text may not be updated
     * until the AWT event queue is ready for it - so if you are hogging
     * the event queue the text will not appear until you release it
     * (finish your work or display a modal dialog, for example).
     * @param text the text to be shown
     */
    public abstract void setStatusText(String text);
    
    /** Add a listener for when the text changes.
     * @param l a listener
     */
    public abstract void addChangeListener(ChangeListener l);
    
    /** Remove a listener for the text.
     * @param l a listener
     */
    public abstract void removeChangeListener(ChangeListener l);
    
    /**
     * Trivial default impl for standalone usage.
     * @see "#32154"
     */
    private static final class Trivial extends StatusDisplayer {
        
        private List listeners = null; // List<ChangeListener>
        private String text = ""; // NOI18N
        
        public synchronized String getStatusText() {
            return text;
        }
        
        public synchronized void setStatusText(String text) {
            if (text.equals(this.text)) {
                return;
            }
            this.text = text;
            if (text.length() > 0) {
                System.err.println("(" + text + ")"); // NOI18N
            }
            fireChange();
        }
        
        public synchronized void addChangeListener(ChangeListener l) {
            if (listeners == null) {
                listeners = new ArrayList();
            }
            listeners.add(l);
        }
        
        public synchronized void removeChangeListener(ChangeListener l) {
            if (listeners != null) {
                listeners.remove(l);
            }
        }
        
        protected final void fireChange() {
            if (listeners != null && !listeners.isEmpty()) {
                ChangeEvent ev = new ChangeEvent(this);
                Iterator it = listeners.iterator();
                while (it.hasNext()) {
                    ((ChangeListener)it.next()).stateChanged(ev);
                }
            }
        }
        
    }

}
