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

package org.openide.filesystems;

import java.io.*;
import java.util.*;
import java.lang.ref.*;
import java.net.URL;
import org.openide.ErrorManager;
import org.openide.util.Enumerations;
import org.openide.util.NbBundle;
import org.openide.util.Utilities;

/** Implementation of the file object that simplyfies common
* tasks with hierarchy of objects for AbstractFileObject and MultiFileObject.
*
* @author Jaroslav Tulach, 
*/
abstract class AbstractFolder extends FileObject {
    /** empty array */
    private static final AbstractFolder[] EMPTY_ARRAY = new AbstractFolder[0];


    /** default extension separator */
    private static final char EXT_SEP = '.';

    /** empty hash map to mark that we are initialized */
    private static final HashMap EMPTY = new HashMap (0);

    /** file system */
    private FileSystem system;

    /** name of the file (only name and extension) */
    protected String name;

    /** strong reference to parent (can be null for root) */
    protected final AbstractFolder parent;

    /** Stores the system name of the file system to test
    * validity later.
    */
    boolean validFlag;

    /** If root changes, all AbstractFolders in hierarchy are invalidated. 
     *@see #isValid()*/
    private final AbstractFolder validRoot;
    
    /** list of children */
    private String[] children;

    /** map that assignes file object to names. (String, Reference (AbstractFileObject)) */
    private HashMap map;

    /** listeners */
    private ListenerList listeners;

    /** Constructor. Takes reference to file system this file belongs to.
    *
    * @param fs the file system
    * @param parent the parent object (folder)
    * @param name name of the object (e.g. <code>filename.ext</code>)
    */
    public AbstractFolder(
        FileSystem fs, AbstractFolder parent, String name
    ) {
        this.system = fs;
        this.parent = parent;
        this.name = name;
        validFlag = true;        
        validRoot = (parent != null) ? (AbstractFolder) fs.getRoot() : null;
    }

    /* Get the name without extension of this file or folder.
    * Period at first position is not considered as extension-separator     
    * @return name of the file or folder(in its enclosing folder)
    */
    public final String getName () {
        int i = name.lastIndexOf ('.');
        /** period at first position is not considered as extension-separator */        
        return i <= 0 ? name : name.substring (0, i);
    }

    /* Get the extension of this file or folder.
    * Period at first position is not considered as extension-separator     
    * This is the string after the last dot of the full name, if any.
    *
    * @return extension of the file or folder(if any) or empty string if there is none
    */
    public final String getExt () {
        int i = name.lastIndexOf ('.') + 1;
        /** period at first position is not considered as extension-separator */
        return i <= 1 || i == name.length ()  ? "" : name.substring (i); // NOI18N
    }
    
    /** Overrides the get name and ext method to make it faster then 
     * default implementation.
     */
    public final String getNameExt () {
        return name;
    }
    
    
    /** Overriden in AbstractFolder */
    final boolean isHasExtOverride() {
        return true;
    }    

    /** Overriden in AbstractFolder */
    boolean hasExtOverride(String ext) {
        if (ext == null) {
            return false;
        }
        /** period at first position is not considered as extension-separator */        
        if (name.length() - ext.length() <= 1) {
            return false;
        }
        
        boolean ret = name.endsWith(ext);
        if (! ret) {
            return false;
        }
        
        if (name.charAt(name.length() - ext.length() - 1) != '.') {
            return false;
        }
                
        return true;
    }
    
    /* Getter for the right file system */
    public final FileSystem getFileSystem () {
        return system;
    }

    //
    // Info
    //

    /* Test whether this object is the root folder.
    * The root should always be a folder.
    * @return true if the object is the root of a file system
    */
    public final boolean isRoot () {
        return parent == null;
    }


    /* Test whether the file is valid. The file can be invalid if it has been deserialized
    * and the file no longer exists on disk; or if the file has been deleted.
    *
    * @return true if the file object is valid
    */
    public final boolean isValid () {
        // valid
        if (parent == null) {
            return validFlag;
        }
        boolean isValidRoot = getFileSystem ().getRoot () == validRoot;
        return validFlag && isValidRoot;
    }

    //
    // List
    //

    /* Get parent folder.
    * The returned object will satisfy {@link #isFolder}.
    *
    * @return the parent folder or <code>null</code> if this object {@link #isRoot}.
    */
    public final FileObject getParent () {
        return parent;
    }


