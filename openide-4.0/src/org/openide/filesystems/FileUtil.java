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

package org.openide.filesystems;

import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.SyncFailedException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLStreamHandler;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.WeakHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import javax.swing.Icon;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileSystemView;
import org.openide.ErrorManager;
import org.openide.util.NbBundle;
import org.openide.util.Utilities;

/** Common utilities for handling files.
 * This is a dummy class; all methods are static.
 */
public final class FileUtil extends Object {

    private static byte[] ZIP_HEADER = {0x50,0x4b,0x3,0x4};

    private FileUtil() {}

    /** Copies stream of files.
    * <P> 
    * Please be aware, that this method doesn't close any of passed streams.
    * @param is input stream
    * @param os output stream
    */
    public static void copy (InputStream is, OutputStream os) throws IOException {
        final byte[] BUFFER = new byte[4096];
        int len;

        for (;;) {
            len = is.read (BUFFER);
            if (len == -1) return;
            os.write (BUFFER, 0, len);
        }
    }

    /** Copies file to the selected folder.
     * This implementation simply copies the file by stream content.
    * @param source source file object
    * @param destFolder destination folder
    * @param newName file name (without extension) of destination file
    * @param newExt extension of destination file
    * @return the created file object in the destination folder
    * @exception IOException if <code>destFolder</code> is not a folder or does not exist; the destination file already exists; or
    *      another critical error occurs during copying
    */
    static FileObject copyFileImpl (
        FileObject source, FileObject destFolder, String newName, String newExt
    ) throws IOException {
        FileObject dest = destFolder.createData(newName, newExt);

        FileLock lock = null;
        InputStream bufIn = null;
        OutputStream bufOut = null;
        try {
            lock = dest.lock();
            bufIn = source.getInputStream();
            
            if (dest instanceof AbstractFileObject)
                /** prevents from firing fileChange*/
                bufOut = ((AbstractFileObject)dest).getOutputStream(lock, false);
            else
                bufOut = dest.getOutputStream(lock);

            copy (bufIn, bufOut);
            copyAttributes (source, dest);

        }
        finally {
            if (bufIn != null)
                bufIn.close();
            if (bufOut != null)
                bufOut.close();

            if (lock != null)
                lock.releaseLock();
        }

        return dest;
    }


    //
    // public methods
    //

    /** Factory method that creates an empty implementation of a filesystem that
     * completely resides in a memory.
     * @return a blank writeable filesystem
     * @since 4.43
     */
    public static FileSystem createMemoryFileSystem () {
        return new MemoryFileSystem ();
    }

    /** Copies file to the selected folder.
    * This implementation simply copies the file by stream content.
    * @param source source file object
    * @param destFolder destination folder
    * @param newName file name (without extension) of destination file
    * @param newExt extension of destination file
    * @return the created file object in the destination folder
    * @exception IOException if <code>destFolder</code> is not a folder or does not exist; the destination file already exists; or
    *      another critical error occurs during copying
    */
    public static FileObject copyFile(FileObject source, FileObject destFolder,
                                      String newName, String newExt) throws IOException {
        return source.copy (destFolder, newName, newExt);
    }

    /** Copies file to the selected folder.
    * This implementation simply copies the file by stream content.
    * Uses the extension of the source file.
    * @param source source file object
    * @param destFolder destination folder
    * @param newName file name (without extension) of destination file
    * @return the created file object in the destination folder
    * @exception IOException if <code>destFolder</code> is not a folder or does not exist; the destination file already exists; or
    *      another critical error occurs during copying
    */
    public static FileObject copyFile(FileObject source, FileObject destFolder,
                                      String newName) throws IOException {
        return copyFile(source, destFolder, newName, source.getExt());
    }

    /** Moves file to the selected folder.
     * This implementation uses a copy-and-delete mechanism, and automatically uses the necessary lock.
    * @param source source file object
    * @param destFolder destination folder
    * @param newName file name (without extension) of destination file
    * @return new file object
    * @exception IOException if either the {@link #copyFile copy} or {@link FileObject#delete delete} failed
    */
    public static FileObject moveFile(FileObject source, FileObject destFolder,
                                      String newName) throws IOException {
        FileLock lock = null;
        try {
            lock = source.lock();
            return source.move (lock, destFolder, newName, source.getExt ());
        }
        finally {
            if (lock != null)
                lock.releaseLock();
        }
    }

    /**
     * Creates a folder on given filesystem.  The name of the new folder can be
     * specified as a multi-component pathname whose components are separated
     * by File.separatorChar or &quot;/&quot; (forward slash).
     *
     * @param folder where the new folder will be placed in
     * @param name name of the new folder
     * @return the new folder
     * @exception IOException if the creation fails
     */
    public static FileObject createFolder (FileObject folder, String name)
    throws IOException {
        String separators;
        if (File.separatorChar != '/')
            separators = "/" + File.separatorChar; // NOI18N
        else
            separators = "/";   // NOI18N
        
        StringTokenizer st = new StringTokenizer (name, separators);
        while (st.hasMoreElements ()) {
            name = st.nextToken ();
            if (name.length () > 0) {
                FileObject f = folder.getFileObject (name);
                if (f == null) {
                    try {
                        f = folder.createFolder (name);
                    } catch (SyncFailedException ex) {
                        // there might be unconsistency between the cache
                        // and the disk, that is why
                        folder.refresh();
                        // and try again
                        f = folder.getFileObject (name);
                        if (f == null) {
                            // if still not found than we have to report the
                            // exception
                            throw ex;
                        }
                    }
                }
                folder = f;
            }
        }
        return folder;
    }

