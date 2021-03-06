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

package org.openide.nodes;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.*;
import org.openide.ErrorManager;

import org.openide.util.Lookup;
import org.openide.util.UserCancelException;

/** Provides common operations on nodes.
 * Any component may
 * ask to open a customizer for, or explore, any node.
 * @since 3.14
 */
public abstract class NodeOperation {

    /** Get default instance from lookup.
     * @return some instance
     */
    public static NodeOperation getDefault() {
        NodeOperation no = (NodeOperation)Lookup.getDefault().lookup(NodeOperation.class);
        if (no == null) {
            throw new IllegalStateException (
                "To use NodeOperation you should have its implementation around. For example one from openide-explorer.jar" // NOI18N
            );
        }
        return no;
    }
    
    /** Subclass constructor. */
    protected NodeOperation() {}

    /** Tries to open a customization dialog for the specified node.
     * The dialog is
     * modal and the function returns only after
     * customization is finished, if it was possible.
     *
     * @param n the node to customize
     * @return <CODE>true</CODE> if the node had a customizer,
     * <CODE>false</CODE> if not
     * @see Node#hasCustomizer
     * @see Node#getCustomizer
     */
    public abstract boolean customize(Node n);
    
    /** Explore a node (and its subhierarchy).
     * It will be opened in a new Explorer view, as the root node of that window.
     * @param n the node to explore
     */
    public abstract void explore(Node n);
    
    /** Open a modal Property Sheet on a node.
     * @param n the node to show properties of
     */
    public abstract void showProperties(Node n);
    
    /** Open a modal Property Sheet on a set of nodes.
     * @param n the array of nodes to show properties of
     * @see #showProperties(Node)
     */
    public abstract void showProperties(Node[] n);
    
    /** Open a modal Explorer on a root node, permitting a node selection to be returned.
     * <p>The acceptor
     * should be asked each time the set of selected nodes changes, whether to accept or
     * reject the current result. This will affect for example the
     * display of the "OK" button.
     *
     * @param title title of the dialog
     * @param rootTitle label at root of dialog. May use <code>&amp;</code> for a {@link javax.swing.JLabel#setDisplayedMnemonic(int) mnemonic}.
     * @param root root node to explore
     * @param acceptor class asked to accept or reject current selection
     * @param top an extra component to be placed on the dialog (may be <code>null</code>)
     * @return an array of selected (and accepted) nodes
     *
     * @exception UserCancelException if the selection is interrupted by the user
     */
    public abstract Node[] select(String title, String rootTitle, Node root, NodeAcceptor acceptor, Component top)
        throws UserCancelException;
    
    /** Open a modal Explorer without any extra dialog component.
     * @param title title of the dialog
     * @param rootTitle label at root of dialog. May use <code>&amp;</code> for a {@link javax.swing.JLabel#setDisplayedMnemonic(int) mnemonic}.
     * @param root root node to explore
     * @param acceptor class asked to accept or reject current selection
     * @return an array of selected (and accepted) nodes
     *
     * @exception UserCancelException if the selection is interrupted by the user
     * @see #select(String, String, Node, NodeAcceptor, Component)
     */
    public Node[] select(String title, String rootTitle, Node root, NodeAcceptor acceptor)
        throws UserCancelException  {
        //XXX AFAIK this is completely unused in NetBeans.  Deprecate? -Tim
        return select(title, rootTitle, root, acceptor, null);
    }
    
    /** Open a modal Explorer accepting only a single node.
     * @param title title of the dialog
     * @param rootTitle label at root of dialog. May use <code>&amp;</code> for a {@link javax.swing.JLabel#setDisplayedMnemonic(int) mnemonic}.
     * @param root root node to explore
     * @return the selected node
     *
     * @exception UserCancelException if the selection is interrupted by the user
     * @see #select(String, String, Node, NodeAcceptor)
     */
    public final Node select(String title, String rootTitle, Node root) throws UserCancelException {
        return select(title, rootTitle, root, new NodeAcceptor() {
            public boolean acceptNodes(Node[] nodes) {
                return nodes.length == 1;
            }
        })[0];
    }

}