    /* Get all children of this folder (files and subfolders). If the file does not have children
    * (does not exist or is not a folder) then an empty array should be returned. No particular order is assumed.
    *
    * @return array of direct children
    * @see #getChildren(boolean)
    * @see #getFolders
    * @see #getData
    */
    public final synchronized FileObject[] getChildren () {
        check ();

        if (children == null) {
            return new FileObject[0];
        }

        int size = children.length;
        ArrayList aList = new ArrayList();
        for (int i = 0; i < size; i++) {
            FileObject f = getChild (children[i]);
            if (f != null) aList.add(f);
        }

        return (FileObject [])aList.toArray(new FileObject [0]);
    }

    /** Tries to find a resource.
    * @param en enumeration of strings to scan
    * @return found object or null
    */
    final FileObject find (Enumeration en) {
        AbstractFolder fo = this;
        while (fo != null && en.hasMoreElements ()) {
            // try to go on
            // lock to provide safety for getChild
            synchronized (fo) {
                // JST: Better to call the check only here,
                // than in getChild, than it is not called
                // so often.
                fo.check ();

                fo = fo.getChild ((String)en.nextElement ());
            }
        }
        // no next requirements or not found
        return fo;
    }


    /** Tries to find a resource if it exists in memory.
    * @param en enumeration of strings to scan
    * @return found object or null
    */
    final FileObject findIfExists (Enumeration en) {
        Reference r = findRefIfExists(en);
        return(r == null? null : (FileObject)r.get());
    }

    /** Tries to find a resource if it exists in memory.
    * @param en enumeration of strings to scan
    * @return found object or null
    */
    final Reference findRefIfExists (Enumeration en) {
        AbstractFolder fo = this;
        while (fo != null && en.hasMoreElements ()) {
            if (fo.map == null) {
                // this object is not initialized yet
                return null;
            }
            // try to go on
            // lock to provide safety for getChild
            synchronized (fo) {
                String name = (String)en.nextElement ();
                if (en.hasMoreElements()){
                    fo = fo.getChild(name);
                }else{
                    return((Reference)fo.map.get(name));
                }
            }
        }
        // no next requirements or not found
        return null;
    }

    /** Finds one child for given name .
    * @param name the name of the child
    * @return the file object or null if it does not exist
    */
    protected final AbstractFolder getChild (String name) {
        return getChild (name, true);
    }
    
    private final AbstractFolder getChild (String name, boolean onlyValid) {    
        Reference r = (Reference)map.get (name);
        if (r == null) {
            //On OpenVMS, see if the name is stored in a different case
            //to work around a JVM bug.
            //
            if (Utilities.getOperatingSystem() == Utilities.OS_VMS) {
                if (Character.isLowerCase(name.charAt(0)))
                    r = (Reference)map.get(name.toUpperCase());
                else
                    r = (Reference)map.get(name.toLowerCase());
                if (r == null)
                    return null;
            } else
                return null;
        }

        AbstractFolder fo = (AbstractFolder)(r.get ());

        if (fo == null) {
            // object does not exist => have to recreate it
            fo = createFile (name);                 
            if (fo != null && fo.isValid()) {
                map.put (name, (fo != null) ? createReference(fo) : null);
            } else {
                if (onlyValid) {
                    fo = null;    
                }                
            }
        }

        return fo;
    }

    /** Get method for children array .
    * @return array of children
    */
    final  String[] getChildrenArray () { return children;}
    
    
    /** Creates Reference. In FileSystem, which subclasses AbstractFileSystem, you can overload method
    * createReference(FileObject fo) to achieve another type of Reference (weak, strong etc.)
    * @param fo FileObject
    * @return Reference to FileObject
    */
    protected  Reference createReference(FileObject fo){
        return(new WeakReference (fo));
    }

    /** Obtains enumeration of all existing subfiles.
    */
    final synchronized AbstractFolder[] subfiles () {
        if (map == null) {
            return EMPTY_ARRAY;
        }
        Iterator it = map.values ().iterator ();
        ArrayList ll = new ArrayList(map.size() + 2);
        while (it.hasNext ()) {
            Reference r = (Reference)it.next ();
            if (r == null) {
                continue;
            }
            AbstractFolder fo = (AbstractFolder)r.get ();
            if (fo != null /*&& (!fo.isFolder () || fo.map != null)*/) {
                // if the file object exists and either is not folder (then
                // we have to check the time) or it is folder and it has
                // some children
                // => use it
                ll.add (fo);
            }
        }

        return (AbstractFolder[])ll.toArray (EMPTY_ARRAY);
    }