    /** Creates a data file on given filesystem. The name of
    * data file can be composed as resource name (e. g. org/netbeans/myfolder/mydata )
    * and the method scans which of folders has already been created 
    * and which not. 
    *
    * @param folder to begin with creation at
    * @param name name of data file as a resource
    * @return the data file for given name
    * @exception IOException if the creation fails
    */
    public static FileObject createData (FileObject folder, String name)
    throws IOException {
        if (folder == null) {
            throw new IllegalArgumentException("Null folder"); // NOI18N
        }
        if (name == null) {
            throw new IllegalArgumentException("Null name"); // NOI18N
        }
        String foldername, dataname, fname, ext;
        int index = name.lastIndexOf('/');
        FileObject data;

        // names with '/' on the end are not valid
        if (index >= name.length()) throw new IOException("Wrong file name."); // NOI18N

        // if name contains '/', create necessary folder first
        if (index != -1) {
            foldername = name.substring(0, index);
            dataname = name.substring(index + 1);
            folder = createFolder(folder, foldername);
            assert folder != null;
        } else {
            dataname = name;
        }

        // create data
        index = dataname.lastIndexOf('.');
        if (index != -1) {
            fname = dataname.substring(0, index);
            ext = dataname.substring(index + 1);
        } else {
            fname = dataname;
            ext = ""; // NOI18N
        }

        data = folder.getFileObject (fname, ext);
        if (data == null) {
            try {
                data = folder.createData(fname, ext);
                assert data != null : "FileObject.createData cannot return null; called on " + folder + " + " + fname + " + " + ext; // #50802
            } catch (SyncFailedException ex) {
                // there might be unconsistency between the cache
                // and the disk, that is why
                folder.refresh();
                // and try again
                data = folder.getFileObject (fname, ext);
                if (data == null) {
                    // if still not found than we have to report the
                    // exception
                    throw ex;
                }
            }
        }
        return data;
    }

    /** Finds appropriate java.io.File to FileObject if possible. 
     * If not possible then null is returned.
     * This is the inverse operation of {@link #toFileObject}.
     * @param fo FileObject whose coresponding File will be looked for
     * @return java.io.File or null if no corresponding File exists.
     * @since 1.29
     */    
    public static java.io.File toFile (FileObject fo) {
        File retVal = (File)fo.getAttribute("java.io.File"); // NOI18N;        
                
        if (retVal == null) {
            URL fileURL = null;            
            int[] types = new int [] {URLMapper.INTERNAL, URLMapper.EXTERNAL};
            for (int i = 0; (fileURL == null || "file".equals(fileURL.getProtocol())) && i < types.length; i++) { // NOI18N
                fileURL = URLMapper.findURL(fo, types[i]);    
            }
        
            if (fileURL != null && "file".equals(fileURL.getProtocol())) {
                retVal =  new File(URI.create(fileURL.toExternalForm()));
            } 

            retVal = (retVal != null) ? normalizeFile(retVal) : null;
        }
        
        return retVal; 
    }

    /**
     * Converts a disk file to a matching file object.
     * This is the inverse operation of {@link #toFile}.
     * <p class="nonnormative">
     * If you are running with the MasterFS module enabled, that will guarantee
     * that this method never returns null for a file which exists on disk.
     * </p>
     * @param file a disk file (may or may not exist)
     * @return a corresponding file object, or null if the file does not exist
     *         or there is no {@link URLMapper} available to convert it
     * @throws IllegalArgumentException if the file is not {@link #normalizeFile normalized}
     * @since 4.29
     */
    public static FileObject toFileObject(File file) throws IllegalArgumentException {
        FileObject retVal = null;
        if (!file.equals(normalizeFile(file))) {
            throw new IllegalArgumentException("Parameter file was not " + // NOI18N
                    "normalized. Was " + file + " instead of " + normalizeFile(file)); // NOI18N
        }        
        try {
            URL url = fileToURL(file);
            if (url.getAuthority() != null && (Utilities.isWindows () || (Utilities.getOperatingSystem () == Utilities.OS_OS2))) {
                return null;
            }              
            retVal = URLMapper.findFileObject(url);
            /*probably temporary piece of code to catch the cause of #46630*/ 
        } catch (MalformedURLException e) {
            retVal = null;
        }
        return retVal;
    }

    static URL fileToURL(File file) throws MalformedURLException {        
        URL retVal = null;
        if (canBeCanonicalizedOnWindows(file)) {
            retVal = file.toURI().toURL();            
        } else {
            retVal = new URL ("file:/"+file.getAbsolutePath ());//NOI18N
            
        }        
        return retVal;
    }

    /** Finds appropriate FileObjects to java.io.File if possible.
     * If not possible then empty array is returned. More FileObjects may 
     * correspond to one java.io.File that`s why array is returned.
     * @param file File whose coresponding FileObjects will be looked for.
     * The file has to be "normalized" otherwise IllegalArgumentException is thrown.
     * See {@link #normalizeFile} for how to do that.
     * @return corresponding FileObjects or empty array  if no 
     * corresponding FileObject exists.
     * @since 1.29
     * @deprecated Use {@link #toFileObject} instead.
     */        
    public static FileObject[] fromFile(File file) {
        FileObject[] retVal;
        if (!file.equals(normalizeFile(file))) {
            throw new IllegalArgumentException("Parameter file was not " + // NOI18N
                    "normalized. Was " + file + " instead of " + normalizeFile(file)); // NOI18N
        }
        try {
            URL url = (file.toURI().toURL());

            if (url.getAuthority() != null && (Utilities.isWindows () || (Utilities.getOperatingSystem () == Utilities.OS_OS2))) {
                return null;
            }
            retVal = URLMapper.findFileObjects(url);
        } catch (MalformedURLException e) {
            retVal = null;
        }
        return retVal;
    }


    
    
    /** transient attributes which should not be copied
    * of type Set<String>
    */
    static final Set transientAttributes = new HashSet ();
    static {
        transientAttributes.add ("templateWizardURL"); // NOI18N
        transientAttributes.add ("templateWizardIterator"); // NOI18N
        transientAttributes.add ("templateWizardDescResource"); // NOI18N
        transientAttributes.add ("templateCategory"); // NOI18N
        transientAttributes.add ("instantiatingIterator"); // NOI18N
        transientAttributes.add ("instantiatingWizardURL"); // NOI18N
        transientAttributes.add ("SystemFileSystem.localizingBundle"); // NOI18N
        transientAttributes.add ("SystemFileSystem.icon"); // NOI18N
        transientAttributes.add ("SystemFileSystem.icon32"); // NOI18N
    }
    /** Copies attributes from one file to another.
    * Note: several special attributes will not be copied, as they should
    * semantically be transient. These include attributes used by the
    * template wizard (but not the template atttribute itself).
    * @param source source file object
    * @param dest destination file object
    * @exception IOException if the copying failed
    */
    public static void copyAttributes (FileObject source, FileObject dest) throws IOException {
        Enumeration attrKeys = source.getAttributes();
        while (attrKeys.hasMoreElements()) {
            String key = (String) attrKeys.nextElement();
            if (transientAttributes.contains (key)) continue;            
            if (isTransient (source, key)) continue;            
            Object value = source.getAttribute(key);
            if (value != null) {
                dest.setAttribute(key, value);
            }
        }
    }

    static boolean isTransient (FileObject fo, String attrName) {
        return XMLMapAttr.ModifiedAttribute.isTransient (fo, attrName);
    }
    
    /** Extract jar file into folder represented by file object. If the JAR contains
    * files with name filesystem.attributes, it is assumed that these files 
    * has been created by DefaultAttributes implementation and the content
    * of these files is treated as attributes and added to extracted files.
    * <p><code>META-INF/</code> directories are skipped over.
    *
    * @param fo file object of destination folder
    * @param is input stream of jar file
    * @exception IOException if the extraction fails
    * @deprecated Use of XML filesystem layers generally obsoletes this method.
    */
    public static void extractJar (final FileObject fo, final InputStream is) throws IOException {
        FileSystem fs = fo.getFileSystem();

        fs.runAtomicAction (new FileSystem.AtomicAction () {
		public void run () throws IOException {
		    extractJarImpl (fo, is);
		}
	    });
    }

    /** Does the actual extraction of the Jar file.
     */
    private static void extractJarImpl (FileObject fo, InputStream is) throws IOException {
        JarInputStream jis;
        JarEntry je;

        // files with extended attributes (name, DefaultAttributes.Table)
        HashMap attributes = new HashMap (7);
	
        jis = new JarInputStream(is);

        while ((je = jis.getNextJarEntry()) != null) {
            String name = je.getName();
            if (name.toLowerCase ().startsWith ("meta-inf/")) continue; // NOI18N

            if (je.isDirectory ()) {
                createFolder (fo, name);
                continue;
            }

            if (DefaultAttributes.acceptName (name)) {
                // file with extended attributes
                DefaultAttributes.Table table = DefaultAttributes.loadTable (jis,name);
                attributes.put (name, table);
            } else {
                // copy the file
                FileObject fd = createData(fo, name);
                FileLock lock = fd.lock ();
                try {
                    OutputStream os = fd.getOutputStream (lock);
                    try {
                        copy (jis, os);
                    } finally {
                        os.close ();
                    }
                } finally {
                    lock.releaseLock ();
                }
            }
        }

        //
        // apply all extended attributes
        //

        Iterator it = attributes.entrySet ().iterator ();
        while (it.hasNext ()) {
            Map.Entry entry = (Map.Entry)it.next ();

            String fileName = (String)entry.getKey ();
            int last = fileName.lastIndexOf ('/');
            String dirName;
            if (last != -1)
                dirName = fileName.substring (0, last + 1);
            else
                dirName = ""; // NOI18N
            String prefix = fo.isRoot () ? dirName : fo.getPath() + '/' + dirName;

            DefaultAttributes.Table t = (DefaultAttributes.Table)entry.getValue ();
            Iterator files = t.keySet ().iterator ();
            while (files.hasNext ()) {
                String orig = (String)files.next ();
                String fn = prefix + orig;
                FileObject obj = fo.getFileSystem ().findResource (fn);

                if (obj == null) {
                    continue;
                }

                Enumeration attrEnum = t.attrs (orig);
                while (attrEnum.hasMoreElements ()) {
                    // iterate thru all arguments
                    String attrName = (String)attrEnum.nextElement ();
                    // Note: even transient attributes set here!
                    Object value = t.getAttr (orig, attrName);
                    if (value != null) {
                        obj.setAttribute (attrName, value);
                    }
                }
            }
        }

    } // extractJar


    /** Gets the extension of a specified file name. The extension is
    * everything after the last dot.
    *
    * @param fileName name of the file
    * @return extension of the file (or <code>""</code> if it had none)
    */
    public static String getExtension(String fileName) {
        int index = fileName.lastIndexOf("."); // NOI18N
        if (index == -1)
            return ""; // NOI18N
        else
            return fileName.substring(index + 1);
    }

    /** Finds an unused file name similar to that requested in the same folder.
     * The specified file name is used if that does not yet exist or is
     * {@link FileObject#isVirtual isVirtual}.
     * Otherwise, the first available name of the form <code>basename_nnn.ext</code> (counting from one) is used.
     *
     * <p><em>Caution:</em> this method does not lock the parent folder
     * to prevent race conditions: i.e. it is possible (though unlikely)
     * that the resulting name will have been created by another thread
     * just as you were about to create the file yourself (if you are,
     * in fact, intending to create it just after this call). Since you
     * cannot currently lock a folder against child creation actions,
     * the safe approach is to use a loop in which a free name is
     * retrieved; an attempt is made to {@link FileObject#createData create}
     * that file; and upon an <code>IOException</code> during
     * creation, retry the loop up to a few times before giving up.
     *
    * @param folder parent folder
    * @param name preferred base name of file
    * @param ext extension to use
    * @return a free file name <strong>(without the extension)</strong>
     */
    public static String findFreeFileName (
        FileObject folder, String name, String ext
    ) {
        if (checkFreeName (folder, name, ext)) {
            return name;
        }
        for (int i = 1;;i++) {
            String destName = name + "_"+i; // NOI18N
            if (checkFreeName (folder, destName, ext)) {
                return destName;
            }
        }
    }