    final boolean isInitialized () {
        return this.map != null;
    }
    /** Creates enumeration of existing subfiles in all tree
    * of files.
    *
    * @param rec should it be recursive or not
    * @return enumeration of AbstractFolders
    */
    final Enumeration existingSubFiles (boolean rec) {
        if (!rec) {
            return Enumerations.array (subfiles ());
        } else {
            class P implements org.openide.util.Enumerations.Processor {
                public Object process(Object o, Collection toAdd) {
                   AbstractFolder af = (AbstractFolder)o;
                   toAdd.addAll (Arrays.asList (af.subfiles ()));
                   return o;
               }
            }
            return Enumerations.queue (
                Enumerations.singleton (this),
                new P ()
            );
        }
    }
    
    /* helper method for MFO.setAttribute. MFO can disable firing from underlaying
     * layers. Should be reviewed in 3.4 or later 
      *@see MultiFileObject#setAttribute*/
    abstract void setAttribute(String attrName, Object value, boolean fire) throws IOException;
        
    /** Retrieve file or folder contained in this folder by name.
    * <em>Note</em> that neither file nor folder is created on disk.
    * @param name basename of the file or folder (in this folder)
    * @param ext extension of the file; <CODE>null</CODE> or <code>""</code>
    *    if the file should have no extension or if folder is requested
    * @return the object representing this file or <CODE>null</CODE> if the file
    *   or folder does not exist
    * @exception IllegalArgumentException if <code>this</code> is not a folder
    */
    public final synchronized FileObject getFileObject (String name, String ext) {
        check ();

        if (ext == null || ext.equals ("")) { // NOI18N
            return getChild (name);
        } else {
            StringBuffer sb = new StringBuffer(name.length() + 1 + ((ext == null) ? 0 : ext.length()));
            sb.append(name).append(EXT_SEP).append(ext);
            return getChild (sb.toString());
        }
    }

    /* Refresh the contents of a folder. Rescans the list of children names.
    */
    public void refresh(boolean expected) {
        if (!isInitialized () && isFolder ())
            return;
        
        refresh (null, null, true, expected);
    }


    //
    // Listeners section
    //

    /* Add new listener to this object.
    * @param l the listener
    */
    public final void addFileChangeListener(FileChangeListener fcl) {
        synchronized (EMPTY_ARRAY) {
            if (listeners == null) {
                listeners = new ListenerList (FileChangeListener.class);
            }
        }
        listeners.add(fcl);
    }


    /* Remove listener from this object.
    * @param l the listener
    */
    public final void removeFileChangeListener (FileChangeListener fcl) {
        if (listeners != null) {
            listeners.remove (fcl);
        }
    }

    /** Fires event */
    protected final void fileDeleted0(FileEvent fileevent) {
        super.fireFileDeletedEvent(listeners (), fileevent);
        if(fileevent.getFile().equals(this) && parent != null) {
            FileEvent ev = new FileEvent(parent, fileevent.getFile(),fileevent.isExpected ());
            parent.fileDeleted0(ev);
        }
    }

    /** Fires event */
    protected final void fileCreated0(FileEvent fileevent, boolean isData) {
/*
        if(isData)
            super.fireFileDataCreatedEvent(listeners (), fileevent);
        else
            super.fireFileFolderCreatedEvent(listeners (), fileevent);
*/
        dispatchEvent(listeners(), fileevent);

        if(fileevent.getFile().equals(this) && parent != null) {
            FileEvent ev = new FileEvent(parent, fileevent.getFile(), fileevent.isExpected ());
            parent.fileCreated0 (ev, isData);
        }
    }
    /** Creates nad fires event */
    protected final void fileCreated0(FileObject src, FileObject file, boolean expected) {
        fileCreated0(new FileEvent (src, file, expected), false);
    }
    
    /** Fires event */
    protected final void fileChanged0 (FileEvent fileevent) {
        super.fireFileChangedEvent(listeners (), fileevent);
        if(fileevent.getFile().equals(this) && parent != null) {
            FileEvent ev = new FileEvent(parent, fileevent.getFile(), fileevent.isExpected ());
            parent.fileChanged0 (ev);
        }
    }
    
    /** Fires event - but doesn`t fork events*/
    final void fileChanged1 (FileEvent fileevent) {
        super.fireFileChangedEvent(listeners (), fileevent);
    }
    