    /** Finds an unused folder name similar to that requested in the same parent folder.
     * <p>See caveat for <code>findFreeFileName</code>.
     * @see #findFreeFileName findFreeFileName
    * @param folder parent folder
    * @param name preferred folder name
    * @return a free folder name
    */
    public static String findFreeFolderName (
        FileObject folder, String name
    ) {
        if (checkFreeName (folder, name, null)) {
            return name;
        }
        for (int i = 1;;i++) {
            String destName = name + "_"+i; // NOI18N
            if (checkFreeName (folder, destName, null)) {
                return destName;
            }
        }
    }
    
    /**
     * Gets a relative resource path between folder and fo.
     * @param folder root of filesystem or any other folder in folders hierarchy
     * @param fo arbitrary FileObject in folder's tree (including folder itself)
     * @return relative path between folder and fo. The returned path never
     * starts with a '/'. It never ends with a '/'. Specifically, if
     * folder==fo, returns "". Returns <code>null</code> if fo is not in
     * folder's tree.    
     * @see #isParentOf
     * @since 4.16 
     */ 
    
    public static String getRelativePath(FileObject folder, FileObject fo) {
         if (!isParentOf(folder, fo) && folder != fo) {
            return null;     
         }
         
        String result = fo.getPath().substring(folder.getPath().length());
        if (result.startsWith("/")) {
            result = result.substring(1);
        }
        return result;
    } 
    
    
    /** Test if given name is free in given folder.
     * @param fo folder to check in
     * @param name name of the file or folder to check
     * @param ext extension of the file (null for folders)
     * @return true, if such name does not exists
     */
    private static boolean checkFreeName (
        FileObject fo, String name, String ext
    ) {
        if ((Utilities.isWindows () || (Utilities.getOperatingSystem () == Utilities.OS_OS2)) || isMacOS()) {
            // case-insensitive, do some special check
            Enumeration en = fo.getChildren(false);
            while (en.hasMoreElements()) {
                fo = (FileObject)en.nextElement();
                String n = fo.getName ();
                String e = fo.getExt();
                
                // different names => check others
                if (!n.equalsIgnoreCase (name)) continue;
                
                // same name + without extension => no
                if ((ext == null || ext.trim().length() == 0) && (e == null || e.trim().length() == 0))
                    return (fo.isVirtual()) ? true : false;
                
                // one of there is witout extension => check next
                if (ext == null || e == null) continue;
                
                if (ext.equalsIgnoreCase (e)) {
                  // same name + same extension => no
                  return (fo.isVirtual()) ? true: false;
                }
            }
            
            // no of the files has similar name and extension
            return true;
        } else {
          if (ext == null) {
              fo = fo.getFileObject(name);
              if (fo == null) return true;
              return (fo.isVirtual()) ? true: false;
          } else {
              fo = fo.getFileObject(name, ext);
              if (fo == null) return true;
              return (fo.isVirtual()) ? true: false;
          }
        }
    }

    private static boolean isMacOS() {
        return (Utilities.getOperatingSystem() & Utilities.OS_MAC) != 0;
    }

    // note: "sister" is preferred in English, please don't ask me why --jglick // NOI18N
    /** Finds brother file with same base name but different extension.
    * @param fo the file to find the brother for or <CODE>null</CODE>
    * @param ext extension for the brother file
    * @return a brother file (with the requested extension and the same parent folder as the original) or
    *   <CODE>null</CODE> if the brother file does not exist or the original file was <CODE>null</CODE>
    */
    public static FileObject findBrother (FileObject fo, String ext) {
        if (fo == null) return null;
        FileObject parent = fo.getParent ();
        if (parent == null) return null;

        return parent.getFileObject (fo.getName (), ext);
    }

    /** Obtain MIME type for a well-known extension.
    * If there is a case-sensitive match, that is used, else will fall back
    * to a case-insensitive match.
    * @param ext the extension: <code>"jar"</code>, <code>"zip"</code>, etc.
    * @return the MIME type for the extension, or <code>null</code> if the extension is unrecognized
    * @deprecated in favour of {@link #getMIMEType(FileObject) getMIMEType(FileObject)} as MIME cannot
    * be generaly detected by a file object extension.
    */
    public static String getMIMEType (String ext) {
        String s = (String) map.get (ext);
        if (s != null)
            return s;
        else
            return (String) map.get (ext.toLowerCase ());
    }

    /** Resolves MIME type. Registered resolvers are invoked and used to achieve this goal.
    * Resolvers must subclass MIMEResolver. If resolvers don`t recognize MIME type then  
    * MIME type is obtained  for a well-known extension.
    * @param fo whose MIME type should be recognized
    * @return the MIME type for the FileObject, or <code>null</code> if the FileObject is unrecognized
    */    
    public static String getMIMEType (FileObject fo) {
        String retVal = MIMESupport.findMIMEType(fo, null);
        if (retVal == null) retVal = getMIMEType (fo.getExt ());
        return retVal;
    }
    
    /** Finds mime type by calling getMIMEType, but 
     * instead of returning null it fallbacks to default type
     * either text/plain or content/unknown (even for folders)
     */
    static String getMIMETypeOrDefault (FileObject fo) {
        String def = getMIMEType (fo.getExt ());
        String t = MIMESupport.findMIMEType(fo, def);
        if (t == null) {
            // #42965: never allowed
            t = "content/unknown"; // NOI18N
        }
        return t;
    }

    /* mapping of file extensions to content-types */
    private static java.util.Dictionary map = new java.util.Hashtable();

    /**
     * Register MIME type for a new extension.
     * Note that you may register a case-sensitive extension if that is
     * relevant (for example <samp>*.C</samp> for C++) but if you register
     * a lowercase extension it will by default apply to uppercase extensions
     * too (for use on Windows or generally for situations where filenames
     * become accidentally upcased).
     * @param ext the file extension (should be lowercase unless you specifically care about case)
     * @param mimeType the new MIME type
     * @throws IllegalArgumentException if this extension was already registered with a <em>different</em> MIME type
     * @see #getMIMEType
     * @deprecated You should instead use the more general {@link MIMEResolver} system.
     */
    public static void setMIMEType(String ext, String mimeType) {
        synchronized (map) {
            String old=(String)map.get(ext);
            if (old == null) {
                map.put(ext, mimeType);
            } else {
                if (!old.equals(mimeType))
                    throw new IllegalArgumentException
                    ("Cannot overwrite existing MIME type mapping for extension `" + // NOI18N
                     ext + "' with " + mimeType + " (was " + old + ")"); // NOI18N
                // else do nothing
            }
        }
    }

    static {
        setMIMEType("uu", "application/octet-stream"); // NOI18N
        setMIMEType("exe", "application/octet-stream"); // NOI18N
        setMIMEType("ps", "application/postscript"); // NOI18N
        setMIMEType("zip", "application/zip"); // NOI18N
        setMIMEType("class", "application/octet-stream"); // Sun uses application/java-vm // NOI18N
        setMIMEType("jar", "application/x-jar"); // NOI18N
        setMIMEType("sh", "application/x-shar"); // NOI18N
        setMIMEType("tar", "application/x-tar"); // NOI18N
        setMIMEType("snd", "audio/basic"); // NOI18N
        setMIMEType("au", "audio/basic"); // NOI18N
        setMIMEType("wav", "audio/x-wav"); // NOI18N
        setMIMEType("htm", "text/html"); // NOI18N
        setMIMEType("html", "text/html"); // NOI18N
        setMIMEType("xml", "text/xml"); // NOI18N
        setMIMEType("xsl", "text/xml"); // NOI18N
        setMIMEType("xsd", "text/xml"); // NOI18N
        setMIMEType("dtd", "text/x-dtd"); // NOI18N
        setMIMEType("css", "text/css"); // NOI18N
        setMIMEType("text", "text/plain"); // NOI18N
        setMIMEType("pl", "text/plain"); // NOI18N
        setMIMEType("txt", "text/plain"); // NOI18N
        setMIMEType("properties", "text/plain"); // NOI18N
        setMIMEType("java", "text/x-java"); // NOI18N
        // mime types from Jetty web server
        setMIMEType("ra", "audio/x-pn-realaudio"); // NOI18N
        setMIMEType("ram", "audio/x-pn-realaudio"); // NOI18N
        setMIMEType("rm", "audio/x-pn-realaudio"); // NOI18N
        setMIMEType("rpm", "audio/x-pn-realaudio"); // NOI18N
        setMIMEType("mov", "video/quicktime"); // NOI18N
        setMIMEType("jsp", "text/plain"); // NOI18N
    }
    
    /**
     * Construct a stream handler that handles the <code>nbfs</code> URL protocol
     * used for accessing file objects directly.
     * This method is not intended for module use; only the core
     * should need to call it.
     * Modules probably need only use {@link URLMapper} to create and decode such
     * URLs.
     * @since 3.17
     */
    public static URLStreamHandler nbfsURLStreamHandler() {
        return FileURL.HANDLER;
    }

    /** Recursively checks whether the file is underneath the folder. It checks whether
     * the file and folder are located on the same filesystem, in such case it checks the
     * parent <code>FileObject</code> of the file recursively until the folder is found
     * or the root of the filesystem is reached.
     * <p><strong>Warning:</strong> this method will return false in the case that
     * <code>folder == fo</code>.
     * @param folder the root of folders hierarchy to search in
     * @param fo the file to search for
     * @return <code>true</code>, if <code>fo</code> lies somewhere underneath the <code>folder</code>,
     * <code>false</code> otherwise
     * @since 3.16
     */
    public static boolean isParentOf (FileObject folder, FileObject fo) {
        if (folder == null) {
            throw new IllegalArgumentException("Tried to pass null folder arg"); // NOI18N
        }
        if (fo == null) {
            throw new IllegalArgumentException("Tried to pass null fo arg"); // NOI18N
        }
        
        if (folder.isData ()) {
            return false;
        }

        try {
            if (folder.getFileSystem () != fo.getFileSystem ()) {
                return false;
            }
        } catch (FileStateInvalidException e) {
            return false;
        }
        
        FileObject parent = fo.getParent ();
        while (parent != null) {
            if (parent == folder) {
                return true;
            }
            
            parent = parent.getParent ();
        }
        
        return false;
    }

    /** Creates a weak implementation of FileChangeListener.
     *
     * @param l the listener to delegate to
     * @param source the source that the listener should detach from when
     *     listener <CODE>l</CODE> is freed, can be <CODE>null</CODE>
     * @return a FileChangeListener delegating to <CODE>l</CODE>.
     * @since 4.10
     */
    public static FileChangeListener weakFileChangeListener (FileChangeListener l, Object source) {
        return (FileChangeListener)org.openide.util.WeakListeners.create (FileChangeListener.class, l, source);
    }

    /** Creates a weak implementation of FileStatusListener.
     *
     * @param l the listener to delegate to
     * @param source the source that the listener should detach from when
     *     listener <CODE>l</CODE> is freed, can be <CODE>null</CODE>
     * @return a FileChangeListener delegating to <CODE>l</CODE>.
     * @since 4.10
     */
    public static FileStatusListener weakFileStatusListener (FileStatusListener l, Object source) {
        return (FileStatusListener)org.openide.util.WeakListeners.create (FileStatusListener.class, l, source);
    }
    