    /** Fires event */
    protected final void fileRenamed0 (FileRenameEvent filerenameevent) {
        super.fireFileRenamedEvent(listeners (), filerenameevent);
        if(filerenameevent.getFile().equals(this) && parent != null) {
            FileRenameEvent ev = new FileRenameEvent(
                                     parent,
                                     filerenameevent.getFile(),
                                     filerenameevent.getName(),
                                     filerenameevent.getExt(),
                                     filerenameevent.isExpected ()
                                 );
            parent.fileRenamed0 (ev);
        }
    }

    /** Fires event */
    protected final void fileAttributeChanged0 (FileAttributeEvent fileattributeevent) {
        super.fireFileAttributeChangedEvent(listeners (), fileattributeevent);
        if(fileattributeevent.getFile().equals(this) && parent != null) {
            FileAttributeEvent ev = new FileAttributeEvent(
                                        parent,
                                        fileattributeevent.getFile(),
                                        fileattributeevent.getName(),
                                        fileattributeevent.getOldValue(),
                                        fileattributeevent.getNewValue(), 
                                        fileattributeevent.isExpected ()
                                    );
            parent.fileAttributeChanged0 (ev);
        }
    }

    /** @return true if there is a listener
    */
    protected final boolean hasListeners () {
        boolean fsHas = getFileSystem().getFCLSupport ().hasListeners ();
        boolean repHas = false;        
        Repository rep  = getFileSystem().getRepository ();
        if (rep != null)
            repHas = rep.getFCLSupport ().hasListeners ();        
        
        return (listeners != null && listeners.getAllListeners ().length != 0) || repHas || fsHas;
    }

    /** @return true if this folder or its parent have listeners
    */
    protected final boolean hasAtLeastOneListeners () {
        
        return hasListeners () || (parent != null && parent.hasListeners ());         
    }

    /** @return enumeration of all listeners.
    */
    private final Enumeration listeners () {
        if (listeners == null) {
            return Enumerations.empty();
        } else {
            return Enumerations.removeNulls (
                Enumerations.array (listeners.getAllListeners ())
            );
        }
    }

    //
    // Refreshing the state of the object
    //

    /** Test if the file has been checked and if not, refreshes its
    * content.
    */
    private final void check () {
        if (map == null) {
            refresh (null, null, false, false);
            if (map == null) {
                // create empty map to mark that we are initialized
                map = EMPTY;
                if (children == null) {
                    children = new String [] {};                   
                }
            }
        }
    }

    /** Refresh the content of file. Ignores changes to the files provided,
    * instead returns its file object.
    * @param added do not notify addition of this file
    * @param removed do not notify removing of this file
    */
    protected final void refresh (String added, String removed) {
        this.refresh(added, removed, false);
    }

    /** Refresh the content of file. Ignores changes to the files provided,
    * instead returns its file object.
    * @param added do not notify addition of this file
    * @param removed do not notify removing of this file
    * @param reuseChildren a flag reuse children?
    */
    protected final void refresh (String added, String removed, boolean reuseChildren) {
        if (reuseChildren && removed != null) {
            String[] nc = new String[children.length];
            System.arraycopy(children, 0, nc, 0, children.length);
            for (int i = nc.length; --i >= 0; ) {
                if (removed.equals(nc[i])) {
                    nc[i] = null;
                    break;
                }
            }
            refresh (added, removed, true, false, nc);
        } else {
            refresh (added, removed, true, false, null);
        }
    }

    /** Method that allows subclasses to return its children.
    *
    * @return names (name . ext) of subfiles
    */
    protected abstract String[] list ();

    /** Method to create a file object for given subfile.
    * @param name of the subfile
    * @return the file object
    */
    protected abstract AbstractFolder createFile (String name);

    /** Refresh the content of file. Ignores changes to the files provided,
    * instead returns its file object.
    * @param added do not notify addition of this file
    * @param removed do not notify removing of this file
    * @param fire true if we should fire changes
    * @param expected true if the change has been expected by the user
    */
    protected void refresh (
        String added, String removed, boolean fire, boolean expected
    ) {
        this.refresh(added, removed, fire, expected, null);
    }


    void registerChild (String name) {
        synchronized (this) {
            if (map == null)
                check ();

            Object o = map.put(name, new WeakReference(null));
            if (o != null) {
                map.put(name, o);
            } else {
                String newChildren[] = new String [children.length + 1];
                System.arraycopy(children, 0, newChildren, 0, children.length);
                newChildren [children.length] = name;
                children = newChildren;
            }
        }
    }