    /**
     * Get an appropriate display name for a file object.
     * If the file corresponds to a path on disk, this will be the disk path.
     * Otherwise the name will mention the filesystem name or archiv name in case 
     * the file comes from archiv and relative path. Relative path will be mentioned 
     * just in case that passed <code>FileObject</code> isn't root {@link FileObject#isRoot}.   
     *  
     * @param fo a file object
     * @return a display name indicating where the file is
     * @since 4.39
     */
    public static String getFileDisplayName(FileObject fo) {
        String displayName = null;
        File f = FileUtil.toFile(fo);
        if (f != null) {
            displayName = f.getAbsolutePath();
        } else {
            FileObject archiveFile = FileUtil.getArchiveFile(fo);
            if (archiveFile != null) {
                displayName = getArchivDisplayName(fo, archiveFile);
            }
        }

        if (displayName == null) {
            try {            
                if (fo.isRoot()) {
                    displayName = fo.getFileSystem().getDisplayName();     
                } else {
                    displayName = NbBundle.getMessage(FileUtil.class, "LBL_file_in_filesystem",
                            fo.getPath(), fo.getFileSystem().getDisplayName());
                }
            } catch (FileStateInvalidException e) {
                // Not relevant now, just use the simple path.
                displayName = fo.getPath();
            }
        }
        return displayName;
    }

    private static String getArchivDisplayName(FileObject fo, FileObject archiveFile) {
        String displayName = null;

        File f = FileUtil.toFile(archiveFile);
        if (f != null) {
            String archivDisplayName = f.getAbsolutePath();
            if (fo.isRoot()) {
                displayName = archivDisplayName;
            } else {
                String entryPath = fo.getPath();
                displayName = NbBundle.getMessage(FileUtil.class, "LBL_file_in_filesystem",
                        entryPath, archivDisplayName);                
            }
        }

        return displayName;
    }

    /**
     * Normalize a file path to a clean form.
     * This method may for example make sure that the returned file uses
     * the correct case on Windows; that old Windows 8.3 filenames are changed to the long form;
     * that relative paths are changed to be
     * absolute; etc.
     * Unlike {@link File#getCanonicalFile} this method will not traverse symbolic links on Unix.
     * @param file file to normalize
     * @return normalized file
     * @since 4.48
     */
    public static File normalizeFile(File file) {
        if ((Utilities.isWindows () || (Utilities.getOperatingSystem () == Utilities.OS_OS2))) {
            file = normalizeFileOnWindows(file);
        } else if (Utilities.getOperatingSystem() == Utilities.OS_MAC) {
            file = normalizeFileOnMac(file);
        } else {
            file = normalizeFileOnUnixAlike(file);
        }
        return file;
    }

    private static File normalizeFileOnUnixAlike(File file) {
        // On Unix, do not want to traverse symlinks.
        if (file.getAbsolutePath().equals("/..")) { // NOI18N
            // Special treatment.
            file = new File("/"); // NOI18N
        } else {
            // URI.normalize removes ../ and ./ sequences nicely.
            file = new File(file.toURI().normalize()).getAbsoluteFile();
        }
        return file;
    }

    private static File normalizeFileOnMac(final File file) {
        File retVal = file;
        try {
            // URI.normalize removes ../ and ./ sequences nicely.            
            File absoluteFile = new File(file.toURI().normalize());
            File canonicalFile = file.getCanonicalFile();
            boolean isSymLink = !canonicalFile.getAbsolutePath().equalsIgnoreCase(absoluteFile.getAbsolutePath());
            if (isSymLink) {
                retVal = normalizeSymLinkOnMac(absoluteFile);
            } else {
                retVal = canonicalFile;                
            }
        } catch (IOException ioe) {
            ErrorManager.getDefault().log(ErrorManager.ERROR, "Normalization failed on file " + file + ": " + ioe);             
            // OK, so at least try to absolutize the path
            retVal = file.getAbsoluteFile();
        }
        return retVal;
    }

    
    /**
     * @param file is expected to be already absolute with removed ../ and ./ 
     */ 
    private static File normalizeSymLinkOnMac(final File file) throws IOException {
        File retVal = File.listRoots()[0];
        File pureCanonicalFile = retVal;
        
        final String pattern = File.separator + ".." + File.separator; //NOI18N                
        final String fileName;

        {// strips insufficient non-<tt>".."</tt> segments preceding them
            String tmpFileName = file.getAbsolutePath();
            int index = tmpFileName.lastIndexOf(pattern);
            if (index > -1) {
                tmpFileName = tmpFileName.substring(index + pattern.length()); //Remove starting {/../}*
            }
            fileName = tmpFileName; 
        }

        /*normalized step after step*/
        StringTokenizer fileSegments = new StringTokenizer(fileName, File.separator);
        while (fileSegments.hasMoreTokens()) {
            File absolutelyEndingFile = new File(pureCanonicalFile, fileSegments.nextToken());
            pureCanonicalFile = absolutelyEndingFile.getCanonicalFile();

            boolean isSymLink = !pureCanonicalFile.getAbsolutePath().equalsIgnoreCase(absolutelyEndingFile.getAbsolutePath());
            if (isSymLink) {
                retVal = new File(retVal, absolutelyEndingFile.getName());
            } else {
                retVal = new File(retVal, pureCanonicalFile.getName());
            }
        }

        return retVal;
    }

    private static File normalizeFileOnWindows(final File file) {
        File retVal = null;
        
        if (canBeCanonicalizedOnWindows(file)) {
            try {
                retVal = file.getCanonicalFile();                   
            } catch (IOException e) {
                ErrorManager.getDefault().log(ErrorManager.ERROR, "getCanonicalFile() on file " + file + " failed. " + e.toString()); // NOI18N
            }
        }
        
        return (retVal != null) ? retVal : file.getAbsoluteFile();
    }

    private static boolean canBeCanonicalizedOnWindows(final File file) {
        /*Flopy and empty CD-drives can't be canonicalized*/
        boolean canBeCanonizalized = true;
        if (file.getParent() == null) {//for File.listRoots should be true
            canBeCanonizalized = !FileSystemView.getFileSystemView().isFloppyDrive(file) && file.exists();    
        }
        return canBeCanonizalized;
    }

    /**
     * Returns a FileObject representing the root folder of an archive.
     * Clients may need to first call {@link #isArchiveFile(FileObject)} to determine
     * if the file object refers to an archive file.
     * @param fo a ZIP- (or JAR-) format archive file
     * @return a virtual archive root folder, or null if the file is not actually an archive
     * @since 4.48
     */
    public static FileObject getArchiveRoot (FileObject fo) {
        URL archiveURL = URLMapper.findURL(fo, URLMapper.EXTERNAL);
        if (archiveURL == null) {
            return null;
        }
        return URLMapper.findFileObject(getArchiveRoot(archiveURL));
    }

    /**
     * Returns a URL representing the root of an archive.
     * Clients may need to first call {@link #isArchiveFile(URL)} to determine if the URL
     * refers to an archive file.
     * @param url of a ZIP- (or JAR-) format archive file
     * @return the <code>jar</code>-protocol URL of the root of the archive
     * @since 4.48
     */
    public static URL getArchiveRoot (URL url) {
        try {
            // XXX TBD whether the url should ever be escaped...
            return new URL("jar:" + url + "!/"); // NOI18N
        } catch (MalformedURLException e) {
            throw new AssertionError(e);
        }
    }

    /**
     * Returns a FileObject representing an archive file containg the
     * FileObject given by the parameter.
     * <strong>Remember</strong> that any path within the archive is discarded
     * so you may need to check for non-root entries.
     * @param fo a file in a JAR filesystem
     * @return the file corresponding to the archive itself,
     *         or null if <code>fo</code> is not an archive entry
     * @since 4.48
     */
    public static FileObject getArchiveFile(FileObject fo) {
        try {
            FileSystem fs = fo.getFileSystem();
            if (fs instanceof JarFileSystem) {
                File jarFile = ((JarFileSystem)fs).getJarFile();
                return toFileObject(jarFile);
            }
        } catch (FileStateInvalidException e) {
            ErrorManager.getDefault().notify(e);
        }
        return null;
    }

    /**
     * Returns the URL of the archive file containing the file
     * referred to by a <code>jar</code>-protocol URL.
     * <strong>Remember</strong> that any path within the archive is discarded
     * so you may need to check for non-root entries.
     * @param url a URL
     * @return the embedded archive URL, or null if the URL is not a
     *         <code>jar</code>-protocol URL containing <code>!/</code>
     * @since 4.48
     */
    public static URL getArchiveFile (URL url) {
        String protocol = url.getProtocol();
        if ("jar".equals(protocol)) {   //NOI18N
            String path = url.getPath();
            int index = path.indexOf("!/");     //NOI18N
            if (index>=0) {
                try {
                    return new URL(path.substring(0,index));
                } catch (MalformedURLException mue) {
                    ErrorManager.getDefault().notify(mue);
                }
            }
        }
        return null;
    }

    /**
     * Tests if a file represents a JAR or ZIP archive.
     * @param fo the file to be tested
     * @return true if the file looks like a ZIP-format archive
     * @since 4.48
     */
    public static boolean isArchiveFile(FileObject fo) {
        if (fo == null) {
            throw new IllegalArgumentException("Cannot pass null to FileUtil.isArchiveFile"); // NOI18N
        }
        // XXX Special handling of virtual file objects: try to determine it using its name, but don't cache the
        // result; when the file is checked out the more correct method can be used
        if (fo.isVirtual()) {
            String path = fo.getPath();
            int index = path.lastIndexOf('.');
            return index != -1 && index > path.lastIndexOf('/');
        }
        if (fo.isFolder()) {
            return false;
        }
        // First check the cache.
        Boolean b = (Boolean)archiveFileCache.get(fo);
        if (b == null) {
            // Need to check it.
            try {
                InputStream in = fo.getInputStream();
                try {
                    byte[] buffer = new byte[4];
                    int len = in.read(buffer, 0, 4);
                    if (len == 4) {
                        // Got a header, see if it is a ZIP file.
                        b = Boolean.valueOf(Arrays.equals(ZIP_HEADER, buffer));
                    }
                    else {
                        //If the length is less than 4, it can be either
                        //broken (empty) archive file or other empty file.
                        //Return false and don't cache it, when the archive
                        //file will be written and closed its length will change
                        return false;
                    }
                } finally {
                    in.close();
                }
            } catch (IOException ioe) {
                ErrorManager.getDefault().notify(ErrorManager.INFORMATIONAL, ioe);                
            }
            if (b == null) {
                String path = fo.getPath();
                int index = path.lastIndexOf('.');
                b = index != -1 && index > path.lastIndexOf('/') ? Boolean.TRUE : Boolean.FALSE;
            }
            archiveFileCache.put(fo, b);
        }
        return b.booleanValue();
    }
    /** Cache for {@link #isArchiveFile(FileObject)}. */
    private static final Map/*<FileObject,boolean>*/ archiveFileCache = new WeakHashMap();