    /** Refresh the content of file. Ignores changes to the files provided,
    * instead returns its file object.
    * @param added do not notify addition of this file
    * @param removed do not notify removing of this file
    * @param fire true if we should fire changes
    * @param expected true if the change has been expected by the user
    * @param list contains list of children
    */
    protected final void refreshFolder(String added, String removed, boolean fire, boolean expected, final String[] list) {
        try {
            getFileSystem().beginAtomicAction();
            // refresh of folder checks children
            final String[] newChildren = getNewChildren(list);
            final Set addedNames;
            final Map removedPairs;
            
            synchronized (this) {
                if (children == null && newChildren == null) {
                    return;
                }

                final int initialCapacity = (newChildren != null) ? (newChildren.length * 4 / 3 + 1) : 0;
                final HashMap newMap = new HashMap(initialCapacity);/*<String, AbstractFileObject>*/
                
                /*Just for firing event*/
                addedNames = new HashSet(initialCapacity);/*<String>*/
                                                 
                if (newChildren != null) {
                    final Reference removedRef = (Reference) (map != null ? map.get(removed) : null);

                    for (int i = 0; i < newChildren.length; i++) {
                        final String child = newChildren[i];
                        Reference foRef = null;

                        if (map != null) {
                            foRef = (Reference) map.remove(child);
                            
                            {   /** #18340 
                                 * In CVS filesystems renaming of FileObject means that 
                                 * two FileObjects will be present (old - needs checkout, 
                                 * new - localy added). 
                                 */                                                            
                                if ((foRef != null && added != null && removed != null && child.equals(removed))) {
                                    // needs cvs checkout
                                    foRef = null;
                                }                
                            
                                if (added != null && removed != null && child.equals(added)) {
                                    foRef = removedRef;     
                                }
                                
                                if ((foRef != null &&  added == null && removed != null && child.equals(removed))) {
                                    // needs cvs checkout
                                    foRef = null;
                                }                
                                
                            }
                            
                        }

                        if (foRef == null) {
                            if (!child.equals(added)) {
                                addedNames.add(child);    
                            }                            
                            // create new empty reference
                            foRef = new WeakReference(null);

                        }
                        newMap.put(child, foRef);
                    }                                                                                
                } 
                
                removedPairs = (map != null) ? dereferenceValues(map) : null;/*<String, AbstractFileObject>*/                 
                map = newMap;
                children = newChildren;                                
            }
            if (fire && addedNames != null && hasAtLeastOneListeners()) {
                // fire these files has been added
                filesCreated(addedNames, expected);
            }

            if (fire && removedPairs != null) {
                if (removed != null) {
                    removedPairs.remove(removed);
                }
                filesDeleted(removedPairs, expected);
            }

            if (fire && added == null && removed == null && !getFileSystem().isReadOnly() && !(this instanceof MultiFileObject)) {
                Set nameFilter = nameFilter = new HashSet();
                if (addedNames != null) nameFilter.addAll(addedNames);
                if (removedPairs != null) nameFilter.addAll(removedPairs.keySet());                    
                refreshChildren(existingSubFiles(false),nameFilter, expected);
            }            
        } finally {
            getFileSystem().finishAtomicAction();
        }
    }

    private void refreshChildren(final Enumeration subfiles, final Collection nameFilter, boolean expected) {        
        while (subfiles.hasMoreElements()) {
            AbstractFolder child = (AbstractFolder) subfiles.nextElement();

            if (child.isData()) {
                if (!nameFilter.contains(child.getNameExt())) {
                    child.refresh(expected);
                }
            }
        }
    }

    private void filesDeleted(final Map/*<String, AbstractFileObject>*/ removedToFire, final boolean expected) {
        for (Iterator it = removedToFire.values ().iterator(); it.hasNext();) {
            AbstractFolder fo = (AbstractFolder )it.next();                        
            fo.validFlag = false;

            if (hasAtLeastOneListeners() || fo.hasAtLeastOneListeners()) {
                FileEvent ev = new FileEvent(fo, fo, expected);
                fo.fileDeleted0(ev);
            }
        }
    }

    private void filesCreated(final Set/*<String>*/ addedToFire, final boolean expected) {
        for (Iterator it = addedToFire.iterator(); it.hasNext();) {
            AbstractFolder fo = getChild((String) it.next());
            if (fo != null) {
                fileCreated0(this, fo, expected);    
            }                        
        }
    }