    /**
     * Tests if a URL represents a JAR or ZIP archive.
     * If there is no such file object, the test is done by heuristic: any URL with an extension is
     * treated as an archive.
     * @param url a URL to a file
     * @return true if the URL seems to represent a ZIP-format archive
     * @since 4.48
     */
    public static boolean isArchiveFile (URL url) {
        if (url == null) {
            throw new NullPointerException("Cannot pass null URL to FileUtil.isArchiveFile"); // NOI18N
        }
        if ("jar".equals(url.getProtocol())) {  //NOI18N
            //Already inside archive, return false
            return false;
        }
        FileObject fo = URLMapper.findFileObject(url);
        if (fo!=null && !fo.isVirtual()) {
            return isArchiveFile(fo);
        }
        else {
            String urlPath = url.getPath();
            int index = urlPath.lastIndexOf('.');
            return index != -1 && index > urlPath.lastIndexOf('/');
        }
    }

    /**
     * Make sure that a JFileChooser does not traverse symlinks on Unix.
     * @param chooser a file chooser
     * @param currentDirectory if not null, a file to set as the current directory
     *                         using {@link JFileChooser#setCurrentDirectory} without canonicalizing
     * @see <a href="http://www.netbeans.org/issues/show_bug.cgi?id=46459">Issue #46459</a>
     * @see <a href="http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4906607">JRE bug #4906607</a>
     * @since org.openide/1 4.42
     */
    public static void preventFileChooserSymlinkTraversal(JFileChooser chooser, File currentDirectory) {
        if (!(Utilities.isWindows () || (Utilities.getOperatingSystem () == Utilities.OS_OS2))) {
            chooser.setCurrentDirectory(wrapFileNoCanonicalize(currentDirectory));
            chooser.setFileSystemView(new NonCanonicalizingFileSystemView());
        } else {
            chooser.setCurrentDirectory(currentDirectory);
        }
    }

    static boolean assertDeprecatedMethod () {
        Thread.dumpStack();
        return true;
    }
    
    private static File wrapFileNoCanonicalize(File f) {
        if (f instanceof NonCanonicalizingFile) {
            return f;
        } else if (f != null) {
            return new NonCanonicalizingFile(f);
        } else {
            return null;
        }
    }
    
    private static File[] wrapFilesNoCanonicalize(File[] fs) {
        if (fs != null) {
            for (int i = 0; i < fs.length; i++) {
                fs[i] = wrapFileNoCanonicalize(fs[i]);
            }
        }
        return fs;
    }
    
    private static final class NonCanonicalizingFile extends File {
        public NonCanonicalizingFile(File orig) {
            this(orig.getPath());
        }
        private NonCanonicalizingFile(String path) {
            super(path);
        }
        private NonCanonicalizingFile(URI uri) {
            super(uri);
        }
        public File getCanonicalFile() throws IOException {
            return wrapFileNoCanonicalize(normalizeFile(super.getAbsoluteFile()));
        }
        public String getCanonicalPath() throws IOException {
            return normalizeFile(super.getAbsoluteFile()).getAbsolutePath();
        }
        public File getParentFile() {
            return wrapFileNoCanonicalize(super.getParentFile());
        }
        public File getAbsoluteFile() {
            return wrapFileNoCanonicalize(super.getAbsoluteFile());
        }
        public File[] listFiles() {
            return wrapFilesNoCanonicalize(super.listFiles());
        }
        public File[] listFiles(FileFilter filter) {
            return wrapFilesNoCanonicalize(super.listFiles(filter));
        }
        public File[] listFiles(FilenameFilter filter) {
            return wrapFilesNoCanonicalize(super.listFiles(filter));
        }
    }
    
    private static final class NonCanonicalizingFileSystemView extends FileSystemView {
        private final FileSystemView delegate = FileSystemView.getFileSystemView();
        public NonCanonicalizingFileSystemView() {}
        public boolean isFloppyDrive(File dir) {
            return delegate.isFloppyDrive(dir);
        }
        public boolean isComputerNode(File dir) {
            return delegate.isComputerNode(dir);
        }
        public File createNewFolder(File containingDir) throws IOException {
            return wrapFileNoCanonicalize(delegate.createNewFolder(containingDir));
        }
        public boolean isDrive(File dir) {
            return delegate.isDrive(dir);
        }
        public boolean isFileSystemRoot(File dir) {
            return delegate.isFileSystemRoot(dir);
        }
        public File getHomeDirectory() {
            return wrapFileNoCanonicalize(delegate.getHomeDirectory());
        }
        public File createFileObject(File dir, String filename) {
            return wrapFileNoCanonicalize(delegate.createFileObject(dir, filename));
        }
        public Boolean isTraversable(File f) {
            return delegate.isTraversable(f);
        }
        public boolean isFileSystem(File f) {
            return delegate.isFileSystem(f);
        }
        /*
        protected File createFileSystemRoot(File f) {
            return translate(delegate.createFileSystemRoot(f));
        }
         */
        public File getChild(File parent, String fileName) {
            return wrapFileNoCanonicalize(delegate.getChild(parent, fileName));
        }
        public File getParentDirectory(File dir) {
            return wrapFileNoCanonicalize(delegate.getParentDirectory(dir));
        }
        public Icon getSystemIcon(File f) {
            return delegate.getSystemIcon(f);
        }
        public boolean isParent(File folder, File file) {
            return delegate.isParent(folder, file);
        }
        public String getSystemTypeDescription(File f) {
            return delegate.getSystemTypeDescription(f);
        }
        public File getDefaultDirectory() {
            return wrapFileNoCanonicalize(delegate.getDefaultDirectory());
        }
        public String getSystemDisplayName(File f) {
            return delegate.getSystemDisplayName(f);
        }
        public File[] getRoots() {
            return wrapFilesNoCanonicalize(delegate.getRoots());
        }
        public boolean isHiddenFile(File f) {
            return delegate.isHiddenFile(f);
        }
        public File[] getFiles(File dir, boolean useFileHiding) {
            return wrapFilesNoCanonicalize(delegate.getFiles(dir, useFileHiding));
        }
        public boolean isRoot(File f) {
            return delegate.isRoot(f);
        }
        public File createFileObject(String path) {
            return wrapFileNoCanonicalize(delegate.createFileObject(path));
        }
    }

}