    private Map/*<String, AbstractFileObject>*/ dereferenceValues(final Map/*<String, Reference (AbstractFileObject)> */ map) {
        Map retVal = new HashMap (map.size());
        Iterator it = map.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry entry = (Map.Entry) it.next();
            String name = (String) entry.getKey();
            AbstractFolder child = getChild(name, false);
            if (child != null) retVal.put(name, child);
        }
        return retVal;
    }

    private String[] getNewChildren(final String[] list) {
        String[] newChildren = (list != null) ? list : list();

        if (isRoot() && newChildren == null) {
            newChildren = new String[0];
        } 
        
        if (newChildren != null) {
            newChildren = stripNulls(newChildren);

        }
                
        return newChildren;
    }

    private static String[] stripNulls(final String[] children) {
        String[] newChildren = children;
        Collection childrenList = new ArrayList (Arrays.asList(newChildren));
        for (Iterator iterator = childrenList.iterator(); iterator.hasNext();) {
            Object child = iterator.next();
            if (child == null) {
                iterator.remove();
            }                
        }            
            
        if (childrenList.size() != newChildren.length) {
            newChildren = (String[])childrenList.toArray(new String[childrenList.size()]);
        }
        return newChildren;
    }

    /** Refresh the content of file. Ignores changes to the files provided,
    * instead returns its file object.
    * @param added do not notify addition of this file
    * @param removed do not notify removing of this file
    * @param fire true if we should fire changes
    * @param expected true if the change has been expected by the user
    * @param list contains list of children
    */
    protected void refresh (
        String added, String removed, boolean fire, boolean expected, String[] list
    ) {
        if (isFolder ()) {
            refreshFolder (added, removed, fire, expected, list);
        }
    }
    
    /** Notification that the output stream has been closed.
     * @param fireFileChanged defines if FileEvent should be fired to notify about 
     * change of file after close of stream
     */
    protected void outputStreamClosed (boolean fireFileChanged) {
        if (fireFileChanged)
            fileChanged0 (new FileEvent (AbstractFolder.this));
    }
        

    //
    // Serialization
    //

    public final Object writeReplace () {
        return new Replace(this);
    }
    
    /* Delete this file. If the file is a folder and it is not empty then
    * all of its contents are also recursively deleted.
    *
    * @param lock the lock obtained by a call to {@link #lock}
    * @exception IOException if the file could not be deleted
    */    
    final public void delete(FileLock lock) throws IOException {
        if (isFolder ()) {
            FileObject[] fos = this.getChildren();
            for (int i = 0; i < fos.length; i++) {
                FileObject fo = fos[i];
                FileLock foLock = fo.lock();
                try {
                    fo.delete(foLock);
                } catch (IOException iex) {
                    String   message = NbBundle.getMessage(getClass(), "EXC_CannotDelete", //NOI18N
                    // XXX use FileUtil.getFileDisplayName instead?
                    getPath(), fo.getFileSystem().getDisplayName());
                    ExternalUtil.annotate(iex, message);//NOI18N                    
                    throw iex;
                } finally {
                    foLock.releaseLock();
                }
            }
        }
        handleDelete (lock);        
    }
    
    abstract void handleDelete (FileLock lock) throws IOException;

    public boolean canWrite() {
        return !isReadOnly();
    }
    
    private static final class Replace implements Serializable {
        
        private static final long serialVersionUID = 1L;
        
        private transient FileObject f;
        private String fsname;
        private String path;
        private URL url;
        
        public Replace(FileObject f) {
            this.f = f;
        }
        
        private void writeObject(ObjectOutputStream oos) throws IOException {
            fsname = f.getFileSystem().getSystemName();
            path = f.getPath();
            url = f.getURL();
            assert url != null : "No URL for " + path;
            oos.defaultWriteObject();
        }
        
        private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
            ois.defaultReadObject();
            assert fsname != null : "Should always have a non-null fsname here";
            FileSystem fs = Repository.getDefault().findFileSystem(fsname);
            if (fs != null) {
                assert path != null : "Should always have a non-null path here";
                f = fs.findResource(path);
            }
            if (f == null) {
                // Fall back to URL.
                assert url != null : "Should always have a non-null URL here";
                FileObject[] fos = URLMapper.findFileObjects(url);
                if (fos.length >= 1) {
                    f = fos[0];
                } else {
                    throw new FileNotFoundException("Could not restore: " + url); // NOI18N
                }
            }
        }
        
        public Object readResolve() {
            assert f != null : "Did not read " + url;
            return f;
        }
        
    }
    
}
